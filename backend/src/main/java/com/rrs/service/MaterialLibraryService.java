package com.rrs.service;

import com.rrs.dto.MaterialLibraryDTO;
import com.rrs.entity.LibraryType;
import com.rrs.entity.MaterialLibrary;
import com.rrs.exception.BusinessException;
import com.rrs.repository.MaterialLibraryRepository;
import com.rrs.repository.MaterialRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class MaterialLibraryService {

    private final MaterialLibraryRepository libraryRepository;
    private final MaterialRepository materialRepository;
    private final FileStorageService fileStorageService;

    @Transactional
    public MaterialLibraryDTO create(MaterialLibraryDTO dto) {
        // Check if code already exists
        if (libraryRepository.findByCode(dto.getCode()).isPresent()) {
            throw new BusinessException("素材库编码已存在: " + dto.getCode());
        }

        MaterialLibrary library = new MaterialLibrary();
        library.setCode(dto.getCode());
        library.setName(dto.getName());
        library.setDescription(dto.getDescription());
        library.setMaintainer(dto.getMaintainer());
        library.setLibraryType(dto.getLibraryType());

        library = libraryRepository.save(library);
        return toDTO(library);
    }

    public List<MaterialLibraryDTO> list(LibraryType type, String keyword) {
        List<MaterialLibrary> libraries;

        if (type != null && keyword != null && !keyword.isEmpty()) {
            libraries = libraryRepository.searchByTypeAndKeyword(type, keyword);
        } else if (type != null) {
            libraries = libraryRepository.findByLibraryType(type);
        } else if (keyword != null && !keyword.isEmpty()) {
            libraries = libraryRepository.searchByKeyword(keyword);
        } else {
            libraries = libraryRepository.findAll();
        }

        return libraries.stream().map(this::toDTOWithStats).collect(Collectors.toList());
    }

    public MaterialLibraryDTO getById(Long id) {
        MaterialLibrary library = libraryRepository.findById(id)
                .orElseThrow(() -> new BusinessException(404, "素材库不存在"));
        return toDTOWithStats(library);
    }

    @Transactional
    public MaterialLibraryDTO update(Long id, MaterialLibraryDTO dto) {
        MaterialLibrary library = libraryRepository.findById(id)
                .orElseThrow(() -> new BusinessException(404, "素材库不存在"));

        if (dto.getName() != null) {
            library.setName(dto.getName());
        }
        if (dto.getDescription() != null) {
            library.setDescription(dto.getDescription());
        }
        if (dto.getMaintainer() != null) {
            library.setMaintainer(dto.getMaintainer());
        }

        library = libraryRepository.save(library);
        return toDTOWithStats(library);
    }

    @Transactional
    public void delete(Long id) {
        MaterialLibrary library = libraryRepository.findById(id)
                .orElseThrow(() -> new BusinessException(404, "素材库不存在"));

        log.info("删除素材库: id={}, name={}", id, library.getName());

        // 删除数据库记录（级联删除所有素材记录）
        libraryRepository.delete(library);
        libraryRepository.flush();

        // 数据库操作完成后删除物理文件目录
        fileStorageService.deleteLibraryFiles(library.getName(), library.getLibraryType());
    }

    private MaterialLibraryDTO toDTO(MaterialLibrary library) {
        MaterialLibraryDTO dto = new MaterialLibraryDTO();
        dto.setId(library.getId());
        dto.setCode(library.getCode());
        dto.setName(library.getName());
        dto.setDescription(library.getDescription());
        dto.setMaintainer(library.getMaintainer());
        dto.setLibraryType(library.getLibraryType());
        dto.setCreatedAt(library.getCreatedAt());
        dto.setUpdatedAt(library.getUpdatedAt());
        return dto;
    }

    private MaterialLibraryDTO toDTOWithStats(MaterialLibrary library) {
        MaterialLibraryDTO dto = toDTO(library);
        dto.setMaterialCount(materialRepository.countByLibraryId(library.getId()));
        materialRepository.findTopByLibraryIdOrderByUploadedAtDesc(library.getId())
                .ifPresent(m -> dto.setLastUploadedAt(m.getUploadedAt()));
        return dto;
    }
}
