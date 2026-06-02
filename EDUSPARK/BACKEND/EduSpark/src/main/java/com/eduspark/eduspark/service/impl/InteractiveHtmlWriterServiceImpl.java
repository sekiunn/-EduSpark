package com.eduspark.eduspark.service.impl;

import com.eduspark.eduspark.dto.interactive.InteractiveContext;
import com.eduspark.eduspark.service.IInteractiveHtmlWriterService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class InteractiveHtmlWriterServiceImpl implements IInteractiveHtmlWriterService {

    private static final Pattern COMPLETE_HTML_BLOCK_PATTERN =
            Pattern.compile("(?is)<!doctype\\s+html[\\s\\S]*?</html>|<html[\\s\\S]*?</html>");
    private static final Pattern DOCTYPE_PATTERN = Pattern.compile("(?is)<!doctype\\s+html[^>]*>");
    private static final Pattern HTML_OPEN_PATTERN = Pattern.compile("(?is)<html\\b[^>]*>");
    private static final Pattern HTML_CLOSE_PATTERN = Pattern.compile("(?is)</html>");
    private static final Pattern BODY_OPEN_PATTERN = Pattern.compile("(?is)<body\\b[^>]*>");
    private static final Pattern BODY_CLOSE_PATTERN = Pattern.compile("(?is)</body>");
    private static final Pattern BODY_BLOCK_PATTERN = Pattern.compile("(?is)<body\\b[^>]*>([\\s\\S]*?)(?:</body>|$)");
    private static final Pattern SCRIPT_OPEN_PATTERN = Pattern.compile("(?is)<script\\b[^>]*>");
    private static final Pattern SCRIPT_CLOSE_PATTERN = Pattern.compile("(?is)</script>");
    private static final Pattern STYLE_OPEN_PATTERN = Pattern.compile("(?is)<style\\b[^>]*>");
    private static final Pattern STYLE_CLOSE_PATTERN = Pattern.compile("(?is)</style>");

    @Value("${interactive.writer.mode:external}")
    private String writerMode;

    @Value("${interactive.writer.fallback-to-local:false}")
    private boolean fallbackToLocal;

    private final InteractivePromptBuilder promptBuilder;
    private final InteractiveTemplateGenerationService templateGenerationService;
    private final ExternalInteractiveHtmlWriterDelegate externalWriter;
    private final LocalInteractiveHtmlWriterDelegate localWriter;

    public InteractiveHtmlWriterServiceImpl(InteractivePromptBuilder promptBuilder,
                                            InteractiveTemplateGenerationService templateGenerationService,
                                            ExternalInteractiveHtmlWriterDelegate externalWriter,
                                            LocalInteractiveHtmlWriterDelegate localWriter) {
        this.promptBuilder = promptBuilder;
        this.templateGenerationService = templateGenerationService;
        this.externalWriter = externalWriter;
        this.localWriter = localWriter;
    }

    @Override
    public String streamInitialHtml(InteractiveContext context, Consumer<String> onChunk) {
        String templateHtml = templateGenerationService.tryGenerateInitialHtml(context, onChunk);
        if (hasText(templateHtml)) {
            log.info("Interactive writer route: template renderer (templateId={})", context.getTemplateId());
            return finalizeHtml(templateHtml, context);
        }
        return generateValidatedHtml(
                context,
                consumer -> shouldUseExternal()
                        ? streamInitialHtmlWithExternal(context, consumer)
                        : streamInitialHtmlWithLocal(context, consumer),
                onChunk
        );
    }

    @Override
    public String streamRefinedHtml(InteractiveContext context,
                                    String currentHtml,
                                    String instruction,
                                    Consumer<String> onChunk) {
        String templateHtml = templateGenerationService.tryGenerateRefinedHtml(context, currentHtml, instruction, onChunk);
        if (hasText(templateHtml)) {
            log.info("Interactive writer route: template renderer (templateId={})", context.getTemplateId());
            return finalizeHtml(templateHtml, context);
        }
        return generateValidatedHtml(
                context,
                consumer -> shouldUseExternal()
                        ? streamRefinedHtmlWithExternal(context, currentHtml, instruction, consumer)
                        : streamRefinedHtmlWithLocal(context, currentHtml, instruction, consumer),
                onChunk
        );
    }

    private String generateValidatedHtml(InteractiveContext context,
                                         HtmlGenerationOperation operation,
                                         Consumer<String> onChunk) {
        String raw = operation.generate(onChunk != null ? onChunk : chunk -> {
        });
        return finalizeHtml(raw, context);
    }

    private String finalizeHtml(String raw, InteractiveContext context) {
        String sanitized = sanitizeHtml(raw, context);
        List<String> issues = validateHtml(sanitized);
        if (issues.isEmpty()) {
            return sanitized;
        }

        String issueSummary = String.join("；", issues);
        log.warn("Interactive HTML invalid after generation, attempting repair: {}", issueSummary);

        String repairInput = buildRepairInput(raw, sanitized);
        String repairedRaw = streamRepairPrompt(
                promptBuilder.getSystemPrompt(),
                promptBuilder.buildRepairPrompt(context, repairInput, issueSummary),
                chunk -> {
                }
        );

        String repaired = sanitizeHtml(repairedRaw, context);
        List<String> repairIssues = validateHtml(repaired);
        if (repairIssues.isEmpty()) {
            return repaired;
        }

        throw new IllegalStateException(
                "Interactive HTML generation returned malformed content: " + String.join("; ", repairIssues)
        );
    }

    private String streamInitialHtmlWithExternal(InteractiveContext context, Consumer<String> onChunk) {
        if (!externalWriter.isConfigured()) {
            if (!fallbackToLocal) {
                throw new IllegalStateException("External interactive writer is not configured");
            }
            log.warn("Interactive writer route: external requested but not configured, fallback to local");
            return streamInitialHtmlWithLocal(context, onChunk);
        }

        try {
            log.info("Interactive writer route: external model={}, baseUrl={}",
                    externalWriter.getConfiguredModel(), externalWriter.getConfiguredBaseUrl());
            return externalWriter.streamInitialHtml(context, onChunk);
        } catch (Exception e) {
            if (!fallbackToLocal) {
                throw e;
            }
            log.warn("Interactive writer route: external failed, fallback to local writer: {}", e.getMessage());
            return streamInitialHtmlWithLocal(context, onChunk);
        }
    }

    private String streamRefinedHtmlWithExternal(InteractiveContext context,
                                                 String currentHtml,
                                                 String instruction,
                                                 Consumer<String> onChunk) {
        if (!externalWriter.isConfigured()) {
            if (!fallbackToLocal) {
                throw new IllegalStateException("External interactive writer is not configured");
            }
            log.warn("Interactive writer route: external requested but not configured, fallback to local");
            return streamRefinedHtmlWithLocal(context, currentHtml, instruction, onChunk);
        }

        try {
            log.info("Interactive writer route: external model={}, baseUrl={}",
                    externalWriter.getConfiguredModel(), externalWriter.getConfiguredBaseUrl());
            return externalWriter.streamRefinedHtml(context, currentHtml, instruction, onChunk);
        } catch (Exception e) {
            if (!fallbackToLocal) {
                throw e;
            }
            log.warn("Interactive writer route: external failed, fallback to local writer: {}", e.getMessage());
            return streamRefinedHtmlWithLocal(context, currentHtml, instruction, onChunk);
        }
    }

    private String streamInitialHtmlWithLocal(InteractiveContext context, Consumer<String> onChunk) {
        log.info("Interactive writer route: local (mode={})", writerMode);
        return localWriter.streamInitialHtml(context, onChunk);
    }

    private String streamRefinedHtmlWithLocal(InteractiveContext context,
                                              String currentHtml,
                                              String instruction,
                                              Consumer<String> onChunk) {
        log.info("Interactive writer route: local (mode={})", writerMode);
        return localWriter.streamRefinedHtml(context, currentHtml, instruction, onChunk);
    }

    private String streamRepairPrompt(String systemPrompt, String userPrompt, Consumer<String> onChunk) {
        if (shouldUseExternal()) {
            if (!externalWriter.isConfigured()) {
                if (!fallbackToLocal) {
                    throw new IllegalStateException("External interactive writer is not configured");
                }
                log.warn("Interactive repair route: external requested but not configured, fallback to local");
                return streamRepairPromptWithLocal(systemPrompt, userPrompt, onChunk);
            }

            try {
                log.info("Interactive repair route: external model={}, baseUrl={}",
                        externalWriter.getConfiguredModel(), externalWriter.getConfiguredBaseUrl());
                return externalWriter.streamPrompt(systemPrompt, userPrompt, onChunk);
            } catch (Exception e) {
                if (!fallbackToLocal) {
                    throw e;
                }
                log.warn("Interactive repair route: external failed, fallback to local writer: {}", e.getMessage());
                return streamRepairPromptWithLocal(systemPrompt, userPrompt, onChunk);
            }
        }

        return streamRepairPromptWithLocal(systemPrompt, userPrompt, onChunk);
    }

    private String streamRepairPromptWithLocal(String systemPrompt, String userPrompt, Consumer<String> onChunk) {
        log.info("Interactive repair route: local (mode={})", writerMode);
        return localWriter.streamPrompt(systemPrompt, userPrompt, onChunk);
    }

    private boolean shouldUseExternal() {
        return "external".equalsIgnoreCase(normalize(writerMode));
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String sanitizeHtml(String raw, InteractiveContext context) {
        String cleaned = stripCodeFence(raw);
        String extractedDocument = extractPreferredDocument(cleaned);
        if (hasText(extractedDocument)) {
            return extractedDocument.trim();
        }

        String bodyContent = extractBodyContent(cleaned);
        if (hasText(bodyContent)) {
            return wrapHtml(bodyContent, context);
        }

        return wrapHtml(stripDanglingDocumentShell(cleaned), context);
    }

    private String buildRepairInput(String raw, String sanitized) {
        String cleaned = stripCodeFence(raw);
        String extractedDocument = extractPreferredDocument(cleaned);
        if (hasText(extractedDocument)) {
            return extractedDocument;
        }
        return hasText(cleaned) ? cleaned : sanitized;
    }

    private String extractPreferredDocument(String cleaned) {
        if (!hasText(cleaned)) {
            return null;
        }

        String normalized = cleaned.trim();
        String lower = normalized.toLowerCase(Locale.ROOT);
        int end = lower.lastIndexOf("</html>");
        if (end >= 0) {
            int lastDoctype = lower.lastIndexOf("<!doctype html", end);
            int lastHtml = lower.lastIndexOf("<html", end);
            int start = lastDoctype >= 0 ? lastDoctype : lastHtml;
            if (start >= 0 && start < end) {
                return normalized.substring(start, end + "</html>".length()).trim();
            }
        }

        Matcher matcher = COMPLETE_HTML_BLOCK_PATTERN.matcher(normalized);
        String lastMatch = null;
        while (matcher.find()) {
            lastMatch = matcher.group();
        }
        return hasText(lastMatch) ? lastMatch.trim() : null;
    }

    private String extractBodyContent(String html) {
        if (!hasText(html)) {
            return null;
        }

        Matcher matcher = BODY_BLOCK_PATTERN.matcher(html);
        String lastBody = null;
        while (matcher.find()) {
            lastBody = matcher.group(1);
        }
        return hasText(lastBody) ? stripDanglingDocumentShell(lastBody) : null;
    }

    private List<String> validateHtml(String html) {
        List<String> issues = new ArrayList<>();
        if (!hasText(html)) {
            issues.add("HTML 为空");
            return issues;
        }

        if (countMatches(DOCTYPE_PATTERN, html) != 1) {
            issues.add("必须且只能有一个 DOCTYPE");
        }
        if (countMatches(HTML_OPEN_PATTERN, html) != 1 || countMatches(HTML_CLOSE_PATTERN, html) != 1) {
            issues.add("html 根标签数量不正确");
        }
        if (countMatches(BODY_OPEN_PATTERN, html) != 1 || countMatches(BODY_CLOSE_PATTERN, html) != 1) {
            issues.add("body 标签不完整");
        }
        if (countMatches(SCRIPT_OPEN_PATTERN, html) != countMatches(SCRIPT_CLOSE_PATTERN, html)) {
            issues.add("script 标签未正确闭合");
        }
        if (countMatches(STYLE_OPEN_PATTERN, html) != countMatches(STYLE_CLOSE_PATTERN, html)) {
            issues.add("style 标签未正确闭合");
        }

        String lowered = html.toLowerCase(Locale.ROOT);
        int firstHtml = lowered.indexOf("<html");
        int bodyIndex = lowered.indexOf("<body");
        if (bodyIndex >= 0) {
            int nestedHtml = lowered.indexOf("<html", bodyIndex + 1);
            if (nestedHtml >= 0 && nestedHtml > firstHtml) {
                issues.add("body 内包含嵌套 html 文档");
            }
            int nestedDoctype = lowered.indexOf("<!doctype html", bodyIndex + 1);
            if (nestedDoctype >= 0) {
                issues.add("body 内包含重复 DOCTYPE");
            }
        }

        if (html.contains("```")) {
            issues.add("仍包含 Markdown 代码块标记");
        }

        return issues;
    }

    private int countMatches(Pattern pattern, String content) {
        Matcher matcher = pattern.matcher(content);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    private String stripCodeFence(String raw) {
        if (raw == null) {
            return "";
        }
        String cleaned = raw.trim();
        if (cleaned.startsWith("```")) {
            cleaned = cleaned.replaceFirst("^```[a-zA-Z]*\\s*", "");
            cleaned = cleaned.replaceFirst("\\s*```$", "");
        }
        return cleaned.trim();
    }

    private String stripDanglingDocumentShell(String value) {
        if (value == null) {
            return "";
        }

        String cleaned = value.trim();
        cleaned = cleaned.replaceAll("(?is)<!doctype\\s+html[^>]*>", "");
        cleaned = cleaned.replaceAll("(?is)</?html\\b[^>]*>", "");
        cleaned = cleaned.replaceAll("(?is)</?head\\b[^>]*>", "");
        cleaned = cleaned.replaceAll("(?is)</?body\\b[^>]*>", "");
        return cleaned.trim();
    }

    private String wrapHtml(String bodyContent, InteractiveContext context) {
        String title = hasText(context.getTopic()) ? context.getTopic().trim() : "互动教学页面";
        String normalizedBody = hasText(bodyContent) ? bodyContent.trim() : "<div></div>";
        return """
                <!DOCTYPE html>
                <html lang="zh-CN">
                <head>
                  <meta charset="UTF-8" />
                  <meta name="viewport" content="width=device-width, initial-scale=1.0" />
                  <title>%s</title>
                </head>
                <body>
                %s
                </body>
                </html>
                """.formatted(title, normalizedBody);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    @FunctionalInterface
    private interface HtmlGenerationOperation {
        String generate(Consumer<String> onChunk);
    }
}
