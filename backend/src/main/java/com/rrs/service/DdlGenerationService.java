package com.rrs.service;

import com.rrs.entity.DataModel;
import com.rrs.entity.LibraryType;
import com.rrs.entity.Material;
import com.rrs.entity.MaterialLibrary;
import com.rrs.exception.BusinessException;
import com.rrs.repository.MaterialLibraryRepository;
import com.rrs.repository.MaterialRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DdlGenerationService {

    private final LlmService llmService;
    private final OcrService ocrService;
    private final DataModelService dataModelService;
    private final MaterialRepository materialRepository;
    private final MaterialLibraryRepository libraryRepository;
    private final FileStorageService fileStorageService;

    private static final String EXCEL_SYSTEM_PROMPT = """
            你是一个数据库建模专家。根据提供的 Excel 文件表头和示例数据，生成 SQLite CREATE TABLE DDL。

            规则：
            1. 表名使用 {TABLE_NAME} 占位符
            2. 所有字段类型只用 SQLite 支持的类型：TEXT, INTEGER, REAL, BLOB
            3. 金额/数量/价格类用 REAL，整数ID/序号用 INTEGER，日期/时间用 TEXT，其余默认 TEXT
            4. 字段名使用英文 snake_case 命名风格
            5. 每个字段后面用 -- 注释说明中文含义
            6. 不要包含 PRIMARY KEY 定义（系统会自动添加）
            7. 不要包含 created_at, updated_at, source_file 字段（系统会自动添加）
            8. 只输出 DDL 语句本身，不要输出任何解释文字或 markdown 代码块标记""";

    private static final String IMAGE_SYSTEM_PROMPT = """
            你是一个数据库建模专家。根据提供的图片 OCR 识别结果，分析数据共性，生成 SQLite CREATE TABLE DDL。

            规则：
            1. 表名使用 {TABLE_NAME} 占位符
            2. 所有字段类型只用 SQLite 支持的类型：TEXT, INTEGER, REAL, BLOB
            3. 金额/数量/价格类用 REAL，整数ID/序号用 INTEGER，日期/时间用 TEXT，其余默认 TEXT
            4. 字段名使用英文 snake_case 命名风格
            5. 每个字段后面用 -- 注释说明中文含义
            6. 不要包含 PRIMARY KEY 定义（系统会自动添加）
            7. 不要包含 created_at, updated_at, source_file 字段（系统会自动添加）
            8. 只输出 DDL 语句本身，不要输出任何解释文字或 markdown 代码块标记
            9. 只提取各图片中共同出现的结构化字段""";

    public String generate(Long modelId, Long agentId, List<Long> fileIds) {
        var model = dataModelService.getEntity(modelId);
        if (model.getLibraryId() == null) {
            throw new BusinessException(400, "数据模型尚未绑定素材库");
        }

        MaterialLibrary library = libraryRepository.findById(model.getLibraryId())
                .orElseThrow(() -> new BusinessException(404, "素材库不存在"));

        // Get files: use specified IDs or latest 5
        List<Material> files;
        if (fileIds != null && !fileIds.isEmpty()) {
            files = materialRepository.findAllById(fileIds);
        } else {
            List<Material> allFiles = materialRepository.findByLibraryId(model.getLibraryId());
            files = allFiles.stream()
                    .sorted(Comparator.comparing(Material::getUploadedAt).reversed())
                    .limit(5)
                    .collect(Collectors.toList());
        }

        if (files.isEmpty()) {
            throw new BusinessException(400, "素材库中没有可用的文件");
        }

        log.info("Generating DDL for model {} using {} files", model.getCode(), files.size());

        String ddl;
        if (library.getLibraryType() == LibraryType.EXCEL) {
            ddl = generateFromExcel(agentId, model, files);
        } else {
            ddl = generateFromImage(agentId, model, files);
        }

        // Clean up DDL: remove markdown code block markers
        ddl = cleanDdl(ddl);
        return ddl;
    }

    private String generateFromExcel(Long agentId, DataModel model, List<Material> files) {
        StringBuilder userContent = new StringBuilder();
        userContent.append("以下是最近 ").append(files.size()).append(" 个 Excel 文件的表头和示例数据：\n\n");

        int fileIndex = 1;
        for (Material material : files) {
            try {
                Resource resource = fileStorageService.load(material.getStoredPath());
                try (InputStream is = resource.getInputStream();
                     Workbook workbook = new XSSFWorkbook(is)) {

                    Sheet sheet = workbook.getSheetAt(0);
                    userContent.append("--- 文件 ").append(fileIndex).append(": ")
                            .append(material.getFileName()).append(" ---\n");

                    // Read header row
                    Row headerRow = sheet.getRow(0);
                    if (headerRow != null) {
                        List<String> headers = new ArrayList<>();
                        for (int i = 0; i < headerRow.getLastCellNum(); i++) {
                            Cell cell = headerRow.getCell(i);
                            headers.add(cell != null ? getCellString(cell) : "");
                        }
                        userContent.append("表头: ").append(String.join(" | ", headers)).append("\n");
                    }

                    // Read first 3 data rows
                    for (int r = 1; r <= Math.min(3, sheet.getLastRowNum()); r++) {
                        Row row = sheet.getRow(r);
                        if (row == null) continue;
                        List<String> values = new ArrayList<>();
                        for (int i = 0; i < (headerRow != null ? headerRow.getLastCellNum() : 0); i++) {
                            Cell cell = row.getCell(i);
                            values.add(cell != null ? getCellString(cell) : "");
                        }
                        userContent.append("行").append(r).append(": ")
                                .append(String.join(" | ", values)).append("\n");
                    }
                    userContent.append("\n");
                }
                fileIndex++;
            } catch (Exception e) {
                log.warn("Failed to read Excel file {}: {}", material.getFileName(), e.getMessage());
                userContent.append("--- 文件 ").append(fileIndex).append(": ")
                        .append(material.getFileName()).append(" (读取失败) ---\n\n");
                fileIndex++;
            }
        }

        userContent.append("请根据以上所有文件的共性，生成统一的 CREATE TABLE DDL。");

        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", EXCEL_SYSTEM_PROMPT));
        messages.add(Map.of("role", "user", "content", userContent.toString()));

        return llmService.callLlm(agentId, messages);
    }

    private String generateFromImage(Long agentId, DataModel model, List<Material> files) {
        StringBuilder userContent = new StringBuilder();
        userContent.append("以下是最近 ").append(files.size()).append(" 个图片文件的 OCR 识别结果：\n\n");

        int fileIndex = 1;
        for (Material material : files) {
            try {
                // Load file as MultipartFile for OCR
                Resource resource = fileStorageService.load(material.getStoredPath());
                Path tempFile = Files.createTempFile("ocr_gen_", "_" + material.getFileName());
                Files.copy(resource.getInputStream(), tempFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);

                // Create a simple MultipartFile adapter
                byte[] fileBytes = Files.readAllBytes(tempFile);
                MultipartFile multipart = new InMemoryMultipartFile(
                        material.getFileName(),
                        material.getMimeType() != null ? material.getMimeType() : "image/png",
                        fileBytes);

                String ocrResult = ocrService.processLlm(multipart, agentId);
                userContent.append("--- 图片 ").append(fileIndex).append(": ")
                        .append(material.getFileName()).append(" ---\n");
                userContent.append(ocrResult).append("\n\n");

                Files.deleteIfExists(tempFile);
                fileIndex++;
            } catch (Exception e) {
                log.warn("Failed to OCR image {}: {}", material.getFileName(), e.getMessage());
                userContent.append("--- 图片 ").append(fileIndex).append(": ")
                        .append(material.getFileName()).append(" (OCR失败) ---\n\n");
                fileIndex++;
            }
        }

        userContent.append("请分析这些图片中的结构化数据共性，提取所有可识别的字段，生成统一的 CREATE TABLE DDL。");

        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", IMAGE_SYSTEM_PROMPT));
        messages.add(Map.of("role", "user", "content", userContent.toString()));

        return llmService.callLlm(agentId, messages);
    }

    private String cleanDdl(String ddl) {
        if (ddl == null) return "";
        // Remove markdown code block markers
        ddl = ddl.replaceAll("```sql\\s*", "").replaceAll("```\\s*", "");
        ddl = ddl.trim();
        return ddl;
    }

    /**
     * Simple in-memory MultipartFile implementation to avoid spring-test dependency.
     */
    static class InMemoryMultipartFile implements MultipartFile {
        private final String name;
        private final String contentType;
        private final byte[] content;

        InMemoryMultipartFile(String name, String contentType, byte[] content) {
            this.name = name;
            this.contentType = contentType;
            this.content = content;
        }

        @Override public String getName() { return name; }
        @Override public String getOriginalFilename() { return name; }
        @Override public String getContentType() { return contentType; }
        @Override public boolean isEmpty() { return content == null || content.length == 0; }
        @Override public long getSize() { return content.length; }
        @Override public byte[] getBytes() { return content; }
        @Override public InputStream getInputStream() { return new java.io.ByteArrayInputStream(content); }
        @Override public void transferTo(File dest) throws java.io.IOException {
            Files.write(dest.toPath(), content);
        }
        @Override public Resource getResource() {
            return new org.springframework.core.io.ByteArrayResource(content) {
                @Override public String getFilename() { return name; }
            };
        }
    }

    private String getCellString(Cell cell) {
        if (cell == null) return "";
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> {
                double val = cell.getNumericCellValue();
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
}
