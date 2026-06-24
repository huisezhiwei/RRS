package com.rrs.dto;

import lombok.Data;

@Data
public class ExtractionRuleSaveRequest {
    private String ruleType;
    private String ruleContent;
}
