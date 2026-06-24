package com.rrs.dto;

import lombok.Data;

@Data
public class ExtractionRuleDTO {
    private Long id;
    private Long modelId;
    private Integer version;
    private String ruleType;
    private String ruleContent;
    private Boolean active;
    private String createdAt;
}
