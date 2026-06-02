package com.eduspark.eduspark.service.impl;

import com.eduspark.eduspark.dto.courseware.LessonPlanRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.net.ConnectException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.NoRouteToHostException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/**
 * External lesson-plan writer using an OpenAI-compatible chat completions API.
 */
@Slf4j
@Component
public class ExternalLessonPlanWriterDelegate {

    @Value("${lesson-plan.writer.external.base-url:}")
    private String baseUrl;

    @Value("${lesson-plan.writer.external.chat-path:/chat/completions}")
    private String chatPath;

    @Value("${lesson-plan.writer.external.api-key:}")
    private String apiKey;

    @Value("${lesson-plan.writer.external.model:}")
    private String model;

    @Value("${lesson-plan.writer.external.temperature:0.4}")
    private double temperature;

    @Value("${lesson-plan.writer.external.max-tokens:3072}")
    private int maxTokens;

    @Value("${lesson-plan.writer.external.connect-timeout:30000}")
    private int connectTimeout;

    @Value("${lesson-plan.writer.external.read-timeout:180000}")
    private int readTimeout;

    @Value("${lesson-plan.writer.external.max-attempts:2}")
    private int maxAttempts;

    @Value("${lesson-plan.writer.external.retry-backoff-ms:1200}")
    private long retryBackoffMs;

    private final ObjectMapper objectMapper;
    private final LessonPlanPromptBuilder promptBuilder;

    public ExternalLessonPlanWriterDelegate(ObjectMapper objectMapper, LessonPlanPromptBuilder promptBuilder) {
        this.objectMapper = objectMapper;
        this.promptBuilder = promptBuilder;
    }

    public String streamDraft(LessonPlanRequest request, Consumer<String> onChunk) {
        ensureConfigured();

        Consumer<String> safeConsumer = onChunk != null ? onChunk : chunk -> {
        };
        int attemptLimit = Math.max(1, maxAttempts);
        ExternalWriterException lastError = null;

        for (int attempt = 1; attempt <= attemptLimit; attempt++) {
            try {
                return streamDraftOnce(request, safeConsumer);
            } catch (ExternalWriterException e) {
                lastError = e;
                Throwable cause = rootCause(e);
                boolean canRetry = !e.hasPartialResponse() && isRetryable(cause) && attempt < attemptLimit;
                if (!canRetry) {
                    break;
                }

                log.warn("External lesson-plan writer transient failure on attempt {}/{}: {}",
                        attempt, attemptLimit, cause != null ? cause.getMessage() : e.getMessage());
                sleepBeforeRetry(attempt);
            }
        }

        Throwable cause = lastError != null ? rootCause(lastError) : null;
        throw new IllegalStateException(buildFailureMessage(cause), cause != null ? cause : lastError);
    }

    public boolean isConfigured() {
        return hasText(baseUrl) && hasText(apiKey) && hasText(model);
    }

    public String getConfiguredModel() {
        return model;
    }

    public String getConfiguredBaseUrl() {
        return baseUrl;
    }

    private void ensureConfigured() {
        if (!isConfigured()) {
            throw new IllegalStateException("External lesson-plan writer is not configured");
        }
    }

    private String streamDraftOnce(LessonPlanRequest request, Consumer<String> onChunk) {
        StringBuilder fullResponse = new StringBuilder();
        HttpURLConnection connection = null;
        try {
            URL url = new URL(resolveEndpoint());
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setConnectTimeout(connectTimeout);
            connection.setReadTimeout(readTimeout);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Accept", "text/event-stream");
            connection.setRequestProperty("Authorization", "Bearer " + apiKey);

            Map<String, Object> requestBody = Map.of(
                    "model", model,
                    "stream", true,
                    // 非思考模式：混合思考模型(deepseek-v4-flash 等)若开思考，会先流式吐一大段 reasoning_content、
                    // 而这里只取 content，导致思考阶段前端一直空白、思考完才涌出正文 → 体感"非流式"。
                    // 关掉思考，正文就会立刻逐字流出来。
                    "enable_thinking", false,
                    "temperature", temperature,
                    "max_tokens", maxTokens,
                    "messages", List.of(
                            Map.of("role", "system", "content", promptBuilder.getSystemPrompt()),
                            Map.of("role", "user", "content", promptBuilder.buildDraftPrompt(request))
                    )
            );

            try (OutputStream outputStream = connection.getOutputStream()) {
                outputStream.write(objectMapper.writeValueAsBytes(requestBody));
            }

            int responseCode = connection.getResponseCode();
            if (responseCode < 200 || responseCode >= 300) {
                throw new IllegalStateException("External lesson-plan writer returned HTTP " + responseCode + ": "
                        + readAll(connection.getErrorStream()));
            }

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String trimmed = line.trim();
                    if (!trimmed.startsWith("data:")) {
                        continue;
                    }

                    String payload = trimmed.substring(5).trim();
                    if (payload.isEmpty()) {
                        continue;
                    }
                    if ("[DONE]".equals(payload)) {
                        break;
                    }

                    String chunk = extractContent(payload);
                    if (!chunk.isEmpty()) {
                        fullResponse.append(chunk);
                        onChunk.accept(chunk);
                    }
                }
            }
            return fullResponse.toString();
        } catch (Exception e) {
            throw new ExternalWriterException(e, fullResponse.length() > 0);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private String resolveEndpoint() {
        String normalizedBase = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        if (normalizedBase.endsWith("/chat/completions")) {
            return normalizedBase;
        }

        String normalizedPath = chatPath.startsWith("/") ? chatPath : "/" + chatPath;
        if (normalizedBase.endsWith("/v1") && normalizedPath.startsWith("/v1/")) {
            normalizedPath = normalizedPath.substring(3);
        }
        return normalizedBase + normalizedPath;
    }

    private String extractContent(String payload) {
        try {
            JsonNode root = objectMapper.readTree(payload);
            JsonNode choices = root.path("choices");
            if (!choices.isArray() || choices.isEmpty()) {
                return "";
            }

            JsonNode choice = choices.get(0);
            JsonNode delta = choice.path("delta");
            if (delta.has("content")) {
                return delta.path("content").asText("");
            }

            JsonNode message = choice.path("message");
            if (message.has("content")) {
                return message.path("content").asText("");
            }

            if (choice.has("text")) {
                return choice.path("text").asText("");
            }
            return "";
        } catch (Exception e) {
            log.warn("Failed to parse external lesson-plan chunk: {}", e.getMessage());
            return "";
        }
    }

    private String readAll(InputStream stream) {
        if (stream == null) {
            return "";
        }

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            StringBuilder builder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line);
            }
            return builder.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private boolean isRetryable(Throwable throwable) {
        return throwable instanceof UnknownHostException
                || throwable instanceof SocketTimeoutException
                || throwable instanceof ConnectException
                || throwable instanceof NoRouteToHostException;
    }

    private String buildFailureMessage(Throwable throwable) {
        if (throwable instanceof UnknownHostException e) {
            return "External lesson-plan writing failed: unable to resolve host "
                    + extractHostName(e.getMessage())
                    + ", please check DNS/network or the configured Base URL";
        }
        return "External lesson-plan writing failed: "
                + (throwable != null && hasText(throwable.getMessage()) ? throwable.getMessage() : "unknown error");
    }

    private Throwable rootCause(Throwable throwable) {
        Throwable current = throwable;
        while (current != null && current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }

    private String extractHostName(String message) {
        if (!hasText(message)) {
            return resolveEndpoint();
        }
        return message.trim();
    }

    private void sleepBeforeRetry(int attempt) {
        long backoff = Math.max(0L, retryBackoffMs) * attempt;
        if (backoff <= 0) {
            return;
        }
        try {
            Thread.sleep(backoff);
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
        }
    }

    private static final class ExternalWriterException extends RuntimeException {

        private final boolean partialResponse;

        private ExternalWriterException(Throwable cause, boolean partialResponse) {
            super(cause);
            this.partialResponse = partialResponse;
        }

        private boolean hasPartialResponse() {
            return partialResponse;
        }
    }
}
