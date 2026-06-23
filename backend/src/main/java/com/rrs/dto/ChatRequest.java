package com.rrs.dto;

import lombok.Data;

@Data
public class ChatRequest {
    private Long sessionId;
    private String message;
    private String fileName;
    private String fileContent;
}
