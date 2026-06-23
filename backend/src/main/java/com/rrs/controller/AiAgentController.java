package com.rrs.controller;

import com.rrs.dto.AiAgentConfigDTO;
import com.rrs.dto.AiAgentDTO;
import com.rrs.dto.ApiResponse;
import com.rrs.service.AiAgentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/ai-agents")
@RequiredArgsConstructor
@Slf4j
public class AiAgentController {

    private final AiAgentService aiAgentService;

    @GetMapping
    public ApiResponse<List<AiAgentDTO>> list() {
        log.info("List AI agents");
        return ApiResponse.success(aiAgentService.listAll());
    }

    @GetMapping("/{id}")
    public ApiResponse<AiAgentDTO> getById(@PathVariable Long id) {
        log.info("Get AI agent: {}", id);
        return ApiResponse.success(aiAgentService.getById(id));
    }

    @PutMapping("/{id}/config")
    public ApiResponse<AiAgentDTO> configure(
            @PathVariable Long id,
            @RequestBody AiAgentConfigDTO config) {
        log.info("Configure AI agent: {}", id);
        try {
            return ApiResponse.success(aiAgentService.configure(id, config));
        } catch (Exception e) {
            log.error("Configure agent failed", e);
            throw e;
        }
    }
}
