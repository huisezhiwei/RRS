package com.rrs.service;

import com.rrs.dto.*;
import com.rrs.entity.*;
import com.rrs.exception.BusinessException;
import com.rrs.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DataModelService {

    private final DataModelRepository dataModelRepository;
    private final ExtractionRuleRepository extractionRuleRepository;
    private final ExtractionLogRepository extractionLogRepository;
    private final DataModelScheduleRepository scheduleRepository;
    private final MaterialLibraryRepository libraryRepository;
    private final JdbcTemplate jdbcTemplate;

    private static final Pattern CODE_PATTERN = Pattern.compile("^[a-zA-Z][a-zA-Z0-9_]*$");
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final Set<String> RESERVED_COLUMNS = Set.of("_id", "created_at", "updated_at", "source_file");

    // ---- CRUD ----

    @Transactional
    public DataModelDTO create(DataModelCreateDTO dto) {
        if (dto.getCode() == null || !CODE_PATTERN.matcher(dto.getCode()).matches()) {
            throw new BusinessException(400, "编码格式无效，需以字母开头，仅包含字母、数字和下划线");
        }
        if (dataModelRepository.findByCode(dto.getCode()).isPresent()) {
            throw new BusinessException(400, "数据模型编码已存在: " + dto.getCode());
        }

        DataModel model = new DataModel();
        model.setCode(dto.getCode());
        model.setName(dto.getName());
        model.setDescription(dto.getDescription());
        model.setMaintainer(dto.getMaintainer());
        model.setTableName("dm_" + dto.getCode());
        model.setStatus(DataModelStatus.UNINITIALIZED);

        model = dataModelRepository.save(model);
        log.info("Created data model: code={}, id={}", dto.getCode(), model.getId());
        return toDTO(model);
    }

    public List<DataModelDTO> list(String keyword) {
        List<DataModel> models = dataModelRepository.findAll();
        if (keyword != null && !keyword.isEmpty()) {
            String kw = keyword.toLowerCase();
            models = models.stream()
                    .filter(m -> m.getName().toLowerCase().contains(kw)
                            || m.getCode().toLowerCase().contains(kw)
                            || (m.getDescription() != null && m.getDescription().toLowerCase().contains(kw)))
                    .collect(Collectors.toList());
        }
        return models.stream().map(this::toDTO).collect(Collectors.toList());
    }

    public DataModelDTO getById(Long id) {
        return toDTO(getEntity(id));
    }

    public DataModel getEntity(Long id) {
        return dataModelRepository.findById(id)
                .orElseThrow(() -> new BusinessException(404, "数据模型不存在"));
    }

    @Transactional
    public void delete(Long id) {
        DataModel model = getEntity(id);
        log.info("Deleting data model: id={}, code={}", id, model.getCode());

        // Drop dynamic table if exists
        if (model.getTableName() != null) {
            try {
                jdbcTemplate.execute("DROP TABLE IF EXISTS " + model.getTableName());
                log.info("Dropped dynamic table: {}", model.getTableName());
            } catch (Exception e) {
                log.warn("Failed to drop table {}: {}", model.getTableName(), e.getMessage());
            }
        }

        // Delete related records
        extractionRuleRepository.findByModelIdOrderByVersionDesc(id)
                .forEach(r -> extractionRuleRepository.delete(r));
        scheduleRepository.findByModelId(id).ifPresent(s -> scheduleRepository.delete(s));

        dataModelRepository.delete(model);
    }

    // ---- DDL Operations ----

    @Transactional
    public void confirmDdl(Long id, DdlConfirmRequest request) {
        DataModel model = getEntity(id);
        String tableName = model.getTableName();

        try {
            executeCreateTable(tableName, request.getDdl(), request.getPrimaryKey());
            model.setDdl(request.getDdl());
            model.setPrimaryKey(request.getPrimaryKey());
            model.setStatus(DataModelStatus.READY);
            dataModelRepository.save(model);
            log.info("DDL confirmed and table created: {}", tableName);
        } catch (Exception e) {
            model.setStatus(DataModelStatus.ERROR);
            dataModelRepository.save(model);
            throw new BusinessException(500, "建表失败，请检查 DDL 语句: " + e.getMessage());
        }
    }

    public void executeCreateTable(String tableName, String userDdl, String primaryKey) {
        // Parse user DDL to extract column definitions
        String columnDefs = extractColumnDefinitions(userDdl);

        // Build final DDL with system columns
        StringBuilder sb = new StringBuilder();
        sb.append("CREATE TABLE IF NOT EXISTS ").append(tableName).append(" (\n");

        // Add _id as primary key if no primary key specified in user DDL
        boolean hasPkInDdl = userDdl.toUpperCase().contains("PRIMARY KEY");
        if (!hasPkInDdl) {
            sb.append("  _id INTEGER PRIMARY KEY AUTOINCREMENT,\n");
        }

        sb.append(columnDefs);
        if (!columnDefs.trim().endsWith(",")) sb.append(",");
        sb.append("\n  created_at TEXT DEFAULT (datetime('now','localtime'))");
        sb.append(",\n  updated_at TEXT DEFAULT (datetime('now','localtime'))");
        sb.append(",\n  source_file TEXT");
        sb.append("\n)");

        String finalDdl = sb.toString();
        log.info("Executing DDL:\n{}", finalDdl);
        jdbcTemplate.execute(finalDdl);
    }

    private String extractColumnDefinitions(String ddl) {
        // Extract content between first ( and last )
        int start = ddl.indexOf('(');
        int end = ddl.lastIndexOf(')');
        if (start < 0 || end < 0 || end <= start) {
            throw new BusinessException(400, "DDL 格式无效，缺少列定义");
        }
        return ddl.substring(start + 1, end).trim();
    }

    @Transactional
    public void modifyDdl(Long id, String newDdl, String primaryKey) {
        DataModel model = getEntity(id);
        String tableName = model.getTableName();
        String backupName = tableName + "_bak_" + LocalDateTime.now().format(DATE_FORMAT);

        try {
            // Backup old table
            jdbcTemplate.execute("ALTER TABLE " + tableName + " RENAME TO " + backupName);
            log.info("Backed up table {} to {}", tableName, backupName);

            // Create new table
            executeCreateTable(tableName, newDdl, primaryKey);

            model.setDdl(newDdl);
            model.setPrimaryKey(primaryKey);
            dataModelRepository.save(model);
            log.info("DDL modified for model: {}", model.getCode());
        } catch (Exception e) {
            throw new BusinessException(500, "DDL 修改失败: " + e.getMessage());
        }
    }

    // ---- Dynamic Table Query ----

    public DynamicTablePageDTO queryDynamicTable(Long id, int page, int size, String keyword) {
        DataModel model = getEntity(id);
        String tableName = model.getTableName();

        if (model.getStatus() != DataModelStatus.READY) {
            return new DynamicTablePageDTO(Collections.emptyList(), Collections.emptyList(), 0L);
        }

        List<String> columns = getColumnNames(tableName);
        // Filter out system columns for display
        List<String> displayColumns = columns.stream()
                .filter(c -> !RESERVED_COLUMNS.contains(c))
                .collect(Collectors.toList());

        String countSql = "SELECT COUNT(*) FROM " + tableName;
        String querySql = "SELECT * FROM " + tableName;

        List<Object> params = new ArrayList<>();

        if (keyword != null && !keyword.isEmpty()) {
            String where = displayColumns.stream()
                    .map(c -> c + " LIKE ?")
                    .collect(Collectors.joining(" OR "));
            if (!where.isEmpty()) {
                countSql += " WHERE " + where;
                querySql += " WHERE " + where;
                for (int i = 0; i < displayColumns.size(); i++) {
                    params.add("%" + keyword + "%");
                }
            }
        }

        Long total = jdbcTemplate.queryForObject(countSql, Long.class, params.toArray());
        if (total == null) total = 0L;

        querySql += " ORDER BY _id DESC LIMIT ? OFFSET ?";
        List<Object> queryParams = new ArrayList<>(params);
        queryParams.add(size);
        queryParams.add(page * size);

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(querySql, queryParams.toArray());

        return new DynamicTablePageDTO(displayColumns, rows, total);
    }

    @Transactional
    public void updateDynamicRow(Long id, Long rowId, Map<String, Object> data) {
        DataModel model = getEntity(id);
        String tableName = model.getTableName();
        List<String> validColumns = getColumnNames(tableName);

        if (data == null || data.isEmpty()) {
            throw new BusinessException(400, "更新数据不能为空");
        }

        // Validate and filter columns
        List<String> setClauses = new ArrayList<>();
        List<Object> params = new ArrayList<>();

        for (Map.Entry<String, Object> entry : data.entrySet()) {
            String col = entry.getKey();
            if (!validColumns.contains(col) || RESERVED_COLUMNS.contains(col)) {
                continue; // Skip invalid/system columns
            }
            setClauses.add(col + " = ?");
            params.add(entry.getValue());
        }

        if (setClauses.isEmpty()) {
            throw new BusinessException(400, "没有可更新的字段");
        }

        // Always update updated_at
        setClauses.add("updated_at = datetime('now','localtime')");

        String sql = "UPDATE " + tableName + " SET " + String.join(", ", setClauses) + " WHERE _id = ?";
        params.add(rowId);

        int updated = jdbcTemplate.update(sql, params.toArray());
        if (updated == 0) {
            throw new BusinessException(404, "未找到指定记录");
        }
        log.info("Updated row {} in table {}", rowId, tableName);
    }

    public List<String> getColumnNames(String tableName) {
        try {
            List<Map<String, Object>> columns = jdbcTemplate.queryForList(
                    "PRAGMA table_info(" + tableName + ")");
            return columns.stream()
                    .map(c -> c.get("name").toString())
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("Failed to get columns for table {}: {}", tableName, e.getMessage());
            return Collections.emptyList();
        }
    }

    // ---- Extraction Rules ----

    @Transactional
    public ExtractionRuleDTO saveRule(Long modelId, ExtractionRuleSaveRequest request) {
        getEntity(modelId); // validate exists

        // Deactivate current active version
        extractionRuleRepository.findByModelIdAndActiveTrue(modelId)
                .ifPresent(rule -> {
                    rule.setActive(false);
                    extractionRuleRepository.save(rule);
                });

        // Calculate new version
        List<ExtractionRule> existing = extractionRuleRepository.findByModelIdOrderByVersionDesc(modelId);
        int newVersion = existing.isEmpty() ? 1 : existing.get(0).getVersion() + 1;

        // Delete oldest if more than 5 versions
        if (existing.size() >= 5) {
            ExtractionRule oldest = existing.get(existing.size() - 1);
            extractionRuleRepository.delete(oldest);
            log.info("Deleted oldest rule version {} for model {}", oldest.getVersion(), modelId);
        }

        ExtractionRule rule = new ExtractionRule();
        rule.setModelId(modelId);
        rule.setVersion(newVersion);
        rule.setRuleType(ExtractionRuleType.valueOf(request.getRuleType()));
        rule.setRuleContent(request.getRuleContent());
        rule.setActive(true);

        rule = extractionRuleRepository.save(rule);
        log.info("Saved extraction rule v{} for model {}", newVersion, modelId);
        return ruleToDTO(rule);
    }

    public ExtractionRuleDTO getActiveRule(Long modelId) {
        return extractionRuleRepository.findByModelIdAndActiveTrue(modelId)
                .map(this::ruleToDTO)
                .orElse(null);
    }

    // ---- Extraction Logs ----

    public List<ExtractionLogDTO> getExtractionLogs(Long modelId, int page, int size) {
        return extractionLogRepository
                .findByModelIdOrderByCreatedAtDesc(modelId, org.springframework.data.domain.PageRequest.of(page, size))
                .getContent().stream()
                .map(this::logToDTO)
                .collect(Collectors.toList());
    }

    // ---- Helpers ----

    private DataModelDTO toDTO(DataModel entity) {
        DataModelDTO dto = new DataModelDTO();
        dto.setId(entity.getId());
        dto.setCode(entity.getCode());
        dto.setName(entity.getName());
        dto.setDescription(entity.getDescription());
        dto.setMaintainer(entity.getMaintainer());
        dto.setLibraryId(entity.getLibraryId());
        dto.setStatus(entity.getStatus().name());
        dto.setTableName(entity.getTableName());
        dto.setDdl(entity.getDdl());
        dto.setPrimaryKey(entity.getPrimaryKey());
        dto.setLastExtractedAt(entity.getLastExtractedAt() != null ? entity.getLastExtractedAt().toString() : null);
        dto.setCreatedAt(entity.getCreatedAt() != null ? entity.getCreatedAt().toString() : null);
        dto.setUpdatedAt(entity.getUpdatedAt() != null ? entity.getUpdatedAt().toString() : null);

        // Resolve library name
        if (entity.getLibraryId() != null) {
            libraryRepository.findById(entity.getLibraryId())
                    .ifPresent(lib -> dto.setLibraryName(lib.getName()));
        }

        // Count data rows
        if (entity.getStatus() == DataModelStatus.READY && entity.getTableName() != null) {
            try {
                Long count = jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM " + entity.getTableName(), Long.class);
                dto.setDataCount(count != null ? count : 0L);
            } catch (Exception e) {
                dto.setDataCount(0L);
            }
        }

        return dto;
    }

    private ExtractionRuleDTO ruleToDTO(ExtractionRule entity) {
        ExtractionRuleDTO dto = new ExtractionRuleDTO();
        dto.setId(entity.getId());
        dto.setModelId(entity.getModelId());
        dto.setVersion(entity.getVersion());
        dto.setRuleType(entity.getRuleType().name());
        dto.setRuleContent(entity.getRuleContent());
        dto.setActive(entity.getActive());
        dto.setCreatedAt(entity.getCreatedAt() != null ? entity.getCreatedAt().toString() : null);
        return dto;
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
}
