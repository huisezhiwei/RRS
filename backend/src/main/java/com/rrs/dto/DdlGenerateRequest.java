package com.rrs.dto;

import lombok.Data;

@Data
public class DdlGenerateRequest {
    private Long agentId;
    private java.util.List<Long> fileIds;
}
