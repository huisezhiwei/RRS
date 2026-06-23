package com.rrs.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class ChatSessionDTO {
    private Long id;
    private Long agentId;
    private String title;
    private List<ChatMessageDTO> messages;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
