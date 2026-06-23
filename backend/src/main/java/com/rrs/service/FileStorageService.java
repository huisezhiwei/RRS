package com.rrs.service;

import com.rrs.entity.LibraryType;
import com.rrs.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PostConstruct;
import java.io.*;
import java.net.MalformedURLException;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
@Slf4j
public class FileStorageService {

    @Value("${rrs.upload-dir:../data-file/uploads}")
    private String uploadDir;

    private Path rootLocation;

    private static final DateTimeFormatter FILE_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    @PostConstruct
    public void init() {
        rootLocation = Paths.get(uploadDir).toAbsolutePath().normalize();
        log.info("文件存储根目录: {}", rootLocation);
        try {
            Files.createDirectories(rootLocation.resolve("excel"));
            Files.createDirectories(rootLocation.resolve("image"));
        } catch (IOException e) {
            throw new RuntimeException("无法初始化文件存储目录", e);
        }
    }

    public StoredFile store(MultipartFile file, String libraryName, LibraryType libraryType) {
        try {
            String timestamp = LocalDateTime.now().format(FILE_DATE_FORMAT);
            String originalFilename = file.getOriginalFilename();
            String extension = getFileExtension(originalFilename);

            log.info("开始存储文件: {}, 素材库: {}, 类型: {}", originalFilename, libraryName, libraryType);

            // Determine subdirectory
            String subDir = libraryType == LibraryType.EXCEL ? "excel" : "image";
            Path targetDir = rootLocation.resolve(subDir).resolve(libraryName);
            Files.createDirectories(targetDir);

            // Generate stored filename
            String storedFileName = libraryName + "-" + timestamp;
            String storedExtension = extension;
            Path targetPath;

            if (libraryType == LibraryType.EXCEL) {
                // Excel files are always converted to xlsx
                storedExtension = "xlsx";
                targetPath = getUniquePath(targetDir, storedFileName, storedExtension);

                if (extension.equalsIgnoreCase("csv")) {
                    convertCsvToXlsx(file.getInputStream(), targetPath);
                } else if (extension.equalsIgnoreCase("xls")) {
                    convertXlsToXlsx(file.getInputStream(), targetPath);
                } else {
                    // xlsx - copy directly
                    Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);
                }
            } else {
                // Image files - store directly
                targetPath = getUniquePath(targetDir, storedFileName, storedExtension);
                Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);
            }

            // Use forward slashes consistently for stored path (cross-platform)
            String relativePath = subDir + "/" + libraryName + "/" + targetPath.getFileName().toString();

            log.info("文件已存储: {}", relativePath);

            return new StoredFile(
                    targetPath.getFileName().toString(),
                    relativePath,
                    file.getSize(),
                    file.getContentType()
            );

        } catch (IOException e) {
            log.error("文件存储失败", e);
            throw new BusinessException("文件存储失败: " + e.getMessage());
        }
    }

    public Resource load(String storedPath) {
        try {
            Path file = rootLocation.resolve(storedPath).normalize();
            log.debug("加载文件: {}, 解析路径: {}", storedPath, file);

            Resource resource = new UrlResource(file.toUri());
            if (resource.exists() && resource.isReadable()) {
                return resource;
            }
            log.error("文件不存在或不可读: {}", file);
            throw new BusinessException(404, "文件不存在");
        } catch (MalformedURLException e) {
            log.error("文件路径无效: {}", storedPath, e);
            throw new BusinessException("文件路径无效");
        }
    }

    public void delete(String storedPath) {
        try {
            Path file = rootLocation.resolve(storedPath).normalize();
            log.info("删除文件: {}, 解析路径: {}", storedPath, file);

            boolean deleted = Files.deleteIfExists(file);
            if (deleted) {
                log.info("文件已删除: {}", file);
            } else {
                log.warn("文件不存在，无法删除: {}", file);
            }
        } catch (IOException e) {
            log.error("文件删除失败: {}", storedPath, e);
            // 在 Windows 上可能因文件锁导致删除失败，尝试强制删除
            try {
                Path file = rootLocation.resolve(storedPath).normalize();
                if (Files.exists(file)) {
                    file.toFile().delete();
                }
            } catch (Exception retryEx) {
                log.error("重试删除也失败: {}", storedPath, retryEx);
            }
        }
    }

    public void deleteLibraryFiles(String libraryName, LibraryType libraryType) {
        try {
            String subDir = libraryType == LibraryType.EXCEL ? "excel" : "image";
            Path libraryDir = rootLocation.resolve(subDir).resolve(libraryName);
            log.info("删除素材库文件目录: {}", libraryDir);

            if (Files.exists(libraryDir)) {
                try (var stream = Files.walk(libraryDir)) {
                    stream.sorted(java.util.Comparator.reverseOrder())
                            .map(Path::toFile)
                            .forEach(f -> {
                                if (!f.delete()) {
                                    log.warn("无法删除: {}", f);
                                }
                            });
                }
            }
        } catch (IOException e) {
            log.warn("素材库文件目录删除失败: {}", libraryName, e);
        }
    }

    private Path getUniquePath(Path dir, String baseName, String extension) {
        Path path = dir.resolve(baseName + "." + extension);
        int counter = 1;
        while (Files.exists(path)) {
            path = dir.resolve(baseName + "-" + counter + "." + extension);
            counter++;
        }
        return path;
    }

    private String getFileExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "bin";
        }
        return filename.substring(filename.lastIndexOf(".") + 1).toLowerCase();
    }

    private void convertCsvToXlsx(InputStream csvStream, Path outputPath) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(csvStream, "UTF-8"));
             Workbook workbook = new XSSFWorkbook()) {

            Sheet sheet = workbook.createSheet("Sheet1");
            String line;
            int rowNum = 0;

            while ((line = reader.readLine()) != null) {
                Row row = sheet.createRow(rowNum++);
                String[] cells = parseCsvLine(line);
                for (int i = 0; i < cells.length; i++) {
                    Cell cell = row.createCell(i);
                    cell.setCellValue(cells[i]);
                }
            }

            try (OutputStream os = Files.newOutputStream(outputPath)) {
                workbook.write(os);
            }
        }
    }

    private void convertXlsToXlsx(InputStream xlsStream, Path outputPath) throws IOException {
        try (Workbook xlsWorkbook = new HSSFWorkbook(xlsStream);
             Workbook xlsxWorkbook = new XSSFWorkbook()) {

            for (int i = 0; i < xlsWorkbook.getNumberOfSheets(); i++) {
                Sheet sourceSheet = xlsWorkbook.getSheetAt(i);
                Sheet targetSheet = xlsxWorkbook.createSheet(sourceSheet.getSheetName());

                for (int rowNum = 0; rowNum <= sourceSheet.getLastRowNum(); rowNum++) {
                    Row sourceRow = sourceSheet.getRow(rowNum);
                    if (sourceRow == null) continue;

                    Row targetRow = targetSheet.createRow(rowNum);
                    for (int cellNum = 0; cellNum < sourceRow.getLastCellNum(); cellNum++) {
                        Cell sourceCell = sourceRow.getCell(cellNum);
                        if (sourceCell == null) continue;

                        Cell targetCell = targetRow.createCell(cellNum);
                        copyCellValue(sourceCell, targetCell);
                    }
                }
            }

            try (OutputStream os = Files.newOutputStream(outputPath)) {
                xlsxWorkbook.write(os);
            }
        }
    }

    private void copyCellValue(Cell source, Cell target) {
        switch (source.getCellType()) {
            case STRING:
                target.setCellValue(source.getStringCellValue());
                break;
            case NUMERIC:
                target.setCellValue(source.getNumericCellValue());
                break;
            case BOOLEAN:
                target.setCellValue(source.getBooleanCellValue());
                break;
            case FORMULA:
                target.setCellValue(source.getCellFormula());
                break;
            case BLANK:
                target.setBlank();
                break;
            default:
                break;
        }
    }

    private String[] parseCsvLine(String line) {
        java.util.List<String> result = new java.util.ArrayList<>();
        boolean inQuotes = false;
        StringBuilder current = new StringBuilder();

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                result.add(current.toString());
                current = new StringBuilder();
            } else {
                current.append(c);
            }
        }
        result.add(current.toString());
        return result.toArray(new String[0]);
    }

    public record StoredFile(String storedName, String storedPath, long fileSize, String mimeType) {}
}

