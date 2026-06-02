package com.eduspark.eduspark.service.impl;

import com.eduspark.eduspark.service.ILLMService;
import com.eduspark.eduspark.service.ISystemPromptService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Dashscope OpenAI 兼容接口 LLM 服务实现
 */
@Slf4j
@Service
public class OllamaChatServiceImpl implements ILLMService {

    @Value("${lesson-plan.writer.external.base-url:https://dashscope.aliyuncs.com/compatible-mode/v1}")
    private String baseUrl;

    @Value("${lesson-plan.writer.external.api-key:}")
    private String apiKey;

    @Value("${lesson-plan.writer.external.model:qwen3.6-plus}")
    private String chatModel;

    @Value("${ollama.chat.temperature:0.7}")
    private float temperature;

    @Value("${ollama.chat.max-tokens:1024}")
    private int maxTokens;

    @Value("${ollama.connect-timeout:30000}")
    private int connectTimeout;

    @Value("${ollama.read-timeout:180000}")
    private int readTimeout;

    private final ISystemPromptService systemPromptService;
    private final ObjectMapper objectMapper;

    public OllamaChatServiceImpl(ISystemPromptService systemPromptService, ObjectMapper objectMapper) {
        this.systemPromptService = systemPromptService;
        this.objectMapper = objectMapper;
    }

    @Override
    public String chat(List<ChatMessage> messages) {
        return callChatCompletions(messages, chatModel, false, null);
    }

    @Override
    public String chat(List<ChatMessage> messages, int maxTokensOverride) {
        return callChatCompletions(messages, chatModel, false, null, maxTokensOverride);
    }

    @Override
    public String chatWithModel(List<ChatMessage> messages, String model) {
        return callChatCompletions(messages, model, false, null);
    }

    @Override
    public String chatStream(List<ChatMessage> messages, Consumer<String> onChunk) {
        return callChatCompletions(messages, chatModel, true, onChunk);
    }

    @Override
    public String chatStream(List<ChatMessage> messages, Consumer<String> onChunk, int maxTokensOverride) {
        return callChatCompletions(messages, chatModel, true, onChunk, maxTokensOverride);
    }

    private String callChatCompletions(List<ChatMessage> messages, String model,
                                       boolean stream, Consumer<String> onChunk) {
        return callChatCompletions(messages, model, stream, onChunk, maxTokens);
    }

    private String callChatCompletions(List<ChatMessage> messages, String model,
                                       boolean stream, Consumer<String> onChunk, int maxTokensParam) {
        try {
            String endpoint = resolveEndpoint();
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", model != null ? model : chatModel);
            body.put("stream", stream);
            body.put("temperature", temperature);
            body.put("max_tokens", maxTokensParam > 0 ? maxTokensParam : maxTokens);
            // 非思考模式：关闭混合思考模型（deepseek-v4-flash / qwen3 系等）的思考过程，直接出结果。
            // 更快，且响应只有 content、没有 reasoning_content，契合现有的内容解析逻辑。
            // 注意：若所用模型/endpoint 不支持该参数，需按其文档改用对应的关思考方式。
            body.put("enable_thinking", false);

            List<Map<String, String>> apiMessages = new ArrayList<>();
            for (ChatMessage msg : messages) {
                apiMessages.add(Map.of("role", msg.role(), "content", msg.content()));
            }
            body.put("messages", apiMessages);

            String jsonBody = objectMapper.writeValueAsString(body);
            HttpURLConnection conn = (HttpURLConnection) new URL(endpoint).openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setConnectTimeout(connectTimeout);
            conn.setReadTimeout(readTimeout);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Authorization", "Bearer " + apiKey);
            if (stream) {
                conn.setRequestProperty("Accept", "text/event-stream");
            }

            try (OutputStream os = conn.getOutputStream()) {
                os.write(jsonBody.getBytes(StandardCharsets.UTF_8));
            }

            int responseCode = conn.getResponseCode();
            if (responseCode < 200 || responseCode >= 300) {
                String errorBody = readStream(conn.getErrorStream());
                log.error("Dashscope API 返回错误: code={}, body={}", responseCode, errorBody);
                throw new RuntimeException("LLM 服务调用失败: HTTP " + responseCode);
            }

            if (stream) {
                return readSseResponse(conn, onChunk);
            } else {
                String responseBody = readStream(conn.getInputStream());
                return extractNonStreamContent(responseBody);
            }
        } catch (Exception e) {
            if (e instanceof RuntimeException) throw (RuntimeException) e;
            log.error("Dashscope API 调用失败: {}", e.getMessage(), e);
            throw new RuntimeException("LLM 服务调用失败: " + e.getMessage());
        }
    }

    @Override
    public String chatWithContext(String userMessage, String context, String systemPrompt) {
        String effectiveSystemPrompt = systemPrompt != null ? systemPrompt :
                (context != null ? systemPromptService.ragPrompt(context) : systemPromptService.simplePrompt());

        String enhancedUserMessage = userMessage;
        if (context != null && !context.isBlank()) {
            enhancedUserMessage += "\n\n【重要】正文正常回答即可，不要在正文中标注来源。回答最后必须列出【参考文献】列表（含文档名）。";
        }

        List<ChatMessage> messages = List.of(
                new ChatMessage("system", effectiveSystemPrompt),
                new ChatMessage("user", enhancedUserMessage)
        );

        return chat(messages);
    }

    @Override
    public String chatWithHistory(String userMessage, List<ChatMessage> history, String context, String systemPrompt) {
        List<ChatMessage> messages = new ArrayList<>();

        String effectiveSystemPrompt = systemPrompt != null ? systemPrompt :
                (context != null ? systemPromptService.ragPrompt(context) : systemPromptService.simplePrompt());
        messages.add(new ChatMessage("system", effectiveSystemPrompt));

        if (history != null) {
            for (ChatMessage msg : history) {
                if (msg.role() != null && msg.content() != null) {
                    messages.add(msg);
                }
            }
        }

        String enhancedUserMessage = userMessage;
        if (context != null && !context.isBlank()) {
            enhancedUserMessage += "\n\n【重要】正文正常回答即可，不要在正文中标注来源。回答最后必须列出【参考文献】列表（含文档名）。";
        }
        messages.add(new ChatMessage("user", enhancedUserMessage));

        return chat(messages);
    }

    @Override
    public boolean isAvailable() {
        return apiKey != null && !apiKey.isBlank();
    }

    @Override
    public String getModelName() {
        return chatModel;
    }

    private String resolveEndpoint() {
        String normalizedBase = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        if (normalizedBase.endsWith("/chat/completions")) {
            return normalizedBase;
        }
        return normalizedBase + "/chat/completions";
    }

    private String readSseResponse(HttpURLConnection conn, Consumer<String> onChunk) throws Exception {
        StringBuilder fullResponse = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (!trimmed.startsWith("data:")) continue;

                String payload = trimmed.substring(5).trim();
                if (payload.isEmpty()) continue;
                if ("[DONE]".equals(payload)) break;

                String chunk = extractDeltaContent(payload);
                if (!chunk.isEmpty()) {
                    fullResponse.append(chunk);
                    if (onChunk != null) {
                        onChunk.accept(chunk);
                    }
                }
            }
        }
        return fullResponse.toString();
    }

    private String extractDeltaContent(String payload) {
        try {
            JsonNode root = objectMapper.readTree(payload);
            JsonNode choices = root.path("choices");
            if (!choices.isArray() || choices.isEmpty()) return "";

            JsonNode choice = choices.get(0);
            JsonNode delta = choice.path("delta");
            if (delta.has("content")) {
                return delta.path("content").asText("");
            }
            JsonNode message = choice.path("message");
            if (message.has("content")) {
                return message.path("content").asText("");
            }
            return "";
        } catch (Exception e) {
            log.warn("解析流式响应块失败: {}", e.getMessage());
            return "";
        }
    }

    private String extractNonStreamContent(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode choices = root.path("choices");
            if (!choices.isArray() || choices.isEmpty()) {
                throw new RuntimeException("Dashscope API 未返回 choices");
            }
            JsonNode message = choices.get(0).path("message");
            JsonNode content = message.path("content");
            if (content.isMissingNode() || content.asText("").isEmpty()) {
                throw new RuntimeException("Dashscope API 未返回消息内容");
            }
            return content.asText();
        } catch (Exception e) {
            if (e instanceof RuntimeException) throw (RuntimeException) e;
            throw new RuntimeException("解析 Dashscope 响应失败: " + e.getMessage());
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
