package com.rrs.dto;

import lombok.Data;

@Data
public class DataModelScheduleDTO {
    private Long id;
    private Long modelId;
    private String cronExpression;
    private Boolean enabled;
    private String scopeType;
    private String createdAt;
    private String updatedAt;
}
