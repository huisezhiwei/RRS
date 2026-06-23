package com.rrs.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ChatMessageDTO {
    private Long id;
    private Long sessionId;
    private String role;
    private String content;
    private String fileName;
    private LocalDateTime createdAt;
}
