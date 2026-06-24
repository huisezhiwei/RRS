package com.rrs.service;

import com.rrs.dto.MaterialDTO;
import com.rrs.dto.TagDTO;
import com.rrs.entity.Material;
import com.rrs.entity.MaterialLibrary;
import com.rrs.entity.Tag;
import com.rrs.exception.BusinessException;
import com.rrs.event.MaterialUploadedEvent;
import com.rrs.repository.MaterialLibraryRepository;
import com.rrs.repository.MaterialRepository;
import com.rrs.repository.TagRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class MaterialService {

    private final MaterialRepository materialRepository;
    private final MaterialLibraryRepository libraryRepository;
    private final TagRepository tagRepository;
    private final FileStorageService fileStorageService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public MaterialDTO upload(Long libraryId, MultipartFile file) {
        log.info("上传素材到素材库 {}, 文件: {}", libraryId, file.getOriginalFilename());

        MaterialLibrary library = libraryRepository.findById(libraryId)
                .orElseThrow(() -> new BusinessException(404, "素材库不存在"));

        // Store file
        FileStorageService.StoredFile storedFile = fileStorageService.store(
                file, library.getName(), library.getLibraryType());

        log.info("文件已存储: {}", storedFile.storedPath());

        // Create material entity
        Material material = new Material();
        material.setLibrary(library);
        material.setFileName(file.getOriginalFilename());
        material.setStoredName(storedFile.storedName());
        material.setStoredPath(storedFile.storedPath());
        material.setFileSize(storedFile.fileSize());
        material.setMimeType(storedFile.mimeType());

        material = materialRepository.save(material);
        log.info("素材记录已保存, id={}", material.getId());

        // Publish event for auto-extraction
        eventPublisher.publishEvent(new MaterialUploadedEvent(this, libraryId, material.getId()));

        return toDTO(material);
    }

    @Transactional(readOnly = true)
    public Page<MaterialDTO> listByLibrary(Long libraryId, int page, int size, String tagName) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "uploadedAt"));

        if (tagName != null && !tagName.isEmpty()) {
            List<Material> materials = materialRepository.findByLibraryIdAndTagName(libraryId, tagName);
            List<MaterialDTO> dtos = materials.stream().map(this::toDTO).collect(Collectors.toList());
            return new org.springframework.data.domain.PageImpl<>(dtos, pageRequest, dtos.size());
        }

        return materialRepository.findByLibraryId(libraryId, pageRequest)
                .map(this::toDTO);
    }

    public Resource download(Long materialId) {
        Material material = materialRepository.findById(materialId)
                .orElseThrow(() -> new BusinessException(404, "素材不存在"));
        log.info("下载素材: id={}, path={}", materialId, material.getStoredPath());
        return fileStorageService.load(material.getStoredPath());
    }

    public Material getMaterialById(Long id) {
        return materialRepository.findById(id)
                .orElseThrow(() -> new BusinessException(404, "素材不存在"));
    }

    @Transactional
    public void delete(Long materialId) {
        log.info("删除素材: id={}", materialId);

        Material material = materialRepository.findById(materialId)
                .orElseThrow(() -> new BusinessException(404, "素材不存在"));

        String storedPath = material.getStoredPath();

        // 先清除标签关联，避免外键冲突
        material.getTags().clear();
        materialRepository.save(material);

        // 删除数据库记录
        materialRepository.delete(material);
        materialRepository.flush();

        // 数据库操作完成后再删除物理文件
        log.info("数据库记录已删除，开始删除物理文件: {}", storedPath);
        fileStorageService.delete(storedPath);
    }

    @Transactional
    public MaterialDTO setTags(Long materialId, List<Long> tagIds) {
        log.info("设置素材标签: materialId={}, tagIds={}", materialId, tagIds);

        Material material = materialRepository.findById(materialId)
                .orElseThrow(() -> new BusinessException(404, "素材不存在"));

        material.getTags().clear();
        if (tagIds != null && !tagIds.isEmpty()) {
            material.getTags().addAll(tagRepository.findAllById(tagIds));
        }

        material = materialRepository.save(material);
        log.info("标签已更新, 共 {} 个标签", material.getTags().size());
        return toDTO(material);
    }

    private MaterialDTO toDTO(Material material) {
        MaterialDTO dto = new MaterialDTO();
        dto.setId(material.getId());
        dto.setLibraryId(material.getLibrary().getId());
        dto.setFileName(material.getFileName());
        dto.setStoredName(material.getStoredName());
        dto.setFileSize(material.getFileSize());
        dto.setMimeType(material.getMimeType());
        dto.setUploadedAt(material.getUploadedAt());
        dto.setCreatedAt(material.getCreatedAt());
        dto.setTags(material.getTags().stream().map(this::tagToDTO).collect(Collectors.toList()));
        return dto;
    }

    private TagDTO tagToDTO(Tag tag) {
        TagDTO dto = new TagDTO();
        dto.setId(tag.getId());
        dto.setName(tag.getName());
        dto.setColor(tag.getColor());
        dto.setCreatedAt(tag.getCreatedAt());
        return dto;
    }
}
