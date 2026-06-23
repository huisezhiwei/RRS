package com.rrs.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Map;

@Data
public class CredentialCreateDTO {
    @NotBlank(message = "name is required")
    private String name;

    @NotNull(message = "type is required")
    private String type;

    private Map<String, Object> params;
}
