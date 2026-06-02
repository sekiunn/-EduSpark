package com.eduspark.eduspark.service.impl;

import com.eduspark.eduspark.dto.chat.TeachingBlueprint;
import com.eduspark.eduspark.dto.courseware.LessonPlanRequest;
import com.eduspark.eduspark.dto.courseware.LessonPlanResponse;
import com.eduspark.eduspark.dto.lessonplan.LessonPlanDocumentResponse;
import com.eduspark.eduspark.dto.lessonplan.LessonPlanKnowledgeSourceItem;
import com.eduspark.eduspark.dto.lessonplan.LessonPlanRewriteResponse;
import com.eduspark.eduspark.dto.lessonplan.LessonPlanStreamDeltaResponse;
import com.eduspark.eduspark.dto.lessonplan.LessonPlanWorkspaceCardData;
import com.eduspark.eduspark.exception.BusinessException;
import com.eduspark.eduspark.exception.ErrorCode;
import com.eduspark.eduspark.mapper.LessonPlanDocumentMapper;
import com.eduspark.eduspark.pojo.entity.ChatSession;
import com.eduspark.eduspark.pojo.entity.LessonPlanDocument;
import com.eduspark.eduspark.service.IChatSessionService;
import com.eduspark.eduspark.service.ICoursewareService;
import com.eduspark.eduspark.service.ILessonPlanBlueprintEnrichmentService;
import com.eduspark.eduspark.service.ILessonPlanWorkspaceService;
import com.eduspark.eduspark.service.ILessonPlanWorkspaceStreamService;
import com.eduspark.eduspark.service.ILessonPlanWriterService;
import com.eduspark.eduspark.util.LessonPlanContentUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

@Slf4j
@Service
public class LessonPlanWorkspaceServiceImpl implements ILessonPlanWorkspaceService {

    private static final int STREAM_PERSIST_STEP = 240;
    private static final long STREAM_PERSIST_INTERVAL_MS = 1200L;

    private final LessonPlanDocumentMapper lessonPlanDocumentMapper;
    private final ILessonPlanBlueprintEnrichmentService blueprintEnrichmentService;
    private final ILessonPlanWriterService lessonPlanWriterService;
    private final ICoursewareService coursewareService;
    private final IChatSessionService chatSessionService;
    private final ILessonPlanWorkspaceStreamService workspaceStreamService;
    private final ObjectMapper objectMapper;

    public LessonPlanWorkspaceServiceImpl(LessonPlanDocumentMapper lessonPlanDocumentMapper,
                                          ILessonPlanBlueprintEnrichmentService blueprintEnrichmentService,
                                          ILessonPlanWriterService lessonPlanWriterService,
                                          ICoursewareService coursewareService,
                                          IChatSessionService chatSessionService,
                                          ILessonPlanWorkspaceStreamService workspaceStreamService,
                                          ObjectMapper objectMapper) {
        this.lessonPlanDocumentMapper = lessonPlanDocumentMapper;
        this.blueprintEnrichmentService = blueprintEnrichmentService;
        this.lessonPlanWriterService = lessonPlanWriterService;
        this.coursewareService = coursewareService;
        this.chatSessionService = chatSessionService;
        this.workspaceStreamService = workspaceStreamService;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public LessonPlanWorkspaceCardData createWorkspace(ChatSession session, TeachingBlueprint blueprint) {
        LessonPlanDocument document = LessonPlanDocument.builder()
                .sessionId(session.getId())
                .userId(session.getUserId())
                .title(resolveTitle(session, blueprint))
                .status(LessonPlanDocument.Status.PREPARING)
                .summary("系统会先检索知识库并整理参考资料，再生成可编辑的教案初稿。")
                .sourceBlueprintJson(writeBlueprint(blueprint))
                .content("")
                .preview("")
                .build();
        lessonPlanDocumentMapper.insert(document);
        return toCardData(document);
    }

    @Override
    public void generateInitialDraft(Long sessionId, TeachingBlueprint blueprint) {
        LessonPlanDocument document = lessonPlanDocumentMapper.selectLatestBySessionId(sessionId);
        if (document == null) {
            log.warn("Lesson-plan workspace missing for session {}", sessionId);
            return;
        }

        try {
            updateDocumentStatus(document, LessonPlanDocument.Status.RETRIEVING, "正在检索知识库并整理教案素材...", true);
            updateDocumentStatus(document, LessonPlanDocument.Status.ENRICHING, "正在整理检索结果并注入参考资料...", true);

            TeachingBlueprint enrichedBlueprint = blueprintEnrichmentService.enrichBlueprint(document.getUserId(), blueprint);
            document.setEnrichedBlueprintJson(writeBlueprint(enrichedBlueprint));
            lessonPlanDocumentMapper.updateById(document);
            workspaceStreamService.publishSnapshot(document.getId(), toResponse(document));
            logGenerationBlueprints(sessionId, document, blueprint, enrichedBlueprint);

            LessonPlanRequest request = buildLessonPlanRequest(enrichedBlueprint);
            log.info("Lesson-plan draft request debug: sessionId={}, documentId={}, draftRequest={}",
                    sessionId, document.getId(), toDebugJson(summarizeDraftRequestForLog(request)));
            updateDocumentStatus(document, LessonPlanDocument.Status.DRAFTING, "正在调用写作模型生成教案初稿...", true);

            streamDraftToWorkspace(document, request);

            document.setDownloadUrl(null);
            document.setExportFilePath(null);
            document.setErrorMessage(null);
            updateDocumentStatus(document, LessonPlanDocument.Status.COMPLETED, "教案初稿已完成，可在右侧继续编辑、保存或导出。", false);
            chatSessionService.updateGenerationStatus(
                    sessionId,
                    ChatSession.GenerationStatus.COMPLETED,
                    "lesson_plan_workspace_ready"
            );
            saveAssistantMessage(sessionId, "教案初稿已生成完成，右侧工作区已经可以继续编辑。");
            workspaceStreamService.publishCompleted(document.getId(), toResponse(document));
        } catch (Exception e) {
            log.error("Generate lesson-plan draft failed: sessionId={}, documentId={}", sessionId, document.getId(), e);
            document.setErrorMessage(e.getMessage());
            updateDocumentStatus(document, LessonPlanDocument.Status.FAILED, "教案初稿生成失败，请稍后重试。", false);
            chatSessionService.updateGenerationStatus(
                    sessionId,
                    ChatSession.GenerationStatus.FAILED,
                    "lesson_plan_workspace_failed"
            );
            saveAssistantMessage(sessionId, "教案初稿生成失败，请稍后重试。");
            workspaceStreamService.publishFailed(document.getId(), toResponse(document));
        }
    }

    @Override
    public LessonPlanDocumentResponse getDocument(Long documentId, Long userId) {
        return toResponse(getOwnedDocument(documentId, userId));
    }

    @Override
    @Transactional
    public LessonPlanDocumentResponse updateDocumentContent(Long documentId, Long userId, String content) {
        LessonPlanDocument document = getOwnedDocument(documentId, userId);
        requireCompletedDocument(document);
        String existingContent = LessonPlanContentUtils.normalizeMarkdownContent(document.getContent());
        String normalizedIncomingContent = LessonPlanContentUtils.normalizeMarkdownContent(content);
        if (!hasText(LessonPlanContentUtils.toPlainText(normalizedIncomingContent))
                && hasText(LessonPlanContentUtils.toPlainText(existingContent))) {
            log.warn("Rejected blank lesson-plan content overwrite: documentId={}, userId={}", documentId, userId);
            throw new BusinessException(ErrorCode.PARAM_ERROR, "正文内容为空，已拦截本次保存以避免覆盖已有内容");
        }

        document.setContent(normalizedIncomingContent);
        document.setPreview(buildPreview(normalizedIncomingContent, null));
        lessonPlanDocumentMapper.updateById(document);
        LessonPlanDocumentResponse response = toResponse(document);
        workspaceStreamService.publishSnapshot(documentId, response);
        return response;
    }

    @Override
    public LessonPlanRewriteResponse rewriteSelection(Long documentId, Long userId, String selectedText, String instruction) {
        LessonPlanDocument document = getOwnedDocument(documentId, userId);
        requireCompletedDocument(document);

        String normalizedSelectedText = valueOrEmpty(selectedText).trim();
        String normalizedInstruction = valueOrEmpty(instruction).trim();
        if (!hasText(normalizedSelectedText)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "待改写内容不能为空");
        }
        if (!hasText(normalizedInstruction)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "改写要求不能为空");
        }

        String documentContent = LessonPlanContentUtils.normalizeMarkdownContent(document.getContent());
        if (!hasText(documentContent)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "当前教案内容为空，暂时无法改写");
        }

        String suggestion = coursewareService.rewriteLessonPlanFragment(
                documentContent,
                normalizedSelectedText,
                normalizedInstruction
        );
        if (!hasText(suggestion)) {
            throw new BusinessException(ErrorCode.SERVER_ERROR, "AI 未返回可用的改写结果");
        }

        return LessonPlanRewriteResponse.builder()
                .documentId(documentId)
                .selectedText(normalizedSelectedText)
                .instruction(normalizedInstruction)
                .suggestion(suggestion)
                .build();
    }

    @Override
    @Transactional
    public LessonPlanDocumentResponse exportDocument(Long documentId, Long userId) {
        LessonPlanDocument document = getOwnedDocument(documentId, userId);
        requireCompletedExportableDocument(document);
        TeachingBlueprint blueprint = readBlueprint(document.getEnrichedBlueprintJson(), document.getSourceBlueprintJson());
        LessonPlanResponse response = coursewareService.exportLessonPlanDocument(
                buildLessonPlanRequest(blueprint),
                LessonPlanContentUtils.normalizeMarkdownContent(document.getContent())
        );
        if (!response.isSuccess()) {
            throw new BusinessException(ErrorCode.SERVER_ERROR, response.getError());
        }

        document.setDownloadUrl(response.getDownloadUrl());
        document.setExportFilePath(response.getFilePath());
        document.setPreview(valueOrEmpty(response.getPreview()));
        lessonPlanDocumentMapper.updateById(document);
        LessonPlanDocumentResponse documentResponse = toResponse(document);
        workspaceStreamService.publishSnapshot(documentId, documentResponse);
        return documentResponse;
    }

    private LessonPlanDocument getOwnedDocument(Long documentId, Long userId) {
        LessonPlanDocument document = lessonPlanDocumentMapper.selectOwnedById(documentId, userId);
        if (document == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "教案工作区不存在");
        }
        return document;
    }

    private void requireCompletedDocument(LessonPlanDocument document) {
        if (!LessonPlanDocument.Status.COMPLETED.equalsIgnoreCase(document.getStatus())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "当前教案尚未生成完成，暂时不能进行 AI 改写");
        }
    }

    private void requireCompletedExportableDocument(LessonPlanDocument document) {
        if (!LessonPlanDocument.Status.COMPLETED.equalsIgnoreCase(document.getStatus())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "当前教案尚未生成完成，暂时不能进行导出");
        }
    }

    private void updateDocumentStatus(LessonPlanDocument document, String status, String summary, boolean publishImmediately) {
        document.setStatus(status);
        document.setSummary(summary);
        lessonPlanDocumentMapper.updateById(document);
        if (publishImmediately) {
            workspaceStreamService.publishStatus(document.getId(), toResponse(document));
        }
    }

    private void maybePersistDraftProgress(LessonPlanDocument document,
                                           String currentContent,
                                           int[] lastPersistLength,
                                           long[] lastPersistAt) {
        int deltaLength = currentContent.length() - lastPersistLength[0];
        long now = System.currentTimeMillis();
        if (deltaLength < STREAM_PERSIST_STEP && now - lastPersistAt[0] < STREAM_PERSIST_INTERVAL_MS) {
            return;
        }

        persistDraftContent(document, currentContent);
        lastPersistLength[0] = currentContent.length();
        lastPersistAt[0] = now;
    }

    private void streamDraftToWorkspace(LessonPlanDocument document, LessonPlanRequest request) {
        StringBuilder streamedContent = new StringBuilder();
        long[] lastPersistAt = {System.currentTimeMillis()};
        int[] lastPersistLength = {0};

        // 诊断：统计收到的 chunk 数 / 总字符 / 首块延迟，判定 deepseek 是"逐字流"还是"攒成大块一次性返回"。
        long streamStartMs = System.currentTimeMillis();
        int[] chunkCount = {0};
        long[] firstChunkAt = {0L};

        String generatedContent = lessonPlanWriterService.streamDraft(request, chunk -> {
            if (chunk == null || chunk.isEmpty()) {
                return;
            }

            chunkCount[0]++;
            if (firstChunkAt[0] == 0L) {
                firstChunkAt[0] = System.currentTimeMillis() - streamStartMs;
            }
            streamedContent.append(chunk);
            workspaceStreamService.publishContentDelta(
                    document.getId(),
                    LessonPlanStreamDeltaResponse.builder()
                            .documentId(document.getId())
                            .delta(chunk)
                            .build()
            );
            maybePersistDraftProgress(document, streamedContent.toString(), lastPersistLength, lastPersistAt);
        });

        log.info("Lesson-plan stream done: documentId={}, chunks={}, totalChars={}, firstChunkMs={}, totalCostMs={}",
                document.getId(), chunkCount[0], streamedContent.length(), firstChunkAt[0],
                System.currentTimeMillis() - streamStartMs);

        String finalContentSource = hasText(generatedContent) ? generatedContent : streamedContent.toString();
        String finalContent = LessonPlanContentUtils.normalizeMarkdownContent(finalContentSource);
        if (!hasText(LessonPlanContentUtils.toPlainText(finalContent))) {
            throw new IllegalStateException("Writer returned empty lesson-plan content");
        }

        persistDraftContent(document, finalContent);
    }

    private void persistDraftContent(LessonPlanDocument document, String content) {
        String normalizedContent = LessonPlanContentUtils.normalizeMarkdownContent(content);
        document.setContent(normalizedContent);
        document.setPreview(buildPreview(normalizedContent, null));
        lessonPlanDocumentMapper.updateById(document);
    }

    private void saveAssistantMessage(Long sessionId, String content) {
        chatSessionService.saveMessage(
                sessionId,
                "assistant",
                content,
                ChatSession.Mode.LESSON_PLAN,
                0,
                "教案工作区",
                0,
                null,
                null
        );
    }

    private LessonPlanRequest buildLessonPlanRequest(TeachingBlueprint blueprint) {
        return LessonPlanRequest.builder()
                .subject(blueprint.getCore() != null ? blueprint.getCore().getSubject() : null)
                .grade(blueprint.getCore() != null ? blueprint.getCore().getGrade() : null)
                .topic(blueprint.getCore() != null ? blueprint.getCore().getTopic() : null)
                .duration(blueprint.getCore() != null ? blueprint.getCore().getDuration() : null)
                .knowledgePoints(extractStringList(blueprint, "knowledgePoints", "知识点"))
                .teachingGoal(joinValues(extractStringList(blueprint, "teachingGoals", "教学目标")))
                .keyPoint(joinValues(extractStringList(blueprint, "keyPoints", "重点", "教学重点")))
                .difficultPoint(joinValues(extractStringList(blueprint, "difficultPoints", "难点", "教学难点")))
                .teachingMethod(firstNonBlankValue(blueprint, "teachingMethod", "教学方法"))
                .referenceText(firstNonBlankValue(blueprint, "referenceText"))
                .userDescription(buildUserDescription(blueprint))
                .build();
    }

    private String buildUserDescription(TeachingBlueprint blueprint) {
        List<String> lines = new ArrayList<>();
        appendLine(lines, "用户补充", firstNonBlankValue(blueprint, "notes", "补充说明"));
        appendLine(lines, "约束条件", joinValues(extractStringList(blueprint, "userConstraints", "约束条件")));
        appendLine(lines, "导入设计", firstNonBlankValue(blueprint, "classIntroduction"));
        appendLine(lines, "活动设计", firstNonBlankValue(blueprint, "activityDesign"));
        appendLine(lines, "评价设计", firstNonBlankValue(blueprint, "assessmentDesign"));
        appendLine(lines, "作业设计", firstNonBlankValue(blueprint, "homeworkDesign"));
        appendLine(lines, "教学资源", joinValues(extractStringList(blueprint, "teachingResources")));
        appendLine(lines, "参考文件", joinValues(extractStringList(blueprint, "attachmentNames", "参考文件")));
        return String.join("\n", lines);
    }

    private LessonPlanWorkspaceCardData toCardData(LessonPlanDocument document) {
        return LessonPlanWorkspaceCardData.builder()
                .documentId(document.getId())
                .mode(ChatSession.Mode.LESSON_PLAN)
                .modeName("教案")
                .title(document.getTitle())
                .status(document.getStatus())
                .statusText(resolveStatusText(document.getStatus()))
                .summary(document.getSummary())
                .build();
    }

    private LessonPlanDocumentResponse toResponse(LessonPlanDocument document) {
        Map<String, Object> sourceBlueprint = readBlueprintMap(document.getSourceBlueprintJson());
        Map<String, Object> enrichedBlueprint = readBlueprintMap(
                hasText(document.getEnrichedBlueprintJson()) ? document.getEnrichedBlueprintJson() : document.getSourceBlueprintJson()
        );

        return LessonPlanDocumentResponse.builder()
                .documentId(document.getId())
                .title(document.getTitle())
                .status(document.getStatus())
                .statusText(resolveStatusText(document.getStatus()))
                .summary(document.getSummary())
                .content(LessonPlanContentUtils.normalizeMarkdownContent(document.getContent()))
                .preview(valueOrEmpty(document.getPreview()))
                .downloadUrl(document.getDownloadUrl())
                .errorMessage(document.getErrorMessage())
                .sourceBlueprint(sourceBlueprint)
                .enrichedBlueprint(enrichedBlueprint)
                .knowledgeSources(extractKnowledgeSources(enrichedBlueprint))
                .enrichmentHighlights(buildEnrichmentHighlights(sourceBlueprint, enrichedBlueprint))
                .updateTime(document.getUpdateTime())
                .build();
    }

    private String resolveStatusText(String status) {
        if (!hasText(status)) {
            return "准备中";
        }

        return switch (status.toLowerCase(Locale.ROOT)) {
            case LessonPlanDocument.Status.RETRIEVING -> "检索资料中";
            case LessonPlanDocument.Status.ENRICHING -> "整理参考资料中";
            case LessonPlanDocument.Status.DRAFTING -> "撰写初稿中";
            case LessonPlanDocument.Status.COMPLETED -> "已完成";
            case LessonPlanDocument.Status.FAILED -> "生成失败";
            default -> "准备中";
        };
    }

    private String writeBlueprint(TeachingBlueprint blueprint) {
        try {
            return objectMapper.writeValueAsString(blueprint.toFlatMap());
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.SERVER_ERROR, "蓝图序列化失败");
        }
    }

    @SuppressWarnings("unchecked")
    private TeachingBlueprint readBlueprint(String preferredJson, String fallbackJson) {
        try {
            String target = hasText(preferredJson) ? preferredJson : fallbackJson;
            if (!hasText(target)) {
                return TeachingBlueprint.fromLegacyMap(Map.of());
            }
            return TeachingBlueprint.fromLegacyMap(objectMapper.readValue(target, Map.class));
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.SERVER_ERROR, "蓝图解析失败");
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readBlueprintMap(String json) {
        try {
            if (!hasText(json)) {
                return Map.of();
            }
            Map<String, Object> parsed = objectMapper.readValue(json, Map.class);
            return parsed != null ? parsed : Map.of();
        } catch (Exception e) {
            log.warn("Failed to parse lesson-plan blueprint json", e);
            return Map.of();
        }
    }

    private List<LessonPlanKnowledgeSourceItem> extractKnowledgeSources(Map<String, Object> enrichedBlueprint) {
        Object raw = enrichedBlueprint.get("knowledgeSources");
        if (!(raw instanceof Collection<?> collection) || collection.isEmpty()) {
            return List.of();
        }

        List<LessonPlanKnowledgeSourceItem> result = new ArrayList<>();
        for (Object item : collection) {
            if (!(item instanceof Map<?, ?> itemMap)) {
                continue;
            }
            result.add(LessonPlanKnowledgeSourceItem.builder()
                    .fileId(parseLong(itemMap.get("fileId")))
                    .fileName(asString(itemMap.get("fileName")))
                    .excerpt(asString(itemMap.get("excerpt")))
                    .score(parseFloat(itemMap.get("score")))
                    .build());
        }
        return result;
    }

    private List<String> buildEnrichmentHighlights(Map<String, Object> sourceBlueprint, Map<String, Object> enrichedBlueprint) {
        List<String> highlights = new ArrayList<>();

        addListHighlight(highlights, "新增知识点", sourceBlueprint, enrichedBlueprint, "knowledgePoints");
        addListHighlight(highlights, "补充教学目标", sourceBlueprint, enrichedBlueprint, "teachingGoals");
        addListHighlight(highlights, "补充教学重点", sourceBlueprint, enrichedBlueprint, "keyPoints");
        addListHighlight(highlights, "补充教学难点", sourceBlueprint, enrichedBlueprint, "difficultPoints");
        addListHighlight(highlights, "补充参考资料", sourceBlueprint, enrichedBlueprint, "attachmentNames");
        addTextHighlight(highlights, "补充教学方法", sourceBlueprint, enrichedBlueprint, "teachingMethod");
        addTextHighlight(highlights, "补充导入设计", sourceBlueprint, enrichedBlueprint, "classIntroduction");
        addTextHighlight(highlights, "补充活动设计", sourceBlueprint, enrichedBlueprint, "activityDesign");
        addTextHighlight(highlights, "补充评价设计", sourceBlueprint, enrichedBlueprint, "assessmentDesign");
        addTextHighlight(highlights, "补充作业设计", sourceBlueprint, enrichedBlueprint, "homeworkDesign");

        if (!extractKnowledgeSources(enrichedBlueprint).isEmpty()) {
            highlights.add("已结合知识库命中结果整理参考资料。");
        }

        return highlights;
    }

    private void addListHighlight(List<String> highlights,
                                  String label,
                                  Map<String, Object> sourceBlueprint,
                                  Map<String, Object> enrichedBlueprint,
                                  String key) {
        List<String> sourceValues = normalizeToStringList(sourceBlueprint.get(key));
        List<String> enrichedValues = normalizeToStringList(enrichedBlueprint.get(key));
        List<String> addedValues = enrichedValues.stream()
                .filter(value -> !sourceValues.contains(value))
                .toList();
        if (!addedValues.isEmpty()) {
            highlights.add(label + "：" + String.join("、", addedValues));
        }
    }

    private void addTextHighlight(List<String> highlights,
                                  String label,
                                  Map<String, Object> sourceBlueprint,
                                  Map<String, Object> enrichedBlueprint,
                                  String key) {
        String sourceValue = asString(sourceBlueprint.get(key));
        String enrichedValue = asString(enrichedBlueprint.get(key));
        if (!hasText(enrichedValue) || enrichedValue.equals(sourceValue)) {
            return;
        }
        highlights.add(label + "：" + truncate(enrichedValue, 80));
    }

    private String resolveTitle(ChatSession session, TeachingBlueprint blueprint) {
        String topic = blueprint.getCore() != null ? blueprint.getCore().getTopic() : null;
        if (hasText(topic)) {
            return topic;
        }
        return hasText(session.getTitle()) ? session.getTitle() : "教案初稿";
    }

    private List<String> extractStringList(TeachingBlueprint blueprint, String... keys) {
        for (String key : keys) {
            List<String> values = normalizeToStringList(blueprint.getExtension(key));
            if (!values.isEmpty()) {
                return values;
            }
        }
        return List.of();
    }

    private List<String> normalizeToStringList(Object raw) {
        if (raw == null) {
            return List.of();
        }
        if (raw instanceof Collection<?> collection) {
            return collection.stream()
                    .map(this::asString)
                    .filter(this::hasText)
                    .distinct()
                    .toList();
        }

        String value = asString(raw);
        if (!hasText(value)) {
            return List.of();
        }

        return Stream.of(value.split("[,，、\n]"))
                .map(String::trim)
                .filter(this::hasText)
                .distinct()
                .toList();
    }

    private String firstNonBlankValue(TeachingBlueprint blueprint, String... keys) {
        for (String key : keys) {
            String value = asString(blueprint.getExtension(key));
            if (hasText(value)) {
                return value;
            }
        }
        return "";
    }

    private void appendLine(List<String> lines, String label, String value) {
        if (!hasText(value)) {
            return;
        }
        lines.add(label + "：" + value);
    }

    private String joinValues(List<String> values) {
        return values == null || values.isEmpty() ? "" : String.join("、", values);
    }

    private String buildPreview(String content, String fallbackPreview) {
        String source = hasText(content)
                ? LessonPlanContentUtils.toPlainText(content)
                : LessonPlanContentUtils.toPlainText(fallbackPreview);
        if (!hasText(source)) {
            return "";
        }
        return truncate(source, 240);
    }

    private void logGenerationBlueprints(Long sessionId,
                                         LessonPlanDocument document,
                                         TeachingBlueprint sourceBlueprint,
                                         TeachingBlueprint enrichedBlueprint) {
        Map<String, Object> sourceMap = sourceBlueprint != null ? sourceBlueprint.toFlatMap() : Map.of();
        Map<String, Object> enrichedMap = enrichedBlueprint != null ? enrichedBlueprint.toFlatMap() : Map.of();
        List<LessonPlanKnowledgeSourceItem> knowledgeSources = extractKnowledgeSources(enrichedMap);

        log.info("Lesson-plan generation debug: sessionId={}, documentId={}, sourceBlueprint={}, enrichedBlueprint={}, knowledgeSources={}",
                sessionId,
                document.getId(),
                toDebugJson(summarizeBlueprintForLog(sourceMap)),
                toDebugJson(summarizeBlueprintForLog(enrichedMap)),
                toDebugJson(summarizeKnowledgeSourcesForLog(knowledgeSources)));
    }

    private Map<String, Object> summarizeBlueprintForLog(Map<String, Object> blueprint) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("subject", asString(blueprint.get("subject")));
        summary.put("grade", asString(blueprint.get("grade")));
        summary.put("topic", asString(blueprint.get("topic")));
        summary.put("duration", blueprint.get("duration"));
        summary.put("knowledgePointCount", normalizeToStringList(blueprint.get("knowledgePoints")).size());
        summary.put("teachingGoalCount", normalizeToStringList(blueprint.get("teachingGoals")).size());
        summary.put("keyPointCount", normalizeToStringList(blueprint.get("keyPoints")).size());
        summary.put("difficultPointCount", normalizeToStringList(blueprint.get("difficultPoints")).size());
        summary.put("attachmentCount", normalizeToStringList(blueprint.get("attachmentNames")).size());
        summary.put("knowledgeSourceCount", extractKnowledgeSources(blueprint).size());
        summary.put("referenceTextLength", asString(blueprint.get("referenceText")).length());
        summary.put("notesLength", asString(blueprint.get("notes")).length());
        return summary;
    }

    private Map<String, Object> summarizeDraftRequestForLog(LessonPlanRequest request) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("subject", valueOrEmpty(request.getSubject()));
        summary.put("grade", valueOrEmpty(request.getGrade()));
        summary.put("topic", valueOrEmpty(request.getTopic()));
        summary.put("duration", request.getDuration());
        summary.put("knowledgePointCount", request.getKnowledgePoints() == null ? 0 : request.getKnowledgePoints().size());
        summary.put("teachingGoalLength", valueOrEmpty(request.getTeachingGoal()).length());
        summary.put("keyPointLength", valueOrEmpty(request.getKeyPoint()).length());
        summary.put("difficultPointLength", valueOrEmpty(request.getDifficultPoint()).length());
        summary.put("teachingMethodLength", valueOrEmpty(request.getTeachingMethod()).length());
        summary.put("referenceTextLength", valueOrEmpty(request.getReferenceText()).length());
        summary.put("userDescriptionLength", valueOrEmpty(request.getUserDescription()).length());
        summary.put("teacherName", valueOrEmpty(request.getTeacherName()));
        return summary;
    }

    private List<Map<String, Object>> summarizeKnowledgeSourcesForLog(List<LessonPlanKnowledgeSourceItem> knowledgeSources) {
        List<Map<String, Object>> summary = new ArrayList<>();
        for (LessonPlanKnowledgeSourceItem item : knowledgeSources) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("fileId", item.getFileId());
            row.put("fileName", valueOrEmpty(item.getFileName()));
            row.put("score", item.getScore());
            summary.add(row);
        }
        return summary;
    }

    private String toDebugJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return String.valueOf(value);
        }
    }

    private String truncate(String value, int maxLength) {
        if (!hasText(value) || value.length() <= maxLength) {
            return valueOrEmpty(value);
        }
        return value.substring(0, maxLength);
    }

    private String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }

    private String asString(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private Long parseLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value).trim());
        } catch (Exception e) {
            return null;
        }
    }

    private Float parseFloat(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.floatValue();
        }
        try {
            return Float.parseFloat(String.valueOf(value).trim());
        } catch (Exception e) {
            return null;
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
