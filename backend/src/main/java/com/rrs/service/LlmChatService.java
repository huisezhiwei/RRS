package com.rrs.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.rrs.entity.AiAgent;
import com.rrs.entity.Credential;
import com.rrs.entity.CredentialType;
import com.rrs.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class LlmChatService {

    private final CredentialService credentialService;
    private final AiAgentService aiAgentService;
    private final ObjectMapper objectMapper;

    private static final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();

    /**
     * Stream chat completion response via SSE.
     */
    public void streamChat(AiAgent agent, List<Map<String, String>> messages, SseEmitter emitter) {
        log.info("Starting LLM chat stream for agent: {}", agent.getCode());

        if (agent.getCredentialId() == null) {
            throw new BusinessException(400, "Agent has no LLM credential configured");
        }
        if (agent.getModelName() == null || agent.getModelName().isBlank()) {
            throw new BusinessException(400, "Agent has no model configured");
        }

        Credential credential = credentialService.getEntity(agent.getCredentialId());
        if (credential.getType() != CredentialType.LLM) {
            throw new BusinessException(400, "Credential must be LLM type");
        }

        Map<String, Object> params = credentialService.getParams(credential);
        String apiUrl = params.get("apiUrl").toString().trim();
        String apiKey = params.get("apiKey").toString().trim();

        if (apiUrl.endsWith("/")) apiUrl = apiUrl.substring(0, apiUrl.length() - 1);
        String chatUrl = apiUrl + "/v1/chat/completions";

        // Build request body
        ObjectNode requestBody = objectMapper.createObjectNode();
        requestBody.put("model", agent.getModelName());
        requestBody.put("stream", true);
        if (agent.getTemperature() != null) requestBody.put("temperature", agent.getTemperature());
        if (agent.getMaxTokens() != null) requestBody.put("max_tokens", agent.getMaxTokens());
        if (agent.getTopP() != null) requestBody.put("top_p", agent.getTopP());

        ArrayNode messagesNode = objectMapper.createArrayNode();
        for (Map<String, String> msg : messages) {
            ObjectNode msgNode = objectMapper.createObjectNode();
            msgNode.put("role", msg.get("role"));
            msgNode.put("content", msg.get("content"));
            messagesNode.add(msgNode);
        }
        requestBody.set("messages", messagesNode);

        String jsonBody;
        try {
            jsonBody = objectMapper.writeValueAsString(requestBody);
        } catch (Exception e) {
            throw new BusinessException(500, "Failed to serialize request: " + e.getMessage());
        }

        RequestBody body = RequestBody.create(jsonBody, MediaType.parse("application/json"));
        Request request = new Request.Builder()
                .url(chatUrl)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .post(body)
                .build();

        // Execute async and stream via SSE
        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, java.io.IOException e) {
                log.error("LLM API call failed", e);
                try {
                    emitter.send(SseEmitter.event()
                            .name("error")
                            .data("{\"error\":\"" + e.getMessage() + "\"}"));
                } catch (Exception ignored) {}
                emitter.completeWithError(e);
            }

            @Override
            public void onResponse(Call call, Response response) throws java.io.IOException {
                if (!response.isSuccessful()) {
                    String errBody = response.body() != null ? response.body().string() : "Unknown error";
                    log.error("LLM API returned {}: {}", response.code(), errBody);
                    try {
                        emitter.send(SseEmitter.event()
                                .name("error")
                                .data("{\"error\":\"HTTP " + response.code() + ": " + errBody.replace("\"", "\\\"") + "\"}"));
                    } catch (Exception ignored) {}
                    emitter.complete();
                    return;
                }

                try (ResponseBody responseBody = response.body()) {
                    if (responseBody == null) {
                        emitter.complete();
                        return;
                    }

                    BufferedReader reader = new BufferedReader(
                            new InputStreamReader(responseBody.byteStream(), StandardCharsets.UTF_8));

                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.startsWith("data: ")) {
                            String data = line.substring(6).trim();
                            if ("[DONE]".equals(data)) {
                                emitter.send(SseEmitter.event()
                                        .name("done")
                                        .data("[DONE]"));
                                break;
                            }

                            try {
                                JsonNode chunk = objectMapper.readTree(data);
                                JsonNode choices = chunk.get("choices");
                                if (choices != null && choices.isArray() && choices.size() > 0) {
                                    JsonNode delta = choices.get(0).get("delta");
                                    if (delta != null && delta.has("content")) {
                                        String content = delta.get("content").asText("");
                                        if (!content.isEmpty()) {
                                            emitter.send(SseEmitter.event()
                                                    .name("message")
                                                    .data(content));
                                        }
                                    }
                                }
                            } catch (Exception parseEx) {
                                log.debug("Failed to parse SSE chunk: {}", data);
                            }
                        }
                    }
                } catch (Exception e) {
                    log.error("Error reading SSE stream", e);
                    emitter.completeWithError(e);
                    return;
                }

                emitter.complete();
            }
        });
    }
}
