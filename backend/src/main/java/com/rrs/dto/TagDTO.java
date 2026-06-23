package com.rrs.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TagDTO {

    private Long id;

    @NotBlank(message = "标签名称不能为空")
    private String name;

    private String color;

    private LocalDateTime createdAt;
}
