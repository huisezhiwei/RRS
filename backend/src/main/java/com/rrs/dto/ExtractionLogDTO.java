package com.rrs.dto;

import lombok.Data;

@Data
public class ExtractionLogDTO {
    private Long id;
    private Long modelId;
    private String triggerType;
    private String scopeType;
    private String status;
    private Integer totalFiles;
    private Integer processedFiles;
    private Integer successRecords;
    private Integer failedRecords;
    private String logContent;
    private String startedAt;
    private String finishedAt;
    private String createdAt;
}
