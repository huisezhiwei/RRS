package com.rrs.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rrs.entity.AiAgent;
import com.rrs.entity.Credential;
import com.rrs.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class LlmService {

    private final CredentialService credentialService;
    private final AiAgentService aiAgentService;
    private final ObjectMapper objectMapper;

    private static final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();

    /**
     * Fetch available models from the LLM API.
     */
    public List<String> fetchModels(Long credentialId) {
        log.info("Fetching models for credential: {}", credentialId);
        Credential credential = credentialService.getEntity(credentialId);
        Map<String, Object> params = credentialService.getParams(credential);

        String apiUrl = getRequired(params, "apiUrl");
        String apiKey = getRequired(params, "apiKey");

        // Ensure apiUrl doesn't end with /
        if (apiUrl.endsWith("/")) {
            apiUrl = apiUrl.substring(0, apiUrl.length() - 1);
        }

        String modelsUrl = apiUrl + "/v1/models";

        Request request = new Request.Builder()
                .url(modelsUrl)
                .header("Authorization", "Bearer " + apiKey)
                .get()
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String body = response.body() != null ? response.body().string() : "";
                throw new BusinessException(502, "LLM API returned " + response.code() + ": " + body);
            }

            String body = response.body() != null ? response.body().string() : "[]";
            JsonNode root = objectMapper.readTree(body);
            JsonNode data = root.get("data");

            List<String> models = new ArrayList<>();
            if (data != null && data.isArray()) {
                for (JsonNode node : data) {
                    JsonNode idNode = node.get("id");
                    if (idNode != null) {
                        models.add(idNode.asText());
                    }
                }
            }

            log.info("Found {} models", models.size());
            return models;

        } catch (IOException e) {
            log.error("Failed to fetch models", e);
            throw new BusinessException(502, "Failed to connect to LLM API: " + e.getMessage());
        }
    }

    /**
     * Test LLM API connectivity by calling the models endpoint.
     */
    public String testConnection(Long credentialId) {
        log.info("Testing LLM connection for credential: {}", credentialId);
        List<String> models = fetchModels(credentialId);
        return "Connection successful. Found " + models.size() + " models.";
    }

    /**
     * Test LLM API with raw params (before saving).
     */
    public String testConnectionDirect(Map<String, Object> params) {
        log.info("Testing LLM connection with direct params");
        String apiUrl = getRequired(params, "apiUrl");
        String apiKey = getRequired(params, "apiKey");

        if (apiUrl.endsWith("/")) {
            apiUrl = apiUrl.substring(0, apiUrl.length() - 1);
        }

        String modelsUrl = apiUrl + "/v1/models";
        Request request = new Request.Builder()
                .url(modelsUrl)
                .header("Authorization", "Bearer " + apiKey)
                .get()
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String body = response.body() != null ? response.body().string() : "";
                return "FAILED: HTTP " + response.code() + " - " + body;
            }
            return "Connection successful. HTTP " + response.code();
        } catch (IOException e) {
            return "FAILED: " + e.getMessage();
        }
    }

    /**
     * Generic non-streaming LLM call for data model tasks.
     */
    public String callLlm(Long agentId, List<Map<String, String>> messages) {
        log.info("Generic LLM call for agent: {}", agentId);
        AiAgent agent = aiAgentService.getEntity(agentId);
        if (agent.getCredentialId() == null) {
            throw new BusinessException(400, "Agent 未配置 LLM 凭证");
        }
        Credential credential = credentialService.getEntity(agent.getCredentialId());
        Map<String, Object> params = credentialService.getParams(credential);
        String apiUrl = getRequired(params, "apiUrl");
        String apiKey = getRequired(params, "apiKey");
        if (apiUrl.endsWith("/")) apiUrl = apiUrl.substring(0, apiUrl.length() - 1);
        String chatUrl = apiUrl + "/v1/chat/completions";

        try {
            com.fasterxml.jackson.databind.node.ObjectNode reqBody = objectMapper.createObjectNode();
            reqBody.put("model", agent.getModelName());
            reqBody.put("stream", false);
            reqBody.put("temperature", agent.getTemperature() != null ? agent.getTemperature() : 0.3);
            if (agent.getMaxTokens() != null) reqBody.put("max_tokens", agent.getMaxTokens());

            com.fasterxml.jackson.databind.node.ArrayNode msgsNode = objectMapper.createArrayNode();
            for (Map<String, String> msg : messages) {
                com.fasterxml.jackson.databind.node.ObjectNode mn = objectMapper.createObjectNode();
                mn.put("role", msg.get("role"));
                mn.put("content", msg.get("content"));
                msgsNode.add(mn);
            }
            reqBody.set("messages", msgsNode);

            String jsonBody = objectMapper.writeValueAsString(reqBody);
            RequestBody body = RequestBody.create(jsonBody, MediaType.parse("application/json"));
            Request request = new Request.Builder()
                    .url(chatUrl)
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .post(body)
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    String errBody = response.body() != null ? response.body().string() : "";
                    throw new BusinessException(500, "LLM API 返回错误 " + response.code() + ": " + errBody);
                }
                String body2 = response.body() != null ? response.body().string() : "";
                com.fasterxml.jackson.databind.JsonNode json = objectMapper.readTree(body2);
                return json.get("choices").get(0).get("message").get("content").asText("");
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("LLM call failed", e);
            throw new BusinessException(500, "LLM 调用失败: " + e.getMessage());
        }
    }

    private String getRequired(Map<String, Object> params, String key) {
        Object val = params.get(key);
        if (val == null || val.toString().isBlank()) {
            throw new BusinessException(400, "Missing required param: " + key);
        }
        return val.toString().trim();
    }
}
