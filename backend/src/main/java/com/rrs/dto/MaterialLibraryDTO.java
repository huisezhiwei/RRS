package com.rrs.dto;

import com.rrs.entity.LibraryType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class MaterialLibraryDTO {

    private Long id;

    @NotBlank(message = "英文编码不能为空")
    @Pattern(regexp = "^[a-zA-Z][a-zA-Z0-9_]*$", message = "英文编码必须以字母开头，只能包含字母、数字和下划线")
    private String code;

    @NotBlank(message = "中文名称不能为空")
    private String name;

    private String description;

    private String maintainer;

    @NotNull(message = "素材库类型不能为空")
    private LibraryType libraryType;

    private Long materialCount;

    private LocalDateTime lastUploadedAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
