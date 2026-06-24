package com.rrs.dto;

import lombok.Data;

@Data
public class DdlConfirmRequest {
    private String ddl;
    private String primaryKey;
}
