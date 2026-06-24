package com.rrs.controller;

import com.rrs.dto.*;
import com.rrs.entity.AiAgent;
import com.rrs.entity.CredentialType;
import com.rrs.entity.Material;
import com.rrs.repository.AiAgentRepository;
import com.rrs.repository.CredentialRepository;
import com.rrs.repository.MaterialRepository;
import com.rrs.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/data-models")
@RequiredArgsConstructor
@Slf4j
public class DataModelController {

    private final DataModelService dataModelService;
    private final DdlGenerationService ddlGenerationService;
    private final ExtractionService extractionService;
    private final DataModelScheduleService scheduleService;
    private final MaterialRepository materialRepository;
    private final AiAgentRepository aiAgentRepository;
    private final CredentialRepository credentialRepository;

    // ---- CRUD ----

    @PostMapping
    public ApiResponse<DataModelDTO> create(@RequestBody DataModelCreateDTO dto) {
        log.info("Create data model: {}", dto.getCode());
        return ApiResponse.success(dataModelService.create(dto));
    }

    @GetMapping
    public ApiResponse<List<DataModelDTO>> list(@RequestParam(required = false) String keyword) {
        return ApiResponse.success(dataModelService.list(keyword));
    }

    @GetMapping("/{id}")
    public ApiResponse<DataModelDTO> getById(@PathVariable Long id) {
        return ApiResponse.success(dataModelService.getById(id));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        dataModelService.delete(id);
        return ApiResponse.success(null);
    }

    // ---- Init Wizard ----

    @PostMapping("/{id}/init/generate-ddl")
    public ApiResponse<String> generateDdl(@PathVariable Long id, @RequestBody DdlGenerateRequest request) {
        log.info("Generate DDL for model {}: agentId={}", id, request.getAgentId());
        String ddl = ddlGenerationService.generate(id, request.getAgentId(), request.getFileIds());
        return ApiResponse.success(ddl);
    }

    @PostMapping("/{id}/init/confirm-ddl")
    public ApiResponse<Void> confirmDdl(@PathVariable Long id, @RequestBody DdlConfirmRequest request) {
        log.info("Confirm DDL for model {}", id);
        dataModelService.confirmDdl(id, request);
        return ApiResponse.success(null);
    }

    @GetMapping("/{id}/init/agents")
    public ApiResponse<List<AiAgentDTO>> getAvailableAgents(@PathVariable Long id) {
        // Return agents that have LLM credentials configured
        List<AiAgentDTO> agents = aiAgentRepository.findAll().stream()
                .filter(a -> a.getCredentialId() != null)
                .filter(a -> credentialRepository.findById(a.getCredentialId())
                        .map(c -> c.getType() == CredentialType.LLM).orElse(false))
                .map(this::agentToDTO)
                .collect(Collectors.toList());
        return ApiResponse.success(agents);
    }

    @GetMapping("/{id}/init/library-materials")
    public ApiResponse<List<MaterialDTO>> getLibraryMaterials(@PathVariable Long id) {
        var model = dataModelService.getEntity(id);
        if (model.getLibraryId() == null) {
            return ApiResponse.success(List.of());
        }
        List<MaterialDTO> materials = materialRepository.findByLibraryId(model.getLibraryId()).stream()
                .map(this::materialToDTO)
                .collect(Collectors.toList());
        return ApiResponse.success(materials);
    }

    // ---- Extraction Rules ----

    @GetMapping("/{id}/init/rules")
    public ApiResponse<ExtractionRuleDTO> getActiveRule(@PathVariable Long id) {
        return ApiResponse.success(dataModelService.getActiveRule(id));
    }

    @PostMapping("/{id}/init/rules")
    public ApiResponse<ExtractionRuleDTO> saveRule(@PathVariable Long id, @RequestBody ExtractionRuleSaveRequest request) {
        return ApiResponse.success(dataModelService.saveRule(id, request));
    }

    // ---- Extraction ----

    @PostMapping("/{id}/extract")
    public ApiResponse<ExtractionLogDTO> triggerExtraction(@PathVariable Long id, @RequestBody ExtractionRequest request) {
        log.info("Trigger extraction for model {}: scope={}", id, request.getScopeType());
        return ApiResponse.success(extractionService.extract(id, request, null));
    }

    @GetMapping("/{id}/extract/progress")
    public SseEmitter streamProgress(@PathVariable Long id) {
        SseEmitter emitter = new SseEmitter(600000L); // 10 min timeout
        extractionService.registerEmitter(id, emitter);
        return emitter;
    }

    @GetMapping("/{id}/extract/logs")
    public ApiResponse<List<ExtractionLogDTO>> getLogs(@PathVariable Long id,
                                                       @RequestParam(defaultValue = "0") int page,
                                                       @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(dataModelService.getExtractionLogs(id, page, size));
    }

    @GetMapping("/{id}/extract/files")
    public ApiResponse<List<MaterialDTO>> getExtractableFiles(@PathVariable Long id) {
        var model = dataModelService.getEntity(id);
        if (model.getLibraryId() == null) return ApiResponse.success(List.of());
        List<MaterialDTO> files = materialRepository.findByLibraryId(model.getLibraryId()).stream()
                .map(this::materialToDTO)
                .collect(Collectors.toList());
        return ApiResponse.success(files);
    }

    // ---- Dynamic Table Data ----

    @GetMapping("/{id}/data")
    public ApiResponse<DynamicTablePageDTO> queryData(@PathVariable Long id,
                                                      @RequestParam(defaultValue = "0") int page,
                                                      @RequestParam(defaultValue = "20") int size,
                                                      @RequestParam(required = false) String keyword) {
        return ApiResponse.success(dataModelService.queryDynamicTable(id, page, size, keyword));
    }

    @PutMapping("/{id}/data/{rowId}")
    public ApiResponse<Void> updateRow(@PathVariable Long id, @PathVariable Long rowId,
                                       @RequestBody DynamicTableRowUpdateDTO request) {
        dataModelService.updateDynamicRow(id, rowId, request.getData());
        return ApiResponse.success(null);
    }

    // ---- DDL Maintenance ----

    @PutMapping("/{id}/ddl")
    public ApiResponse<Void> modifyDdl(@PathVariable Long id, @RequestBody DdlConfirmRequest request) {
        log.info("Modify DDL for model {}", id);
        dataModelService.modifyDdl(id, request.getDdl(), request.getPrimaryKey());
        return ApiResponse.success(null);
    }

    // ---- Schedule ----

    @GetMapping("/{id}/schedule")
    public ApiResponse<DataModelScheduleDTO> getSchedule(@PathVariable Long id) {
        return ApiResponse.success(scheduleService.getSchedule(id));
    }

    @PostMapping("/{id}/schedule")
    public ApiResponse<DataModelScheduleDTO> saveSchedule(@PathVariable Long id, @RequestBody DataModelScheduleDTO dto) {
        return ApiResponse.success(scheduleService.saveSchedule(id, dto));
    }

    @DeleteMapping("/{id}/schedule")
    public ApiResponse<Void> deleteSchedule(@PathVariable Long id) {
        scheduleService.removeSchedule(id);
        return ApiResponse.success(null);
    }

    // ---- Helpers ----

    private AiAgentDTO agentToDTO(AiAgent entity) {
        AiAgentDTO dto = new AiAgentDTO();
        dto.setId(entity.getId());
        dto.setCode(entity.getCode());
        dto.setName(entity.getName());
        dto.setDescription(entity.getDescription());
        dto.setAgentType(entity.getAgentType().name());
        dto.setCredentialId(entity.getCredentialId());
        dto.setModelName(entity.getModelName());
        dto.setSystemPrompt(entity.getSystemPrompt());
        dto.setTemperature(entity.getTemperature());
        dto.setMaxTokens(entity.getMaxTokens());
        dto.setTopP(entity.getTopP());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());
        return dto;
    }

    private MaterialDTO materialToDTO(Material m) {
        MaterialDTO dto = new MaterialDTO();
        dto.setId(m.getId());
        dto.setLibraryId(m.getLibrary().getId());
        dto.setFileName(m.getFileName());
        dto.setStoredName(m.getStoredName());
        dto.setFileSize(m.getFileSize());
        dto.setMimeType(m.getMimeType());
        dto.setUploadedAt(m.getUploadedAt());
        dto.setCreatedAt(m.getCreatedAt());
        dto.setTags(List.of());
        return dto;
    }
}
