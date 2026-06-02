package com.eduspark.eduspark.service.impl;

import com.eduspark.eduspark.service.IVoiceService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Base64;

/**
 * 阿里云语音识别服务实现
 * 使用阿里云智能语音交互的 REST API 进行语音转文字
 *
 * 配置项（application.yml）：
 *   aliyun.asr.app-key          - 阿里云智能语音交互项目的 AppKey
 *   aliyun.asr.access-key-id    - RAM 用户 AccessKey ID
 *   aliyun.asr.access-key-secret - RAM 用户 AccessKey Secret
 *   aliyun.asr.region          - 地域，如 cn-shanghai
 *   aliyun.asr.api-version     - API 版本，如 2024-04-19
 */
@Slf4j
@Service
public class WhisperServiceImpl implements IVoiceService {

    @Value("${aliyun.asr.enabled:false}")
    private boolean asrEnabled;

    @Value("${aliyun.asr.app-key:}")
    private String appKey;

    @Value("${aliyun.asr.access-key-id:}")
    private String accessKeyId;

    @Value("${aliyun.asr.access-key-secret:}")
    private String accessKeySecret;

    @Value("${aliyun.asr.region:cn-shanghai}")
    private String region;

    @Value("${aliyun.asr.api-version:2024-04-19}")
    private String apiVersion;

    @Value("${aliyun.asr.language:zh-CN}")
    private String defaultLanguage;

    @Value("${aliyun.asr.token:}")
    private String nlsToken;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public WhisperServiceImpl(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public String transcribe(byte[] audioData, String fileName) {
        return transcribe(audioData, fileName, defaultLanguage);
    }

    @Override
    public String transcribe(byte[] audioData, String fileName, String language) {
        log.info("开始阿里云语音识别: fileName={}, language={}, dataSize={}",
                fileName, language, audioData.length);

        if (!asrEnabled) {
            throw new RuntimeException("阿里云ASR未启用，请联系管理员配置 aliyun.asr.enabled=true");
        }

        if (appKey == null || appKey.isBlank() || "your_app_key_here".equals(appKey)) {
            throw new RuntimeException("阿里云ASR未配置 AppKey，请联系管理员配置 aliyun.asr.app-key");
        }

        try {
            // 阿里云智能语音交互 一句话识别 API
            String asrUrl = String.format(
                    "https://nls-gateway-%s.aliyuncs.com/stream/v1/asr",
                    region
            );

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-NLS-Token", generateAliyunToken());
            headers.set("X-NLS-AppKey", appKey);

            // 请求体
            java.util.Map<String, Object> requestBody = new java.util.HashMap<>();
            requestBody.put("appkey", appKey);
            requestBody.put("format", "pcm");          // 音频格式：pcm / wav / opus
            requestBody.put("sample_rate", 16000);     // 采样率 16k
            requestBody.put("language", mapLanguage(language));

            // 音频数据进行 Base64 编码
            String audioBase64 = Base64.getEncoder().encodeToString(audioData);
            requestBody.put("audio", audioBase64);
            requestBody.put("enable_punctuation_prediction", true);  // 启用标点预测
            requestBody.put("enable_inverse_text_normalization", true); // 启用ITN（数字、日期等规整）

            HttpEntity<java.util.Map<String, Object>> entity =
                    new HttpEntity<>(requestBody, headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    asrUrl,
                    HttpMethod.POST,
                    entity,
                    String.class
            );

            return parseAsrResponse(response.getBody());

        } catch (RestClientException e) {
            log.error("阿里云ASR调用失败: {}", e.getMessage());
            throw new RuntimeException("语音识别服务调用失败: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("阿里云ASR异常: {}", e.getMessage(), e);
            throw new RuntimeException("语音识别异常: " + e.getMessage(), e);
        }
    }

    /**
     * 获取阿里云 NLS Token
     * 优先使用配置文件中的 token，否则抛出异常
     */
    private String generateAliyunToken() {
        if (nlsToken != null && !nlsToken.isBlank()) {
            log.debug("使用配置的阿里云NLS Token");
            return nlsToken;
        }
        log.error("阿里云ASR Token未配置，请在application.yml中配置 aliyun.asr.token");
        throw new RuntimeException("阿里云ASR Token未配置，请联系管理员");
    }

    /**
     * 解析阿里云 ASR 响应
     */
    private String parseAsrResponse(String responseBody) {
        try {
            if (responseBody == null || responseBody.isBlank()) {
                return "";
            }
            JsonNode root = objectMapper.readTree(responseBody);

            // 成功时：{"task_id":"...","text":"识别结果文字","duration":1234}
            if (root.has("text") && !root.get("text").isNull()) {
                String text = root.get("text").asText().trim();
                log.info("阿里云ASR识别成功: textLength={}", text.length());
                return text;
            }

            // 失败时
            if (root.has("error_code")) {
                int errorCode = root.get("error_code").asInt();
                String errorMessage = root.has("error_message") ?
                        root.get("error_message").asText() : "未知错误";
                log.error("阿里云ASR返回错误: code={}, msg={}", errorCode, errorMessage);
                throw new RuntimeException("语音识别失败: " + errorMessage);
            }

            return "";

        } catch (Exception e) {
            if (e instanceof RuntimeException) throw (RuntimeException) e;
            log.error("解析ASR响应失败: {}", e.getMessage());
            throw new RuntimeException("解析语音识别结果失败", e);
        }
    }

    /**
     * 将语言代码映射为阿里云ASR支持的语言代码
     */
    private String mapLanguage(String language) {
        if (language == null || language.isBlank()) {
            return "zh-CN";
        }
        return switch (language.toLowerCase()) {
            case "zh", "chinese", "zh-cn" -> "zh-CN";
            case "en", "english", "en-us" -> "en-US";
            case "ja", "japanese", "ja-jp" -> "ja-JP";
            case "ko", "korean", "ko-kr" -> "ko-KR";
            default -> "zh-CN";
        };
    }

    @Override
    public boolean isAvailable() {
        if (!asrEnabled) {
            return false;
        }
        try {
            // 简单健康检查：验证配置是否完整
            return appKey != null && !appKey.isBlank() &&
                   !"your_app_key_here".equals(appKey) &&
                   accessKeyId != null && !accessKeyId.isBlank();
        } catch (Exception e) {
            log.warn("阿里云ASR健康检查失败: {}", e.getMessage());
            return false;
        }
    }
}