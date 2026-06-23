package com.rrs.dto;

import com.rrs.entity.CredentialType;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Data
public class CredentialDTO {
    private Long id;
    private String name;
    private CredentialType type;
    private Map<String, Object> params;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
