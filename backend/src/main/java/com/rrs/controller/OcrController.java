package com.rrs.controller;

import com.rrs.dto.ApiResponse;
import com.rrs.dto.OcrResultDTO;
import com.rrs.service.OcrService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/ocr")
@RequiredArgsConstructor
@Slf4j
public class OcrController {

    private final OcrService ocrService;

    @PostMapping("/process")
    public ApiResponse<OcrResultDTO> process(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "mode", defaultValue = "LOCAL") String mode,
            @RequestParam(value = "agentId", required = false) Long agentId) {
        log.info("OCR process: file={}, mode={}, agentId={}", file.getOriginalFilename(), mode, agentId);
        try {
            String mdContent;
            if ("LLM".equalsIgnoreCase(mode)) {
                mdContent = ocrService.processLlm(file, agentId);
            } else {
                mdContent = ocrService.processLocal(file);
            }

            OcrResultDTO dto = new OcrResultDTO();
            dto.setMdContent(mdContent);
            dto.setFileName(file.getOriginalFilename());
            return ApiResponse.success(dto);

        } catch (Exception e) {
            log.error("OCR process failed", e);
            throw e;
        }
    }
}
