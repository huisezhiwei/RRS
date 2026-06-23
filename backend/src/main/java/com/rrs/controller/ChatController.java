package com.rrs.controller;

import com.rrs.dto.*;
import com.rrs.entity.AiAgent;
import com.rrs.service.AiAgentService;
import com.rrs.service.ChatService;
import com.rrs.service.LlmChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.*;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
@Slf4j
public class ChatController {

    private final ChatService chatService;
    private final AiAgentService aiAgentService;
    private final LlmChatService llmChatService;

    @GetMapping("/sessions")
    public ApiResponse<List<ChatSessionDTO>> listSessions(@RequestParam Long agentId) {
        log.info("List sessions for agent: {}", agentId);
        return ApiResponse.success(chatService.listSessions(agentId));
    }

    @GetMapping("/sessions/{sessionId}")
    public ApiResponse<ChatSessionDTO> getSession(@PathVariable Long sessionId) {
        log.info("Get session: {}", sessionId);
        return ApiResponse.success(chatService.getSession(sessionId));
    }

    @PostMapping("/sessions")
    public ApiResponse<ChatSessionDTO> createSession(@RequestParam Long agentId) {
        log.info("Create session for agent: {}", agentId);
        try {
            return ApiResponse.success(chatService.createSession(agentId));
        } catch (Exception e) {
            log.error("Create session failed", e);
            throw e;
        }
    }

    @DeleteMapping("/sessions/{sessionId}")
    public ApiResponse<Void> deleteSession(@PathVariable Long sessionId) {
        log.info("Delete session: {}", sessionId);
        try {
            chatService.deleteSession(sessionId);
            return ApiResponse.success(null);
        } catch (Exception e) {
            log.error("Delete session failed", e);
            throw e;
        }
    }

    /**
     * Send a message and stream the AI response via SSE.
     * Client should call /save-response after stream completes.
     */
    @PostMapping("/stream")
    public SseEmitter streamChat(@RequestBody ChatRequest request) {
        log.info("Stream chat: sessionId={}", request.getSessionId());
        try {
            // Save user message first
            chatService.saveUserMessage(request);

            // Get session to find agent
            ChatSessionDTO session = chatService.getSession(request.getSessionId());
            AiAgent agent = aiAgentService.getEntity(session.getAgentId());

            // Build messages list (last 5 conversations + system prompt)
            List<ChatMessageDTO> history = session.getMessages();
            List<Map<String, String>> messages = new ArrayList<>();

            // Add system prompt if configured
            if (agent.getSystemPrompt() != null && !agent.getSystemPrompt().isBlank()) {
                Map<String, String> sysMsg = new HashMap<>();
                sysMsg.put("role", "system");
                sysMsg.put("content", agent.getSystemPrompt());
                messages.add(sysMsg);
            }

            // Add last 5 conversations (10 messages max: 5 user + 5 assistant)
            int startIdx = Math.max(0, history.size() - 10);
            for (int i = startIdx; i < history.size(); i++) {
                ChatMessageDTO msg = history.get(i);
                Map<String, String> m = new HashMap<>();
                m.put("role", msg.getRole().toLowerCase());
                String content = msg.getContent();
                if (msg.getFileName() != null) {
                    content = "[File: " + msg.getFileName() + "]\n" + content;
                }
                m.put("content", content);
                messages.add(m);
            }

            // Create SSE emitter (5 min timeout) and stream
            SseEmitter emitter = new SseEmitter(300000L);
            llmChatService.streamChat(agent, messages, emitter);
            return emitter;

        } catch (Exception e) {
            log.error("Stream chat failed", e);
            SseEmitter emitter = new SseEmitter();
            try {
                emitter.send(SseEmitter.event().name("error")
                        .data("{\"error\":\"" + e.getMessage() + "\"}"));
                emitter.complete();
            } catch (Exception ignored) {
                emitter.completeWithError(e);
            }
            return emitter;
        }
    }

    /**
     * Save assistant response after streaming completes.
     */
    @PostMapping("/save-response")
    public ApiResponse<ChatMessageDTO> saveAssistantResponse(@RequestBody ChatRequest request) {
        log.info("Save assistant response: sessionId={}", request.getSessionId());
        try {
            return ApiResponse.success(chatService.saveAssistantMessage(
                    request.getSessionId(), request.getMessage()));
        } catch (Exception e) {
            log.error("Save response failed", e);
            throw e;
        }
    }
}
