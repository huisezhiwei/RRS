package com.rrs.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.rrs.entity.AiAgent;
import com.rrs.entity.Credential;
import com.rrs.entity.CredentialType;
import com.rrs.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.sourceforge.tess4j.Tesseract;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Base64;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class OcrService {

    private final AiAgentService aiAgentService;
    private final CredentialService credentialService;
    private final ObjectMapper objectMapper;

    @Value("${rrs.tessdata-path:}")
    private String tessdataPath;

    @Value("${rrs.ocr-default-lang:chi_sim+eng}")
    private String defaultLang;

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "jpg", "jpeg", "png", "bmp", "tiff", "tif");

    private static final long MAX_FILE_SIZE = 4 * 1024 * 1024; // 4MB
    private static final int MAX_IMAGE_DIMENSION = 2048;

    private static final OkHttpClient ocrHttpClient = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(180, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build();

    private static final String DEFAULT_OCR_PROMPT =
            "你是一个专业的 OCR 文字识别助手。请仔细识别图片中的所有文字内容，以 Markdown 格式输出，"
                    + "保留原始排版结构（标题、表格、列表等）。如有模糊不清的内容，请用 [模糊] 标记。";

    /**
     * Process image using local Tesseract OCR.
     */
    public String processLocal(MultipartFile file) {
        validateFile(file);
        log.info("Processing local OCR: {}", file.getOriginalFilename());

        File tempFile = null;
        try {
            // Save to temp file
            String ext = getExtension(file.getOriginalFilename());
            tempFile = File.createTempFile("ocr_" + UUID.randomUUID().toString().substring(0, 8), "." + ext);
            file.transferTo(tempFile);

            // Create Tesseract instance (not thread-safe, create new each time)
            Tesseract tesseract = new Tesseract();
            if (tessdataPath != null && !tessdataPath.isBlank()) {
                tesseract.setDatapath(tessdataPath);
            }
            tesseract.setLanguage(defaultLang);
            tesseract.setPageSegMode(3); // Fully automatic page segmentation

            String text = tesseract.doOCR(tempFile);
            log.info("Local OCR completed, text length: {}", text.length());

            return wrapAsMarkdown(file.getOriginalFilename(), text);

        } catch (Exception e) {
            log.error("Local OCR failed", e);
            throw new BusinessException(500, "本地 OCR 识别失败: " + e.getMessage());
        } finally {
            if (tempFile != null && tempFile.exists()) {
                tempFile.delete();
            }
        }
    }

    /**
     * Process image using LLM Vision API.
     */
    public String processLlm(MultipartFile file, Long agentId) {
        validateFile(file);
        if (agentId == null) {
            throw new BusinessException(400, "LLM 模式需要指定 Agent");
        }

        log.info("Processing LLM OCR: file={}, agentId={}", file.getOriginalFilename(), agentId);

        AiAgent agent = aiAgentService.getEntity(agentId);
        if (agent.getCredentialId() == null) {
            throw new BusinessException(400, "Agent 未配置 LLM 凭证");
        }
        if (agent.getModelName() == null || agent.getModelName().isBlank()) {
            throw new BusinessException(400, "Agent 未配置模型");
        }

        Credential credential = credentialService.getEntity(agent.getCredentialId());
        if (credential.getType() != CredentialType.LLM) {
            throw new BusinessException(400, "凭证必须为 LLM 类型");
        }

        Map<String, Object> params = credentialService.getParams(credential);
        String apiUrl = params.get("apiUrl").toString().trim();
        String apiKey = params.get("apiKey").toString().trim();
        if (apiUrl.endsWith("/")) apiUrl = apiUrl.substring(0, apiUrl.length() - 1);
        String chatUrl = apiUrl + "/v1/chat/completions";

        try {
            // Encode image as base64, compress if needed
            byte[] imageBytes = compressIfNeeded(file);
            String base64 = Base64.getEncoder().encodeToString(imageBytes);
            String mimeType = getMimeType(file.getOriginalFilename());

            // Build OpenAI Vision multimodal request (non-streaming)
            ObjectNode requestBody = objectMapper.createObjectNode();
            requestBody.put("model", agent.getModelName());
            requestBody.put("stream", false);
            requestBody.put("max_tokens", agent.getMaxTokens() != null ? agent.getMaxTokens() : 4096);

            ArrayNode messagesNode = objectMapper.createArrayNode();

            // System message
            String systemPrompt = agent.getSystemPrompt() != null && !agent.getSystemPrompt().isBlank()
                    ? agent.getSystemPrompt() : DEFAULT_OCR_PROMPT;
            ObjectNode sysMsg = objectMapper.createObjectNode();
            sysMsg.put("role", "system");
            sysMsg.put("content", systemPrompt);
            messagesNode.add(sysMsg);

            // User message with image
            ObjectNode userMsg = objectMapper.createObjectNode();
            userMsg.put("role", "user");
            ArrayNode contentArray = objectMapper.createArrayNode();

            ObjectNode textPart = objectMapper.createObjectNode();
            textPart.put("type", "text");
            textPart.put("text", "请识别并提取图片中的所有文字内容，以 Markdown 格式输出。");
            contentArray.add(textPart);

            ObjectNode imagePart = objectMapper.createObjectNode();
            imagePart.put("type", "image_url");
            ObjectNode imageUrl = objectMapper.createObjectNode();
            imageUrl.put("url", "data:" + mimeType + ";base64," + base64);
            imagePart.set("image_url", imageUrl);
            contentArray.add(imagePart);

            userMsg.set("content", contentArray);
            messagesNode.add(userMsg);

            requestBody.set("messages", messagesNode);

            String jsonBody = objectMapper.writeValueAsString(requestBody);
            RequestBody body = RequestBody.create(jsonBody, MediaType.parse("application/json"));
            Request request = new Request.Builder()
                    .url(chatUrl)
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .post(body)
                    .build();

            // Execute synchronously (non-streaming)
            try (Response response = ocrHttpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    String errBody = response.body() != null ? response.body().string() : "Unknown error";
                    log.error("LLM Vision API returned {}: {}", response.code(), errBody);
                    throw new BusinessException(500,
                            "LLM Vision API 返回错误 " + response.code()
                                    + "，当前模型可能不支持图片识别，请确认模型支持 Vision 功能");
                }

                ResponseBody responseBody = response.body();
                if (responseBody == null) {
                    throw new BusinessException(500, "LLM API 返回空响应");
                }

                JsonNode json = objectMapper.readTree(responseBody.string());
                JsonNode choices = json.get("choices");
                if (choices == null || !choices.isArray() || choices.isEmpty()) {
                    throw new BusinessException(500, "LLM API 未返回有效结果");
                }

                String content = choices.get(0).get("message").get("content").asText("");
                log.info("LLM OCR completed, content length: {}", content.length());
                return content;
            }

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("LLM OCR failed", e);
            throw new BusinessException(500, "LLM 视觉识别失败: " + e.getMessage());
        }
    }

    // ---- helpers ----

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(400, "请上传图片文件");
        }
        String ext = getExtension(file.getOriginalFilename());
        if (!ALLOWED_EXTENSIONS.contains(ext.toLowerCase())) {
            throw new BusinessException(400, "不支持的文件格式: " + ext + "，支持 jpg/jpeg/png/bmp/tiff");
        }
    }

    private String getExtension(String filename) {
        if (filename == null) return "";
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(dot + 1) : "";
    }

    private String getMimeType(String filename) {
        String ext = getExtension(filename).toLowerCase();
        return switch (ext) {
            case "png" -> "image/png";
            case "bmp" -> "image/bmp";
            case "tiff", "tif" -> "image/tiff";
            default -> "image/jpeg";
        };
    }

    private byte[] compressIfNeeded(MultipartFile file) throws IOException {
        byte[] bytes = file.getBytes();
        if (bytes.length <= MAX_FILE_SIZE) {
            return bytes;
        }
        log.info("Image size {} exceeds {}MB, compressing...", bytes.length, MAX_FILE_SIZE / 1024 / 1024);

        BufferedImage image = ImageIO.read(file.getInputStream());
        if (image == null) {
            return bytes;
        }

        int w = image.getWidth();
        int h = image.getHeight();
        double scale = Math.min((double) MAX_IMAGE_DIMENSION / w, (double) MAX_IMAGE_DIMENSION / h);
        if (scale >= 1.0) {
            return bytes;
        }

        int newW = (int) (w * scale);
        int newH = (int) (h * scale);
        BufferedImage resized = new BufferedImage(newW, newH, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = resized.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(image, 0, 0, newW, newH, null);
        g.dispose();

        File temp = File.createTempFile("ocr_compress_", ".jpg");
        try {
            ImageIO.write(resized, "jpg", temp);
            byte[] compressed = Files.readAllBytes(temp.toPath());
            log.info("Compressed image from {} to {} bytes", bytes.length, compressed.length);
            return compressed;
        } finally {
            temp.delete();
        }
    }

    private String wrapAsMarkdown(String originalFileName, String ocrText) {
        StringBuilder sb = new StringBuilder();
        sb.append("# OCR 识别结果\n\n");
        sb.append("**源文件**: ").append(originalFileName).append("\n\n");
        sb.append("---\n\n");
        String trimmed = ocrText.trim();
        if (trimmed.isEmpty()) {
            sb.append("*未识别到文字内容*\n");
        } else {
            sb.append(trimmed).append("\n");
        }
        return sb.toString();
    }
}
