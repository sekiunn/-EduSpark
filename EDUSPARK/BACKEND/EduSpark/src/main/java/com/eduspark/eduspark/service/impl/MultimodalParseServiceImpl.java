package com.eduspark.eduspark.service.impl;

import com.eduspark.eduspark.service.IMultimodalParseService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xslf.extractor.XSLFExtractor;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * 多模态内容解析服务实现
 * 支持 PPT 文件文本提取、图片视觉分析（通过 Dashscope qwen-vl）
 */
@Slf4j
@Service
public class MultimodalParseServiceImpl implements IMultimodalParseService {

    @Value("${lesson-plan.writer.external.base-url:https://dashscope.aliyuncs.com/compatible-mode/v1}")
    private String baseUrl;

    @Value("${lesson-plan.writer.external.api-key:}")
    private String apiKey;

    @Value("${multimodal.vision.model:qwen-vl-max}")
    private String visionModel;

    @Value("${multimodal.vision.prompt:请详细描述这张图片的内容，如果是教学相关的图片请提取其中的关键文字和知识点。}")
    private String defaultPrompt;

    @Value("${multimodal.connect-timeout:10000}")
    private int connectTimeout;

    @Value("${multimodal.read-timeout:60000}")
    private int readTimeout;

    private final ObjectMapper objectMapper;

    public MultimodalParseServiceImpl(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String extractTextFromPpt(byte[] fileData, String fileName) {
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".pptx")) {
            return extractPptx(fileData);
        } else {
            throw new RuntimeException("不支持的PPT格式: " + fileName + "，仅支持.pptx格式");
        }
    }

    private String extractPptx(byte[] fileData) {
        log.info("开始解析 PPTX 文件, 大小={}", fileData.length);

        try (ByteArrayInputStream bais = new ByteArrayInputStream(fileData);
             XMLSlideShow pptx = new XMLSlideShow(bais)) {

            XSLFExtractor extractor = new XSLFExtractor(pptx);
            String text = extractor.getText();
            String result = cleanPptText(text);

            log.info("PPTX 解析完成，提取文本长度={}", result.length());
            return result.isEmpty() ? "" : result;

        } catch (Exception e) {
            log.error("PPTX 解析失败: {}", e.getMessage());
            throw new RuntimeException("PPTX 文件解析失败: " + e.getMessage(), e);
        }
    }

    private String cleanPptText(String text) {
        if (text == null) return "";
        return text.replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]", "")
                   .replaceAll("[ \\t]+", " ")
                   .replaceAll("\\n{3,}", "\n\n")
                   .trim();
    }

    @Override
    public String describeImage(byte[] imageData, String prompt) {
        String actualPrompt = (prompt != null && !prompt.isBlank())
                ? prompt
                : defaultPrompt;

        log.info("开始图片视觉分析: dataSize={}, model={}", imageData.length, visionModel);

        try {
            String base64Image = java.util.Base64.getEncoder().encodeToString(imageData);
            String dataUrl = "data:image/jpeg;base64," + base64Image;

            String endpoint = resolveEndpoint();

            Map<String, Object> body = Map.of(
                    "model", visionModel,
                    "stream", false,
                    "messages", List.of(
                            Map.of("role", "user", "content", List.of(
                                    Map.of("type", "image_url",
                                            "image_url", Map.of("url", dataUrl)),
                                    Map.of("type", "text", "text", actualPrompt)
                            ))
                    )
            );

            String jsonBody = objectMapper.writeValueAsString(body);
            HttpURLConnection conn = (HttpURLConnection) new URL(endpoint).openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setConnectTimeout(connectTimeout);
            conn.setReadTimeout(readTimeout);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Authorization", "Bearer " + apiKey);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(jsonBody.getBytes(StandardCharsets.UTF_8));
            }

            int responseCode = conn.getResponseCode();
            if (responseCode < 200 || responseCode >= 300) {
                String errorBody = readStream(conn.getErrorStream());
                log.error("视觉模型 API 调用失败: code={}, body={}", responseCode, errorBody);
                throw new RuntimeException("图片分析服务不可用: HTTP " + responseCode);
            }

            String responseBody = readStream(conn.getInputStream());
            String description = extractContent(responseBody);

            log.info("图片视觉分析成功, 结果长度={}", description.length());
            return description;

        } catch (Exception e) {
            if (e instanceof RuntimeException) throw (RuntimeException) e;
            log.error("视觉模型 API 调用失败: {}", e.getMessage());
            throw new RuntimeException("图片分析服务不可用: " + e.getMessage(), e);
        }
    }

    @Override
    public String extractTextFromImage(byte[] imageData) {
        return describeImage(imageData,
            "请识别图片中的所有文字内容，保留原有格式，按顺序输出图片中的文字。");
    }

    @Override
    public boolean isAvailable() {
        return apiKey != null && !apiKey.isBlank();
    }

    private String resolveEndpoint() {
        String normalizedBase = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        if (normalizedBase.endsWith("/chat/completions")) {
            return normalizedBase;
        }
        return normalizedBase + "/chat/completions";
    }

    private String extractContent(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode choices = root.path("choices");
            if (!choices.isArray() || choices.isEmpty()) {
                return "";
            }
            JsonNode message = choices.get(0).path("message");
            JsonNode content = message.path("content");
            return content.isMissingNode() ? "" : content.asText("");
        } catch (Exception e) {
            log.warn("解析视觉模型响应失败: {}", e.getMessage());
            return "";
        }
    }

    private String readStream(java.io.InputStream stream) {
        if (stream == null) return "";
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }
}
