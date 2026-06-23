package com.rrs.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AiAgentDTO {
    private Long id;
    private String code;
    private String name;
    private String description;
    private String agentType;
    private Long credentialId;
    private String credentialName;
    private String modelName;
    private String systemPrompt;
    private Double temperature;
    private Integer maxTokens;
    private Double topP;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
