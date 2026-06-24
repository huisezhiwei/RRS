package com.rrs.dto;

import lombok.Data;

@Data
public class ExtractionRequest {
    private String scopeType;
    private java.util.List<Long> fileIds;
}
