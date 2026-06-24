package com.rrs.controller;

import com.rrs.dto.ApiResponse;
import com.rrs.dto.CredentialCreateDTO;
import com.rrs.dto.CredentialDTO;
import com.rrs.entity.CredentialType;
import com.rrs.service.CredentialService;
import com.rrs.service.DatabaseCredentialService;
import com.rrs.service.LlmService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@Slf4j
public class CredentialController {

    private final CredentialService credentialService;
    private final LlmService llmService;
    private final DatabaseCredentialService databaseCredentialService;

    @GetMapping("/api/credentials")
    public ApiResponse<List<CredentialDTO>> list(
            @RequestParam(required = false) String type) {
        log.info("List credentials, type={}", type);
        try {
            List<CredentialDTO> result;
            if (type != null && !type.isBlank()) {
                try {
                    result = credentialService.listByType(CredentialType.valueOf(type.toUpperCase()));
                } catch (IllegalArgumentException e) {
                    throw new com.rrs.exception.BusinessException(400, "Invalid credential type: " + type);
                }
            } else {
                result = credentialService.listAll();
            }
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("List credentials failed", e);
            throw e;
        }
    }

    @GetMapping("/api/credentials/{id}")
    public ApiResponse<CredentialDTO> getById(@PathVariable Long id) {
        log.info("Get credential: {}", id);
        return ApiResponse.success(credentialService.getById(id));
    }

    @PostMapping("/api/credentials")
    public ApiResponse<CredentialDTO> create(@Valid @RequestBody CredentialCreateDTO dto) {
        log.info("Create credential: {}", dto.getName());
        try {
            return ApiResponse.success(credentialService.create(dto));
        } catch (Exception e) {
            log.error("Create credential failed", e);
            throw e;
        }
    }

    @PutMapping("/api/credentials/{id}")
    public ApiResponse<CredentialDTO> update(@PathVariable Long id, @Valid @RequestBody CredentialCreateDTO dto) {
        log.info("Update credential: {}", id);
        try {
            return ApiResponse.success(credentialService.update(id, dto));
        } catch (Exception e) {
            log.error("Update credential failed", e);
            throw e;
        }
    }

    @DeleteMapping("/api/credentials/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        log.info("Delete credential: {}", id);
        try {
            credentialService.delete(id);
            return ApiResponse.success(null);
        } catch (Exception e) {
            log.error("Delete credential failed", e);
            throw e;
        }
    }

    // ---- LLM specific ----

    @GetMapping("/api/credentials/{id}/llm/models")
    public ApiResponse<List<String>> fetchLlmModels(@PathVariable Long id) {
        log.info("Fetch LLM models for credential: {}", id);
        try {
            return ApiResponse.success(llmService.fetchModels(id));
        } catch (Exception e) {
            log.error("Fetch LLM models failed", e);
            throw e;
        }
    }

    @PostMapping("/api/credentials/{id}/test")
    public ApiResponse<String> testConnection(@PathVariable Long id) {
        log.info("Test credential: {}", id);
        try {
            var credential = credentialService.getEntity(id);
            String result;
            if (credential.getType() == CredentialType.LLM) {
                result = llmService.testConnection(id);
            } else {
                result = databaseCredentialService.testConnection(id);
            }
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("Test credential failed", e);
            throw e;
        }
    }

    @PostMapping("/api/credentials/test")
    public ApiResponse<String> testDirect(@RequestBody Map<String, Object> body) {
        log.info("Test credential with direct params");
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> params = (Map<String, Object>) body.get("params");
            String type = (String) body.get("type");
            String result;
            if ("LLM".equalsIgnoreCase(type)) {
                result = llmService.testConnectionDirect(params);
            } else {
                result = databaseCredentialService.testConnectionDirect(params);
            }
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("Test direct failed", e);
            throw e;
        }
    }
}
