package com.rrs.service;

import com.rrs.dto.ChatMessageDTO;
import com.rrs.dto.ChatRequest;
import com.rrs.dto.ChatSessionDTO;
import com.rrs.entity.*;
import com.rrs.exception.BusinessException;
import com.rrs.repository.ChatMessageRepository;
import com.rrs.repository.ChatSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatService {

    private final ChatSessionRepository sessionRepository;
    private final ChatMessageRepository messageRepository;
    private final AiAgentService aiAgentService;

    public List<ChatSessionDTO> listSessions(Long agentId) {
        return sessionRepository.findByAgentIdOrderByUpdatedAtDesc(agentId).stream()
                .map(this::sessionToDTO)
                .collect(Collectors.toList());
    }

    public ChatSessionDTO getSession(Long sessionId) {
        ChatSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new BusinessException(404, "Session not found"));
        return sessionToDTOWithMessages(session);
    }

    @Transactional
    public ChatSessionDTO createSession(Long agentId) {
        log.info("Creating chat session for agent: {}", agentId);
        aiAgentService.getEntity(agentId); // validate exists

        ChatSession session = new ChatSession();
        session.setAgentId(agentId);
        session.setTitle("New Chat");
        session = sessionRepository.save(session);
        return sessionToDTO(session);
    }

    @Transactional
    public void deleteSession(Long sessionId) {
        log.info("Deleting chat session: {}", sessionId);
        if (!sessionRepository.existsById(sessionId)) {
            throw new BusinessException(404, "Session not found");
        }
        sessionRepository.deleteById(sessionId);
    }

    @Transactional
    public ChatMessageDTO saveUserMessage(ChatRequest request) {
        ChatSession session;
        if (request.getSessionId() != null) {
            session = sessionRepository.findById(request.getSessionId())
                    .orElseThrow(() -> new BusinessException(404, "Session not found"));
        } else {
            throw new BusinessException(400, "sessionId is required");
        }

        // Save user message
        ChatMessage userMsg = new ChatMessage();
        userMsg.setSessionId(session.getId());
        userMsg.setRole(MessageRole.USER);
        userMsg.setContent(request.getMessage());
        userMsg.setFileName(request.getFileName());
        userMsg.setFileContent(request.getFileContent());
        userMsg = messageRepository.save(userMsg);

        // Update session title from first message
        long msgCount = messageRepository.findBySessionIdOrderByCreatedAtAsc(session.getId()).size();
        if (msgCount <= 1) {
            String title = request.getMessage();
            if (title.length() > 50) title = title.substring(0, 50) + "...";
            session.setTitle(title);
            sessionRepository.save(session);
        }

        return messageToDTO(userMsg);
    }

    @Transactional
    public ChatMessageDTO saveAssistantMessage(Long sessionId, String content) {
        if (!sessionRepository.existsById(sessionId)) {
            throw new BusinessException(404, "Session not found");
        }
        ChatMessage msg = new ChatMessage();
        msg.setSessionId(sessionId);
        msg.setRole(MessageRole.ASSISTANT);
        msg.setContent(content);
        msg = messageRepository.save(msg);
        return messageToDTO(msg);
    }

    public List<ChatMessageDTO> getMessages(Long sessionId) {
        return messageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId).stream()
                .map(this::messageToDTO)
                .collect(Collectors.toList());
    }

    // ---- helpers ----

    private ChatSessionDTO sessionToDTO(ChatSession entity) {
        ChatSessionDTO dto = new ChatSessionDTO();
        dto.setId(entity.getId());
        dto.setAgentId(entity.getAgentId());
        dto.setTitle(entity.getTitle());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());
        return dto;
    }

    private ChatSessionDTO sessionToDTOWithMessages(ChatSession entity) {
        ChatSessionDTO dto = sessionToDTO(entity);
        dto.setMessages(entity.getMessages().stream()
                .map(this::messageToDTO)
                .collect(Collectors.toList()));
        return dto;
    }

    private ChatMessageDTO messageToDTO(ChatMessage entity) {
        ChatMessageDTO dto = new ChatMessageDTO();
        dto.setId(entity.getId());
        dto.setSessionId(entity.getSessionId());
        dto.setRole(entity.getRole().name());
        dto.setContent(entity.getContent());
        dto.setFileName(entity.getFileName());
        dto.setCreatedAt(entity.getCreatedAt());
        return dto;
    }
}
