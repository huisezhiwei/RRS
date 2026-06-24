package com.rrs.dto;

import lombok.Data;

@Data
public class DataModelDTO {
    private Long id;
    private String code;
    private String name;
    private String description;
    private String maintainer;
    private Long libraryId;
    private String libraryName;
    private String status;
    private String tableName;
    private String ddl;
    private String primaryKey;
    private String lastExtractedAt;
    private String createdAt;
    private String updatedAt;
    private Long dataCount;
}
