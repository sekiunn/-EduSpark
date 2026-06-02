package com.eduspark.eduspark.service.impl;

import com.eduspark.eduspark.dto.interactive.InteractiveContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.NoRouteToHostException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * External interactive HTML writer using an OpenAI-compatible chat completions API.
 */
@Slf4j
@Component
public class ExternalInteractiveHtmlWriterDelegate {

    @Value("${interactive.writer.external.base-url:}")
    private String baseUrl;

    @Value("${interactive.writer.external.chat-path:/chat/completions}")
    private String chatPath;

    @Value("${interactive.writer.external.api-key:}")
    private String apiKey;

    @Value("${interactive.writer.external.model:}")
    private String model;

    @Value("${interactive.writer.external.temperature:0.4}")
    private double temperature;

    @Value("${interactive.writer.external.max-tokens:4096}")
    private int maxTokens;

    @Value("${interactive.writer.external.connect-timeout:30000}")
    private int connectTimeout;

    @Value("${interactive.writer.external.read-timeout:180000}")
    private int readTimeout;

    @Value("${interactive.writer.external.max-attempts:2}")
    private int maxAttempts;

    @Value("${interactive.writer.external.retry-backoff-ms:1200}")
    private long retryBackoffMs;

    private final ObjectMapper objectMapper;
    private final InteractivePromptBuilder promptBuilder;

    public ExternalInteractiveHtmlWriterDelegate(ObjectMapper objectMapper,
                                                 InteractivePromptBuilder promptBuilder) {
        this.objectMapper = objectMapper;
        this.promptBuilder = promptBuilder;
    }

    public String streamInitialHtml(InteractiveContext context, Consumer<String> onChunk) {
        return streamWithRetry(
                promptBuilder.getSystemPrompt(),
                promptBuilder.buildInitialPrompt(context),
                onChunk
        );
    }

    public String streamRefinedHtml(InteractiveContext context,
                                    String currentHtml,
                                    String instruction,
                                    Consumer<String> onChunk) {
        return streamWithRetry(
                promptBuilder.getSystemPrompt(),
                promptBuilder.buildRefinePrompt(context, currentHtml, instruction),
                onChunk
        );
    }

    public String streamPrompt(String systemPrompt, String userPrompt, Consumer<String> onChunk) {
        return streamWithRetry(systemPrompt, userPrompt, onChunk);
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

    private String streamWithRetry(String systemPrompt, String userPrompt, Consumer<String> onChunk) {
        ensureConfigured();

        Consumer<String> safeConsumer = onChunk != null ? onChunk : chunk -> {
        };
        int attemptLimit = Math.max(1, maxAttempts);
        ExternalWriterException lastError = null;

        for (int attempt = 1; attempt <= attemptLimit; attempt++) {
            try {
                return streamOnce(systemPrompt, userPrompt, safeConsumer);
            } catch (ExternalWriterException e) {
                lastError = e;
                Throwable cause = rootCause(e);
                boolean canRetry = !e.hasPartialResponse() && isRetryable(cause) && attempt < attemptLimit;
                if (!canRetry) {
                    break;
                }

                log.warn("External interactive writer transient failure on attempt {}/{}: {}",
                        attempt, attemptLimit, cause != null ? cause.getMessage() : e.getMessage());
                sleepBeforeRetry(attempt);
            }
        }

        Throwable cause = lastError != null ? rootCause(lastError) : null;
        throw new IllegalStateException(buildFailureMessage(cause), cause != null ? cause : lastError);
    }

    private void ensureConfigured() {
        if (!isConfigured()) {
            throw new IllegalStateException("External interactive writer is not configured");
        }
    }

    private String streamOnce(String systemPrompt, String userPrompt, Consumer<String> onChunk) {
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
                    "temperature", temperature,
                    "max_tokens", maxTokens,
                    "messages", List.of(
                            Map.of("role", "system", "content", systemPrompt),
                            Map.of("role", "user", "content", userPrompt)
                    )
            );

            try (OutputStream outputStream = connection.getOutputStream()) {
                outputStream.write(objectMapper.writeValueAsBytes(requestBody));
            }

            int responseCode = connection.getResponseCode();
            if (responseCode < 200 || responseCode >= 300) {
                throw new IllegalStateException("External interactive writer returned HTTP " + responseCode + ": "
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
            log.warn("Failed to parse external interactive chunk: {}", e.getMessage());
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
            return "External interactive writing failed: unable to resolve host "
                    + extractHostName(e.getMessage())
                    + ", please check DNS/network or the configured Base URL";
        }
        return "External interactive writing failed: "
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
