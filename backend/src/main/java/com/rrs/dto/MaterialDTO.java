package com.rrs.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class MaterialDTO {

    private Long id;

    private Long libraryId;

    private String fileName;

    private String storedName;

    private Long fileSize;

    private String mimeType;

    private LocalDateTime uploadedAt;

    private LocalDateTime createdAt;

    private List<TagDTO> tags;
}
