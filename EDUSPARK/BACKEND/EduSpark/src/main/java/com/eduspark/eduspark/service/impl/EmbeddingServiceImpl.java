package com.eduspark.eduspark.service.impl;

import com.eduspark.eduspark.service.IEmbeddingService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

@Slf4j
@Service
public class EmbeddingServiceImpl implements IEmbeddingService {

    // Embedding 走独立配置：text-embedding-v3 在阿里云 DashScope，必须与聊天大模型解耦——
    // 聊天的 base-url/key 可能被指向 deepseek 等其它平台，若共用会把 embedding 也带到没有
    // text-embedding-v3 的接口上 → 404。这里默认锁定 DashScope，与聊天互不影响。
    @Value("${rag.embedding.base-url:https://dashscope.aliyuncs.com/compatible-mode/v1}")
    private String baseUrl;

    @Value("${rag.embedding.api-key:}")
    private String apiKey;

    @Value("${rag.embedding.model:text-embedding-v3}")
    private String embeddingModel;

    @Value("${ollama.connect-timeout:30000}")
    private int connectTimeout;

    @Value("${rag.ollama.read-timeout:120000}")
    private int readTimeout;

    private final ObjectMapper objectMapper;
    private final ExecutorService executor;

    public EmbeddingServiceImpl(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.executor = Executors.newFixedThreadPool(8);
    }

    @Override
    public float[] embed(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("待向量化的文本不能为空");
        }
        try {
            List<float[]> results = callEmbeddingApi(List.of(text));
            return results.get(0);
        } catch (Exception e) {
            log.error("Embedding 调用失败: {}", e.getMessage());
            throw new RuntimeException("Embedding 服务不可用: " + e.getMessage());
        }
    }

    @Override
    public List<float[]> batchEmbed(List<String> texts) {
        if (texts == null || texts.isEmpty()) return Collections.emptyList();

        // 过滤掉空文本，但保留原始索引
        List<String> validTexts = texts.stream()
                .filter(t -> t != null && !t.isBlank())
                .toList();

        if (validTexts.isEmpty()) {
            // 全部为空，返回空向量列表
            return texts.stream()
                    .map(t -> new float[0])
                    .toList();
        }

        try {
            List<float[]> results = callEmbeddingApi(validTexts);
            List<float[]> output = new ArrayList<>();
            int idx = 0;
            for (String text : texts) {
                if (text != null && !text.isBlank()) {
                    output.add(results.get(idx++));
                } else {
                    output.add(new float[0]);
                }
            }
            return output;
        } catch (Exception e) {
            log.error("批量向量化失败: {}, textsCount={}", e.getMessage(), texts.size());
            throw new RuntimeException("批量向量化失败: " + e.getMessage());
        }
    }

    private List<float[]> callEmbeddingApi(List<String> texts) throws Exception {
        String endpoint = resolveEndpoint();
        
        // 阿里云 DashScope 要求：单次最多25条文本
        if (texts.size() > 25) {
            log.warn("批量文本数量 {} 超过限制，将分批处理", texts.size());
            return processInBatches(texts, 25);
        }
        
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", embeddingModel);
        body.put("input", texts);

        String jsonBody = objectMapper.writeValueAsString(body);
        log.debug("调用 Embedding API: endpoint={}, model={}, textsCount={}", endpoint, embeddingModel, texts.size());
        
        HttpURLConnection conn = (HttpURLConnection) new URL(endpoint).openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setConnectTimeout(connectTimeout);
        conn.setReadTimeout(readTimeout);
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        conn.setRequestProperty("Authorization", "Bearer " + apiKey);

        try (var os = conn.getOutputStream()) {
            os.write(jsonBody.getBytes(StandardCharsets.UTF_8));
        }

        int responseCode = conn.getResponseCode();
        if (responseCode < 200 || responseCode >= 300) {
            String errorBody = readStream(conn.getErrorStream());
            log.error("Embedding API 返回错误: code={}, body={}, request={}", responseCode, errorBody, jsonBody);
            throw new RuntimeException("Embedding API 调用失败: HTTP " + responseCode + " - " + errorBody);
        }

        String responseBody = readStream(conn.getInputStream());
        log.debug("Embedding API 调用成功: responseLength={}", responseBody.length());
        return parseEmbeddingResponse(responseBody, texts.size());
    }

    /**
     * 分批处理大量文本
     */
    private List<float[]> processInBatches(List<String> texts, int batchSize) throws Exception {
        List<float[]> allResults = new ArrayList<>();
        for (int i = 0; i < texts.size(); i += batchSize) {
            int end = Math.min(i + batchSize, texts.size());
            List<String> batch = texts.subList(i, end);
            List<float[]> batchResults = callEmbeddingApi(batch);
            allResults.addAll(batchResults);
        }
        return allResults;
    }

    private List<float[]> parseEmbeddingResponse(String responseBody, int expectedCount) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode data = root.path("data");
            if (!data.isArray()) {
                throw new RuntimeException("Embedding API 未返回 data 数组");
            }

            List<float[]> results = new ArrayList<>();
            for (JsonNode item : data) {
                JsonNode embeddingNode = item.path("embedding");
                if (!embeddingNode.isArray()) {
                    throw new RuntimeException("Embedding API 返回格式异常: 缺少 embedding 数组");
                }
                float[] vector = new float[embeddingNode.size()];
                for (int i = 0; i < embeddingNode.size(); i++) {
                    vector[i] = (float) embeddingNode.get(i).asDouble();
                }
                results.add(vector);
            }

            if (results.size() != expectedCount) {
                throw new RuntimeException("Embedding 返回数量不匹配: expected=" + expectedCount + ", actual=" + results.size());
            }
            return results;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("解析 Embedding 响应失败: " + e.getMessage());
        }
    }

    private String resolveEndpoint() {
        String normalizedBase = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        if (normalizedBase.endsWith("/embeddings")) {
            return normalizedBase;
        }
        return normalizedBase + "/embeddings";
    }

    @Override
    public boolean isAvailable() {
        return apiKey != null && !apiKey.isBlank();
    }

    @Override
    public String getModelName() {
        return embeddingModel;
    }

    private String readStream(java.io.InputStream stream) {
        if (stream == null) return "";
        try (var reader = new java.io.BufferedReader(
                new java.io.InputStreamReader(stream, StandardCharsets.UTF_8))) {
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
