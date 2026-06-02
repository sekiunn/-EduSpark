package com.eduspark.eduspark.service.impl;

import com.eduspark.eduspark.dto.interactive.InteractiveContext;
import com.eduspark.eduspark.dto.interactive.InteractiveDownloadPayload;
import com.eduspark.eduspark.dto.interactive.InteractiveDocumentResponse;
import com.eduspark.eduspark.dto.interactive.InteractiveStageCardData;
import com.eduspark.eduspark.dto.interactive.InteractiveStreamDeltaResponse;
import com.eduspark.eduspark.exception.BusinessException;
import com.eduspark.eduspark.exception.ErrorCode;
import com.eduspark.eduspark.mapper.InteractiveDocumentMapper;
import com.eduspark.eduspark.pojo.entity.ChatSession;
import com.eduspark.eduspark.pojo.entity.InteractiveDocument;
import com.eduspark.eduspark.service.IChatSessionService;
import com.eduspark.eduspark.service.IInteractiveHtmlWriterService;
import com.eduspark.eduspark.service.IInteractiveWorkspaceService;
import com.eduspark.eduspark.service.IInteractiveWorkspaceStreamService;
import com.eduspark.eduspark.util.InteractiveHtmlSafetyUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@Slf4j
@Service
public class InteractiveWorkspaceServiceImpl implements IInteractiveWorkspaceService {

    private static final int STREAM_PERSIST_STEP = 320;
    private static final long STREAM_PERSIST_INTERVAL_MS = 1200L;

    private final InteractiveDocumentMapper interactiveDocumentMapper;
    private final IInteractiveHtmlWriterService interactiveHtmlWriterService;
    private final IChatSessionService chatSessionService;
    private final IInteractiveWorkspaceStreamService workspaceStreamService;
    private final ObjectMapper objectMapper;

    @Value("${courseware.storage.local-path:./data/courseware}")
    private String localStoragePath;

    public InteractiveWorkspaceServiceImpl(InteractiveDocumentMapper interactiveDocumentMapper,
                                           IInteractiveHtmlWriterService interactiveHtmlWriterService,
                                           IChatSessionService chatSessionService,
                                           IInteractiveWorkspaceStreamService workspaceStreamService,
                                           ObjectMapper objectMapper) {
        this.interactiveDocumentMapper = interactiveDocumentMapper;
        this.interactiveHtmlWriterService = interactiveHtmlWriterService;
        this.chatSessionService = chatSessionService;
        this.workspaceStreamService = workspaceStreamService;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public InteractiveStageCardData createWorkspace(ChatSession session, InteractiveContext context) {
        InteractiveDocument document = InteractiveDocument.builder()
                .sessionId(session.getId())
                .userId(session.getUserId())
                .title(resolveTitle(session, context))
                .status(InteractiveDocument.Status.PREPARING)
                .summary("系统会先整理实现上下文，然后在右侧工作区生成一个可运行的互动页面。")
                .sourceContextJson(writeContext(context))
                .enrichedContextJson(writeContext(context))
                .htmlContent("")
                .build();
        interactiveDocumentMapper.insert(document);
        return toCardData(document);
    }

    @Override
    @Async("generationTaskExecutor")
    public void generateInitialDocumentAsync(Long sessionId, Long documentId, InteractiveContext context) {
        InteractiveDocument document = interactiveDocumentMapper.selectById(documentId);
        if (document == null) {
            log.warn("Interactive workspace missing for document {}", documentId);
            return;
        }
        String stableHtml = normalizeHtml(document.getHtmlContent());
        try {
            InteractiveContext normalizedContext = normalizeContext(context, 1);
            document.setEnrichedContextJson(writeContext(normalizedContext));
            interactiveDocumentMapper.updateById(document);
            updateDocumentStatus(document, InteractiveDocument.Status.IMPLEMENTING, "正在生成互动页面初版...", true);
            streamGeneratedHtml(document, normalizedContext, null);
            document.setDownloadUrl(null);
            document.setExportFilePath(null);
            document.setErrorMessage(null);
            document.setEnrichedContextJson(writeContext(normalizedContext));
            updateDocumentStatus(document, InteractiveDocument.Status.COMPLETED, "互动页面已生成完成，可继续预览、修改、保存或下载。", false);
            chatSessionService.updateGenerationStatus(
                    sessionId,
                    ChatSession.GenerationStatus.COMPLETED,
                    "interactive_workspace_ready"
            );
            chatSessionService.updateTeachingMode(sessionId, null, ChatSession.Stage.COMPLETED, writeContext(normalizedContext));
            workspaceStreamService.publishCompleted(document.getId(), toResponse(document));
        } catch (Exception e) {
            handleFailure(sessionId, document, context, stableHtml, "互动页面生成失败，请稍后重试。", e);
        }
    }

    @Override
    @Async("generationTaskExecutor")
    public void refineDocumentAsync(Long sessionId, Long documentId, InteractiveContext context, String instruction) {
        InteractiveDocument document = interactiveDocumentMapper.selectById(documentId);
        if (document == null) {
            log.warn("Interactive workspace missing for refine document {}", documentId);
            return;
        }
        String stableHtml = normalizeHtml(document.getHtmlContent());
        try {
            int version = context != null && context.getCurrentVersion() != null && context.getCurrentVersion() > 0
                    ? context.getCurrentVersion()
                    : 2;
            InteractiveContext normalizedContext = normalizeContext(context, version);
            document.setEnrichedContextJson(writeContext(normalizedContext));
            interactiveDocumentMapper.updateById(document);
            updateDocumentStatus(document, InteractiveDocument.Status.REFINING, "正在根据最新要求调整互动页面...", true);
            streamGeneratedHtml(document, normalizedContext, instruction);
            document.setDownloadUrl(null);
            document.setExportFilePath(null);
            document.setErrorMessage(null);
            document.setEnrichedContextJson(writeContext(normalizedContext));
            updateDocumentStatus(document, InteractiveDocument.Status.COMPLETED, "互动页面已按最新要求更新完成。", false);
            chatSessionService.updateGenerationStatus(
                    sessionId,
                    ChatSession.GenerationStatus.COMPLETED,
                    "interactive_workspace_ready"
            );
            chatSessionService.updateTeachingMode(sessionId, null, ChatSession.Stage.COMPLETED, writeContext(normalizedContext));
            workspaceStreamService.publishCompleted(document.getId(), toResponse(document));
        } catch (Exception e) {
            handleFailure(sessionId, document, context, stableHtml, "互动页面调整失败，请稍后重试。", e);
        }
    }

    @Override
    public InteractiveDocumentResponse getDocument(Long documentId, Long userId) {
        return toResponse(getOwnedDocument(documentId, userId));
    }

    @Override
    @Transactional
    public InteractiveDocumentResponse updateDocumentContent(Long documentId, Long userId, String content) {
        InteractiveDocument document = getOwnedDocument(documentId, userId);
        requireCompletedDocument(document);
        InteractiveHtmlSafetyUtils.SanitizationResult sanitizedResult =
                InteractiveHtmlSafetyUtils.sanitizeManualHtml(content, document.getHtmlContent());
        String normalized = normalizeHtml(sanitizedResult.html());
        if (!hasText(normalized) && hasText(document.getHtmlContent())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "HTML 内容为空，已拦截本次保存以避免覆盖已有内容");
        }
        document.setHtmlContent(normalized);
        if (sanitizedResult.executableContentRemoved()) {
            demoteManualSaveContext(document);
        }
        document.setDownloadUrl(null);
        document.setExportFilePath(null);
        interactiveDocumentMapper.updateById(document);
        InteractiveDocumentResponse response = toResponse(document);
        workspaceStreamService.publishSnapshot(documentId, response);
        return response;
    }

    @Override
    @Transactional
    public InteractiveDocumentResponse exportDocument(Long documentId, Long userId) {
        InteractiveDocument document = getOwnedDocument(documentId, userId);
        requireCompletedDocument(document);
        String html = normalizeHtml(document.getHtmlContent());
        if (!hasText(html)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "当前互动页面 HTML 为空，暂时不能导出");
        }
        String fileName = buildExportFileName(document);
        String filePath = saveLocally(fileName, html);
        document.setExportFilePath(filePath);
        document.setDownloadUrl(buildDownloadUrl(document.getId()));
        interactiveDocumentMapper.updateById(document);
        InteractiveDocumentResponse response = toResponse(document);
        workspaceStreamService.publishSnapshot(documentId, response);
        return response;
    }

    @Override
    public InteractiveDownloadPayload downloadDocument(Long documentId, Long userId) {
        InteractiveDocument document = getOwnedDocument(documentId, userId);
        Path exportPath = resolveExportPath(document);
        try {
            InputStream inputStream = Files.newInputStream(exportPath);
            return InteractiveDownloadPayload.builder()
                    .inputStream(inputStream)
                    .fileName(exportPath.getFileName().toString())
                    .contentType("text/html;charset=UTF-8")
                    .build();
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.FILE_DOWNLOAD_ERROR, "互动页面下载失败: " + e.getMessage(), e);
        }
    }

    private void handleFailure(Long sessionId,
                               InteractiveDocument document,
                               InteractiveContext context,
                               String stableHtml,
                               String summary,
                               Exception error) {
        log.error("Interactive workspace generation failed: sessionId={}, documentId={}", sessionId, document.getId(), error);
        document.setHtmlContent(normalizeHtml(stableHtml));
        document.setErrorMessage(resolveUserFacingError(error, hasText(stableHtml)));
        updateDocumentStatus(document, InteractiveDocument.Status.FAILED, summary, false);
        chatSessionService.updateGenerationStatus(
                sessionId,
                ChatSession.GenerationStatus.FAILED,
                "interactive_workspace_failed"
        );
        chatSessionService.updateTeachingMode(
                sessionId,
                null,
                ChatSession.Stage.CLARIFYING,
                writeContext(context == null ? InteractiveContext.empty() : context)
        );
        workspaceStreamService.publishFailed(document.getId(), toResponse(document));
    }

    private void streamGeneratedHtml(InteractiveDocument document, InteractiveContext context, String instruction) {
        StringBuilder streamedContent = new StringBuilder();
        long[] lastPersistAt = {System.currentTimeMillis()};
        int[] lastPersistLength = {0};

        String generatedContent = instruction == null
                ? interactiveHtmlWriterService.streamInitialHtml(
                context,
                chunk -> onChunk(document, chunk, streamedContent, lastPersistLength, lastPersistAt)
        )
                : interactiveHtmlWriterService.streamRefinedHtml(
                context,
                normalizeHtml(document.getHtmlContent()),
                instruction,
                chunk -> onChunk(document, chunk, streamedContent, lastPersistLength, lastPersistAt)
        );

        String finalContent = normalizeHtml(hasText(generatedContent) ? generatedContent : streamedContent.toString());
        if (!hasText(finalContent)) {
            throw new IllegalStateException("Writer returned empty interactive HTML");
        }
        persistHtml(document, finalContent);
    }

    private void onChunk(InteractiveDocument document,
                         String chunk,
                         StringBuilder streamedContent,
                         int[] lastPersistLength,
                         long[] lastPersistAt) {
        if (!hasText(chunk)) {
            return;
        }
        streamedContent.append(chunk);
        workspaceStreamService.publishContentDelta(
                document.getId(),
                InteractiveStreamDeltaResponse.builder()
                        .documentId(document.getId())
                        .delta(chunk)
                        .build()
        );
        maybePersistProgress(document, streamedContent.toString(), lastPersistLength, lastPersistAt);
    }

    private void maybePersistProgress(InteractiveDocument document,
                                      String currentContent,
                                      int[] lastPersistLength,
                                      long[] lastPersistAt) {
        int deltaLength = currentContent.length() - lastPersistLength[0];
        long now = System.currentTimeMillis();
        if (deltaLength < STREAM_PERSIST_STEP && now - lastPersistAt[0] < STREAM_PERSIST_INTERVAL_MS) {
            return;
        }
        document.setHtmlContent(currentContent);
        interactiveDocumentMapper.updateById(document);
        lastPersistLength[0] = currentContent.length();
        lastPersistAt[0] = now;
    }

    private InteractiveDocument getOwnedDocument(Long documentId, Long userId) {
        InteractiveDocument document = interactiveDocumentMapper.selectOwnedById(documentId, userId);
        if (document == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "互动工作区不存在");
        }
        return document;
    }

    private void persistHtml(InteractiveDocument document, String htmlContent) {
        document.setHtmlContent(normalizeGeneratedHtml(htmlContent));
        interactiveDocumentMapper.updateById(document);
    }

    private String normalizeGeneratedHtml(String htmlContent) {
        return InteractiveHtmlSafetyUtils.sanitizeGeneratedHtml(htmlContent).html();
    }

    private void demoteManualSaveContext(InteractiveDocument document) {
        InteractiveContext context = readContext(document.getEnrichedContextJson(), document.getSourceContextJson());
        context.setRenderMode(InteractiveTemplateCatalog.RENDER_MODE_CUSTOM_HTML);
        context.setTemplateId(null);
        context.setTemplateVersion(null);
        context.setTemplateSpec(Map.of());

        String contextJson = writeContext(context);
        document.setEnrichedContextJson(contextJson);
        chatSessionService.updateTeachingMode(
                document.getSessionId(),
                null,
                ChatSession.Stage.COMPLETED,
                contextJson
        );
    }

    private void requireCompletedDocument(InteractiveDocument document) {
        if (!InteractiveDocument.Status.COMPLETED.equalsIgnoreCase(document.getStatus())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "当前互动页面尚未生成完成，暂时不能保存或导出");
        }
    }

    private void updateDocumentStatus(InteractiveDocument document, String status, String summary, boolean publishImmediately) {
        document.setStatus(status);
        document.setSummary(summary);
        interactiveDocumentMapper.updateById(document);
        if (publishImmediately) {
            workspaceStreamService.publishStatus(document.getId(), toResponse(document));
        }
    }

    private InteractiveStageCardData toCardData(InteractiveDocument document) {
        return InteractiveStageCardData.builder()
                .documentId(document.getId())
                .mode(ChatSession.Mode.INTERACTIVE)
                .modeName("互动内容")
                .title(document.getTitle())
                .status(document.getStatus())
                .statusText(resolveStatusText(document.getStatus()))
                .summary(document.getSummary())
                .build();
    }

    private InteractiveDocumentResponse toResponse(InteractiveDocument document) {
        Map<String, Object> sourceContext = readContextMap(document.getSourceContextJson());
        Map<String, Object> enrichedContext = readContextMap(
                hasText(document.getEnrichedContextJson()) ? document.getEnrichedContextJson() : document.getSourceContextJson()
        );
        return InteractiveDocumentResponse.builder()
                .documentId(document.getId())
                .title(document.getTitle())
                .status(document.getStatus())
                .statusText(resolveStatusText(document.getStatus()))
                .summary(document.getSummary())
                .htmlContent(normalizeHtml(document.getHtmlContent()))
                .downloadUrl(hasText(document.getExportFilePath()) ? buildDownloadUrl(document.getId()) : null)
                .errorMessage(document.getErrorMessage())
                .sourceContext(sourceContext)
                .enrichedContext(enrichedContext)
                .updateTime(document.getUpdateTime())
                .build();
    }

    private Map<String, Object> readContextMap(String json) {
        try {
            if (!hasText(json)) {
                return Map.of();
            }
            Map<String, Object> parsed = objectMapper.readValue(json, Map.class);
            return parsed != null ? parsed : Map.of();
        } catch (Exception e) {
            log.warn("Failed to parse interactive context json", e);
            return Map.of();
        }
    }

    private InteractiveContext readContext(String preferredJson, String fallbackJson) {
        try {
            String json = hasText(preferredJson) ? preferredJson : fallbackJson;
            if (!hasText(json)) {
                return InteractiveContext.empty();
            }
            return InteractiveContext.fromMap(objectMapper.readValue(json, Map.class));
        } catch (Exception e) {
            log.warn("Failed to parse interactive context, fallback to empty context");
            return InteractiveContext.empty();
        }
    }

    private InteractiveContext normalizeContext(InteractiveContext context, int version) {
        InteractiveContext normalized = context == null ? InteractiveContext.empty() : InteractiveContext.fromMap(context.toMap());
        normalized.setCurrentVersion(version);
        if (!hasText(normalized.getRenderMode())) {
            normalized.setRenderMode(resolveRenderMode(normalized));
        }
        return normalized;
    }

    private String resolveRenderMode(InteractiveContext context) {
        if (context == null) {
            return InteractiveTemplateCatalog.RENDER_MODE_CUSTOM_HTML;
        }
        if (hasText(context.getTemplateId())
                && InteractiveTemplateCatalog.SUPPORTED_TEMPLATE_IDS.contains(context.getTemplateId())) {
            return InteractiveTemplateCatalog.RENDER_MODE_TEMPLATE;
        }
        if (!context.safeTemplateSpec().isEmpty()) {
            return InteractiveTemplateCatalog.RENDER_MODE_TEMPLATE;
        }
        return InteractiveTemplateCatalog.RENDER_MODE_CUSTOM_HTML;
    }

    private String writeContext(InteractiveContext context) {
        try {
            return objectMapper.writeValueAsString((context == null ? InteractiveContext.empty() : context).toMap());
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.SERVER_ERROR, "互动上下文序列化失败");
        }
    }

    private String resolveTitle(ChatSession session, InteractiveContext context) {
        if (context != null && hasText(context.getTopic())) {
            return context.getTopic();
        }
        return hasText(session.getTitle()) ? session.getTitle() : "互动页面";
    }

    private String resolveStatusText(String status) {
        if (!hasText(status)) {
            return "准备中";
        }
        return switch (status) {
            case InteractiveDocument.Status.IMPLEMENTING -> "生成中";
            case InteractiveDocument.Status.REFINING -> "调整中";
            case InteractiveDocument.Status.COMPLETED -> "已完成";
            case InteractiveDocument.Status.FAILED -> "生成失败";
            default -> "准备中";
        };
    }

    private String normalizeHtml(String html) {
        return InteractiveHtmlSafetyUtils.normalizeHtml(html);
    }

    private String resolveUserFacingError(Exception error, boolean hasStableHtml) {
        String fallback = hasStableHtml
                ? "本次生成失败，已回退到上一个可用版本。"
                : "本次生成失败，未产出可用页面，请重试。";
        if (error == null || !hasText(error.getMessage())) {
            return fallback;
        }

        String message = error.getMessage();
        if (message.contains("script 标签未正确闭合")) {
            return hasStableHtml
                    ? "本次生成失败，脚本不完整，已回退到上一个可用版本。"
                    : "本次生成失败，脚本不完整，请重试。";
        }
        if (message.contains("style 标签未正确闭合")) {
            return hasStableHtml
                    ? "本次生成失败，样式结构不完整，已回退到上一个可用版本。"
                    : "本次生成失败，样式结构不完整，请重试。";
        }
        if (message.contains("body 标签不完整")
                || message.contains("DOCTYPE")
                || message.contains("html 根标签数量不正确")
                || message.contains("malformed content")) {
            return hasStableHtml
                    ? "本次生成失败，HTML 结构不完整，已回退到上一个可用版本。"
                    : "本次生成失败，HTML 结构不完整，请重试。";
        }
        return fallback;
    }

    private String buildExportFileName(InteractiveDocument document) {
        String baseName = hasText(document.getTitle()) ? document.getTitle() : "互动页面";
        String suffix = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        return sanitizeFileName(baseName) + "_interactive_" + suffix + ".html";
    }

    private String saveLocally(String fileName, String htmlContent) {
        try {
            Path dir = Paths.get(localStoragePath);
            Files.createDirectories(dir);
            Path filePath = dir.resolve(fileName);
            Files.writeString(filePath, htmlContent, StandardCharsets.UTF_8);
            return filePath.toString();
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.SERVER_ERROR, "互动页面导出失败: " + e.getMessage());
        }
    }

    private Path resolveExportPath(InteractiveDocument document) {
        if (document == null || !hasText(document.getExportFilePath())) {
            throw new BusinessException(ErrorCode.FILE_NOT_FOUND, "当前互动页面尚未导出");
        }

        try {
            Path storageRoot = Paths.get(localStoragePath).toAbsolutePath().normalize();
            Path candidate = Paths.get(document.getExportFilePath());
            Path resolvedPath = candidate.isAbsolute()
                    ? candidate.toAbsolutePath().normalize()
                    : storageRoot.resolve(candidate).normalize();
            if (!resolvedPath.startsWith(storageRoot) || !Files.exists(resolvedPath)) {
                throw new BusinessException(ErrorCode.FILE_NOT_FOUND, "互动页面导出文件不存在");
            }
            return resolvedPath;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.FILE_NOT_FOUND, "互动页面导出文件不存在", e);
        }
    }

    private String buildDownloadUrl(Long documentId) {
        return "/api/v1/interactive/documents/" + documentId + "/download";
    }

    private String sanitizeFileName(String fileName) {
        if (!hasText(fileName)) {
            return "interactive";
        }
        String sanitized = fileName.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        return sanitized.isBlank() ? "interactive" : sanitized;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
