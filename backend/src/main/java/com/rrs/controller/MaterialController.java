package com.rrs.controller;

import com.rrs.dto.ApiResponse;
import com.rrs.dto.MaterialDTO;
import com.rrs.entity.Material;
import com.rrs.service.MaterialService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@Slf4j
public class MaterialController {

    private final MaterialService materialService;

    @PostMapping("/api/libraries/{libraryId}/materials")
    public ApiResponse<MaterialDTO> upload(
            @PathVariable Long libraryId,
            @RequestParam("file") MultipartFile file) {
        log.info("Upload request: libraryId={}, file={}", libraryId, file.getOriginalFilename());
        try {
            return ApiResponse.success(materialService.upload(libraryId, file));
        } catch (Exception e) {
            log.error("Upload failed: libraryId={}", libraryId, e);
            throw e;
        }
    }

    @GetMapping("/api/libraries/{libraryId}/materials")
    public ApiResponse<Page<MaterialDTO>> list(
            @PathVariable Long libraryId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String tag) {
        log.info("List request: libraryId={}, page={}, size={}, tag={}", libraryId, page, size, tag);
        try {
            Page<MaterialDTO> result = materialService.listByLibrary(libraryId, page, size, tag);
            log.info("List result: {} items, total={}", result.getContent().size(), result.getTotalElements());
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("List failed: libraryId={}", libraryId, e);
            throw e;
        }
    }

    @GetMapping("/api/materials/{id}/download")
    public ResponseEntity<Resource> download(@PathVariable Long id) {
        log.info("Download request: id={}", id);
        try {
            Material material = materialService.getMaterialById(id);
            Resource resource = materialService.download(id);

            String encodedFileName = URLEncoder.encode(material.getFileName(), StandardCharsets.UTF_8)
                    .replace("+", "%20");

            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename*=UTF-8''" + encodedFileName)
                    .body(resource);
        } catch (Exception e) {
            log.error("Download failed: id={}", id, e);
            throw e;
        }
    }

    @DeleteMapping("/api/materials/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        log.info("Delete request: id={}", id);
        try {
            materialService.delete(id);
            return ApiResponse.success(null);
        } catch (Exception e) {
            log.error("Delete failed: id={}", id, e);
            throw e;
        }
    }

    @PutMapping("/api/materials/{materialId}/tags")
    public ApiResponse<MaterialDTO> setTags(
            @PathVariable Long materialId,
            @RequestBody Map<String, List<Long>> body) {
        List<Long> tagIds = body.get("tagIds");
        log.info("SetTags request: materialId={}, tagIds={}", materialId, tagIds);
        try {
            return ApiResponse.success(materialService.setTags(materialId, tagIds));
        } catch (Exception e) {
            log.error("SetTags failed: materialId={}", materialId, e);
            throw e;
        }
    }
}
