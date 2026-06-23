package com.rrs.controller;

import com.rrs.dto.ApiResponse;
import com.rrs.dto.TagDTO;
import com.rrs.service.TagService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tags")
@RequiredArgsConstructor
public class TagController {

    private final TagService tagService;

    @GetMapping
    public ApiResponse<List<TagDTO>> listAll() {
        return ApiResponse.success(tagService.listAll());
    }

    @PostMapping
    public ApiResponse<TagDTO> create(@Valid @RequestBody TagDTO dto) {
        return ApiResponse.success(tagService.create(dto));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        tagService.delete(id);
        return ApiResponse.success(null);
    }
}
