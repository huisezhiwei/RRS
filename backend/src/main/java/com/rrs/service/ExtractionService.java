package com.rrs.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rrs.dto.ExtractionLogDTO;
import com.rrs.dto.ExtractionRequest;
import com.rrs.entity.*;
import com.rrs.exception.BusinessException;
import com.rrs.repository.ExtractionLogRepository;
import com.rrs.repository.MaterialLibraryRepository;
import com.rrs.repository.MaterialRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExtractionService {

    private final DataModelService dataModelService;
    private final MaterialRepository materialRepository;
    private final MaterialLibraryRepository libraryRepository;
    private final ExtractionLogRepository extractionLogRepository;
    private final OcrService ocrService;
    private final LlmService llmService;
    private final FileStorageService fileStorageService;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;

    private final ConcurrentHashMap<Long, SseEmitter> emitters = new ConcurrentHashMap<>();

    public void registerEmitter(Long modelId, SseEmitter emitter) {
        emitters.put(modelId, emitter);
        emitter.onCompletion(() -> emitters.remove(modelId));
        emitter.onTimeout(() -> emitters.remove(modelId));
    }

    public ExtractionLogDTO extract(Long modelId, ExtractionRequest request, TriggerType triggerType) {
        DataModel model = dataModelService.getEntity(modelId);
        if (model.getStatus() != DataModelStatus.READY) {
            throw new BusinessException(400, "数据模型尚未初始化完成");
        }

        ExtractionRule rule = dataModelService.getActiveRule(modelId) != null
                ? toEntity(dataModelService.getActiveRule(modelId))
                : null;

        ScopeType scope = request.getScopeType() != null
                ? ScopeType.valueOf(request.getScopeType())
                : ScopeType.FULL;
        TriggerType trigger = triggerType != null ? triggerType : TriggerType.MANUAL;

        // Create log entry
        ExtractionLog logEntry = new ExtractionLog();
        logEntry.setModelId(modelId);
        logEntry.setTriggerType(trigger);
        logEntry.setScopeType(scope);
        logEntry.setStatus(ExtractionStatus.RUNNING);
        logEntry.setStartedAt(LocalDateTime.now());
        logEntry = extractionLogRepository.save(logEntry);

        // Get files
        List<Material> files;
        if (request.getFileIds() != null && !request.getFileIds().isEmpty()) {
            files = materialRepository.findAllById(request.getFileIds());
        } else {
            files = getFilesForExtraction(model, scope);
        }

        logEntry.setTotalFiles(files.size());
        extractionLogRepository.save(logEntry);

        StringBuilder logContent = new StringBuilder();
        int successRecords = 0;
        int failedRecords = 0;

        try {
            MaterialLibrary library = getLibrary(model);
            int processedFiles = 0;

            for (Material file : files) {
                processedFiles++;
                String progress = String.format("[%d/%d] 处理文件: %s", processedFiles, files.size(), file.getFileName());
                logContent.append(progress).append("\n");
                sendProgress(modelId, file.getFileName(), processedFiles, files.size(), successRecords, failedRecords, progress);

                try {
                    int count;
                    if (library.getLibraryType() == LibraryType.EXCEL) {
                        count = extractExcelFile(model, rule, file, logContent);
                    } else {
                        count = extractImageFile(model, rule, file, logContent);
                    }
                    successRecords += count;
                    logContent.append("  -> 成功抽取 ").append(count).append(" 条记录\n");
                } catch (Exception e) {
                    failedRecords++;
                    logContent.append("  -> 失败: ").append(e.getMessage()).append("\n");
                    log.warn("Extraction failed for file {}: {}", file.getFileName(), e.getMessage());
                }

                logEntry.setProcessedFiles(processedFiles);
                logEntry.setSuccessRecords(successRecords);
                logEntry.setFailedRecords(failedRecords);
                extractionLogRepository.save(logEntry);
            }

            // Success
            logEntry.setStatus(ExtractionStatus.SUCCESS);
            logEntry.setFinishedAt(LocalDateTime.now());
            logEntry.setLogContent(logContent.toString());
            extractionLogRepository.save(logEntry);

            // Update model's lastExtractedAt
            model.setLastExtractedAt(LocalDateTime.now());
            // Need to save via repository - use DataModelService approach
            log.info("Extraction completed: {} success, {} failed", successRecords, failedRecords);

        } catch (Exception e) {
            logEntry.setStatus(ExtractionStatus.FAILED);
            logEntry.setFinishedAt(LocalDateTime.now());
            logContent.append("抽取失败: ").append(e.getMessage());
            logEntry.setLogContent(logContent.toString());
            extractionLogRepository.save(logEntry);
            log.error("Extraction failed", e);
        }

        // Send completion event
        SseEmitter emitter = emitters.get(modelId);
        if (emitter != null) {
            try {
                emitter.send(SseEmitter.event().name("complete")
                        .data("{\"status\":\"" + logEntry.getStatus().name()
                                + "\",\"successRecords\":" + successRecords
                                + ",\"failedRecords\":" + failedRecords + "}"));
                emitter.complete();
            } catch (Exception ignored) {}
        }

        return logToDTO(logEntry);
    }

    private int extractExcelFile(DataModel model, ExtractionRule rule, Material file, StringBuilder logContent) throws Exception {
        if (rule == null) {
            throw new BusinessException(400, "未配置 Excel 抽取规则");
        }

        JsonNode ruleJson = objectMapper.readTree(rule.getRuleContent());
        int sheetIndex = ruleJson.has("sheetIndex") ? ruleJson.get("sheetIndex").asInt(0) : 0;
        int headerRowIndex = ruleJson.has("headerRowIndex") ? ruleJson.get("headerRowIndex").asInt(0) : 0;
        int dataStartRow = ruleJson.has("dataStartRowIndex") ? ruleJson.get("dataStartRowIndex").asInt(1) : 1;
        JsonNode mappings = ruleJson.get("mappings");

        if (mappings == null || !mappings.isArray() || mappings.isEmpty()) {
            throw new BusinessException(400, "抽取规则中没有配置字段映射");
        }

        Resource resource = fileStorageService.load(file.getStoredPath());
        int count = 0;

        try (InputStream is = resource.getInputStream();
             Workbook workbook = new XSSFWorkbook(is)) {

            Sheet sheet = workbook.getSheetAt(sheetIndex);
            Row headerRow = sheet.getRow(headerRowIndex);

            // Build column index map
            Map<String, Integer> headerIndexMap = new HashMap<>();
            if (headerRow != null) {
                for (int i = 0; i < headerRow.getLastCellNum(); i++) {
                    Cell cell = headerRow.getCell(i);
                    if (cell != null) {
                        headerIndexMap.put(cell.getStringCellValue().trim(), i);
                    }
                }
            }

            // Build SQL
            List<String> targetFields = new ArrayList<>();
            List<String> sourceColumns = new ArrayList<>();
            List<JsonNode> formatters = new ArrayList<>();

            for (JsonNode mapping : mappings) {
                sourceColumns.add(mapping.get("sourceColumn").asText());
                targetFields.add(mapping.get("targetField").asText());
                formatters.add(mapping.get("formatter"));
            }

            String insertSql = "INSERT INTO " + model.getTableName()
                    + " (" + String.join(", ", targetFields) + ", created_at, updated_at, source_file)"
                    + " VALUES (" + String.join(", ", Collections.nCopies(targetFields.size(), "?"))
                    + ", datetime('now','localtime'), datetime('now','localtime'), ?)";

            for (int r = dataStartRow; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;

                // Check if row is empty
                boolean isEmpty = true;
                List<Object> values = new ArrayList<>();
                for (int i = 0; i < sourceColumns.size(); i++) {
                    Integer colIdx = headerIndexMap.get(sourceColumns.get(i));
                    Cell cell = colIdx != null ? row.getCell(colIdx) : null;
                    Object value = cell != null ? getCellValue(cell) : "";
                    // Apply formatter
                    JsonNode formatter = formatters.get(i);
                    if (formatter != null && !formatter.isNull()) {
                        value = applyFormatter(value, formatter);
                    }
                    values.add(value);
                    if (value != null && !value.toString().isEmpty()) isEmpty = false;
                }

                if (isEmpty) continue;

                values.add(file.getFileName());
                jdbcTemplate.update(insertSql, values.toArray());
                count++;
            }
        }

        logContent.append("  Excel 文件抽取完成，共 ").append(count).append(" 行\n");
        return count;
    }

    private int extractImageFile(DataModel model, ExtractionRule rule, Material file, StringBuilder logContent) throws Exception {
        if (rule == null) {
            throw new BusinessException(400, "未配置图片抽取规则");
        }

        JsonNode ruleJson = objectMapper.readTree(rule.getRuleContent());
        String systemPrompt = ruleJson.has("systemPrompt") ? ruleJson.get("systemPrompt").asText() : "";
        String userPromptTemplate = ruleJson.has("userPromptTemplate")
                ? ruleJson.get("userPromptTemplate").asText() : "请从以下文本中提取结构化数据，以JSON格式返回：\n{text}";
        JsonNode outputFields = ruleJson.get("outputFields");

        if (outputFields == null || !outputFields.isArray() || outputFields.isEmpty()) {
            throw new BusinessException(400, "抽取规则中没有配置输出字段");
        }

        // OCR the image
        Resource resource = fileStorageService.load(file.getStoredPath());
        Path tempFile = Files.createTempFile("extract_", "_" + file.getFileName());
        Files.copy(resource.getInputStream(), tempFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);

        byte[] fileBytes = Files.readAllBytes(tempFile);
        MultipartFile multipart = new DdlGenerationService.InMemoryMultipartFile(
                file.getFileName(),
                file.getMimeType() != null ? file.getMimeType() : "image/png",
                fileBytes);

        // Get OCR result
        String ocrText = ocrService.processLlm(multipart, null);
        Files.deleteIfExists(tempFile);

        // Build extraction prompt
        String userPrompt = userPromptTemplate.replace("{text}", ocrText);

        // Find agent for this model - we need an agent with credential
        // Use the first available agent with LLM credential
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", systemPrompt));
        messages.add(Map.of("role", "user", "content", userPrompt));

        // We need an agent ID - get from rule or use a default
        // For now, this is handled by passing agentId in the rule
        Long agentId = ruleJson.has("agentId") ? ruleJson.get("agentId").asLong() : null;
        if (agentId == null) {
            throw new BusinessException(400, "图片抽取规则中未配置 AI Agent ID");
        }

        String llmResponse = llmService.callLlm(agentId, messages);

        // Parse JSON response
        JsonNode data = objectMapper.readTree(cleanJsonResponse(llmResponse));
        if (data.isArray()) {
            // Multiple records
            int count = 0;
            for (JsonNode record : data) {
                insertImageRecord(model.getTableName(), outputFields, record, file.getFileName());
                count++;
            }
            return count;
        } else {
            // Single record
            insertImageRecord(model.getTableName(), outputFields, data, file.getFileName());
            return 1;
        }
    }

    private void insertImageRecord(String tableName, JsonNode outputFields, JsonNode record, String sourceFile) {
        List<String> fields = new ArrayList<>();
        List<Object> values = new ArrayList<>();

        for (JsonNode field : outputFields) {
            String fieldName = field.asText();
            fields.add(fieldName);
            String val = record.has(fieldName) ? record.get(fieldName).asText("") : "";
            values.add(val);
        }

        String sql = "INSERT INTO " + tableName
                + " (" + String.join(", ", fields) + ", created_at, updated_at, source_file)"
                + " VALUES (" + String.join(", ", Collections.nCopies(fields.size(), "?"))
                + ", datetime('now','localtime'), datetime('now','localtime'), ?)";
        values.add(sourceFile);
        jdbcTemplate.update(sql, values.toArray());
    }

    public List<Material> getFilesForExtraction(DataModel model, ScopeType scope) {
        List<Material> allFiles = materialRepository.findByLibraryId(model.getLibraryId());
        if (scope == ScopeType.FULL) return allFiles;
        // INCREMENTAL: files uploaded after last extraction
        if (model.getLastExtractedAt() == null) return allFiles;
        return allFiles.stream()
                .filter(m -> m.getUploadedAt() != null && m.getUploadedAt().isAfter(model.getLastExtractedAt()))
                .collect(Collectors.toList());
    }

    // ---- Helpers ----

    private MaterialLibrary getLibrary(DataModel model) {
        return libraryRepository.findById(model.getLibraryId())
                .orElseThrow(() -> new BusinessException(404, "素材库不存在"));
    }

    private Object getCellValue(Cell cell) {
        if (cell == null) return "";
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> {
                double val = cell.getNumericCellValue();
                if (DateUtil.isCellDateFormatted(cell)) {
                    yield new SimpleDateFormat("yyyy-MM-dd").format(cell.getDateCellValue());
                }
                if (val == Math.floor(val) && !Double.isInfinite(val)) {
                    yield String.valueOf((long) val);
                }
                yield String.valueOf(val);
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> cell.getCellFormula();
            default -> "";
        };
    }

    private Object applyFormatter(Object value, JsonNode formatter) {
        if (formatter == null || formatter.isNull()) return value;
        String type = formatter.has("type") ? formatter.get("type").asText() : "";
        String pattern = formatter.has("pattern") ? formatter.get("pattern").asText() : "";

        try {
            return switch (type) {
                case "NUMBER_TO_STRING" -> {
                    if (value != null && !value.toString().isEmpty()) {
                        try {
                            double num = Double.parseDouble(value.toString());
                            yield pattern.isEmpty() ? String.valueOf(num) : new DecimalFormat(pattern).format(num);
                        } catch (NumberFormatException e) {
                            yield value.toString();
                        }
                    }
                    yield value != null ? value.toString() : "";
                }
                case "DATE_FORMAT" -> {
                    // Assume value is already a string date, just pass through
                    yield value != null ? value.toString() : "";
                }
                case "TRIM" -> value != null ? value.toString().trim() : "";
                default -> value;
            };
        } catch (Exception e) {
            log.warn("Formatter error: {}", e.getMessage());
            return value;
        }
    }

    private String cleanJsonResponse(String response) {
        if (response == null) return "{}";
        // Remove markdown code block markers
        response = response.replaceAll("```json\\s*", "").replaceAll("```\\s*", "");
        return response.trim();
    }

    private ExtractionRule toEntity(com.rrs.dto.ExtractionRuleDTO dto) {
        ExtractionRule rule = new ExtractionRule();
        rule.setId(dto.getId());
        rule.setModelId(dto.getModelId());
        rule.setVersion(dto.getVersion());
        rule.setRuleType(ExtractionRuleType.valueOf(dto.getRuleType()));
        rule.setRuleContent(dto.getRuleContent());
        rule.setActive(dto.getActive());
        return rule;
    }

    private ExtractionLogDTO logToDTO(ExtractionLog entity) {
        ExtractionLogDTO dto = new ExtractionLogDTO();
        dto.setId(entity.getId());
        dto.setModelId(entity.getModelId());
        dto.setTriggerType(entity.getTriggerType().name());
        dto.setScopeType(entity.getScopeType().name());
        dto.setStatus(entity.getStatus().name());
        dto.setTotalFiles(entity.getTotalFiles());
        dto.setProcessedFiles(entity.getProcessedFiles());
        dto.setSuccessRecords(entity.getSuccessRecords());
        dto.setFailedRecords(entity.getFailedRecords());
        dto.setLogContent(entity.getLogContent());
        dto.setStartedAt(entity.getStartedAt() != null ? entity.getStartedAt().toString() : null);
        dto.setFinishedAt(entity.getFinishedAt() != null ? entity.getFinishedAt().toString() : null);
        dto.setCreatedAt(entity.getCreatedAt() != null ? entity.getCreatedAt().toString() : null);
        return dto;
    }

    private void sendProgress(Long modelId, String fileName, int processed, int total, int success, int failed, String logMsg) {
        SseEmitter emitter = emitters.get(modelId);
        if (emitter != null) {
            try {
                emitter.send(SseEmitter.event().name("progress")
                        .data("{\"currentFile\":\"" + fileName + "\""
                                + ",\"processedFiles\":" + processed
                                + ",\"totalFiles\":" + total
                                + ",\"successRecords\":" + success
                                + ",\"failedRecords\":" + failed
                                + ",\"log\":\"" + logMsg.replace("\"", "\\\"").replace("\n", "\\n") + "\"}"));
            } catch (Exception ignored) {}
        }
    }
}
