package com.rrs.controller;

import com.rrs.dto.ApiResponse;
import com.rrs.dto.MaterialLibraryDTO;
import com.rrs.entity.LibraryType;
import com.rrs.service.MaterialLibraryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/libraries")
@RequiredArgsConstructor
public class MaterialLibraryController {

    private final MaterialLibraryService libraryService;

    @PostMapping
    public ApiResponse<MaterialLibraryDTO> create(@Valid @RequestBody MaterialLibraryDTO dto) {
        return ApiResponse.success(libraryService.create(dto));
    }

    @GetMapping
    public ApiResponse<List<MaterialLibraryDTO>> list(
            @RequestParam(required = false) LibraryType type,
            @RequestParam(required = false) String keyword) {
        return ApiResponse.success(libraryService.list(type, keyword));
    }

    @GetMapping("/{id}")
    public ApiResponse<MaterialLibraryDTO> getById(@PathVariable Long id) {
        return ApiResponse.success(libraryService.getById(id));
    }

    @PutMapping("/{id}")
    public ApiResponse<MaterialLibraryDTO> update(@PathVariable Long id, @RequestBody MaterialLibraryDTO dto) {
        return ApiResponse.success(libraryService.update(id, dto));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        libraryService.delete(id);
        return ApiResponse.success(null);
    }
}
