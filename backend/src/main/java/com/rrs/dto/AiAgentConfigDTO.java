package com.rrs.dto;

import lombok.Data;

@Data
public class AiAgentConfigDTO {
    private Long credentialId;
    private String modelName;
    private String systemPrompt;
    private Double temperature;
    private Integer maxTokens;
    private Double topP;
}
