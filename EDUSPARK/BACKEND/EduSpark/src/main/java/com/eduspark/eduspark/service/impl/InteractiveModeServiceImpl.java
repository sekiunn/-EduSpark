package com.eduspark.eduspark.service.impl;

import com.eduspark.eduspark.dto.interactive.InteractiveContext;
import com.eduspark.eduspark.dto.interactive.InteractiveModeResult;
import com.eduspark.eduspark.dto.interactive.InteractiveStageCardData;
import com.eduspark.eduspark.mapper.InteractiveDocumentMapper;
import com.eduspark.eduspark.pojo.entity.ChatSession;
import com.eduspark.eduspark.pojo.entity.InteractiveDocument;
import com.eduspark.eduspark.service.IChatSessionService;
import com.eduspark.eduspark.service.IInteractiveModeService;
import com.eduspark.eduspark.service.IInteractiveWorkspaceService;
import com.eduspark.eduspark.service.ILLMService;
import com.eduspark.eduspark.service.ITextExtractorService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@Slf4j
@Service
public class InteractiveModeServiceImpl implements IInteractiveModeService {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };
    private static final int MAX_REFERENCE_TEXT = 12000;
    private static final int MAX_EXTRACTION_MESSAGE_LENGTH = 4000;
    private static final List<String> SUBJECT_CANDIDATES = List.of(
            "语文", "数学", "英语", "物理", "化学", "生物", "历史", "地理",
            "道德与法治", "思想政治", "科学", "信息技术", "信息科技", "美术", "音乐", "体育"
    );

    private final ILLMService llmService;
    private final ITextExtractorService textExtractorService;
    private final IChatSessionService chatSessionService;
    private final IInteractiveWorkspaceService interactiveWorkspaceService;
    private final InteractiveDocumentMapper interactiveDocumentMapper;
    private final ObjectMapper objectMapper;

    @Value("${lesson-plan.writer.external.model:qwen3.6-plus}")
    private String extractModel;

    public InteractiveModeServiceImpl(ILLMService llmService,
                                      ITextExtractorService textExtractorService,
                                      IChatSessionService chatSessionService,
                                      IInteractiveWorkspaceService interactiveWorkspaceService,
                                      InteractiveDocumentMapper interactiveDocumentMapper,
                                      ObjectMapper objectMapper) {
        this.llmService = llmService;
        this.textExtractorService = textExtractorService;
        this.chatSessionService = chatSessionService;
        this.interactiveWorkspaceService = interactiveWorkspaceService;
        this.interactiveDocumentMapper = interactiveDocumentMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public InteractiveModeResult handleMessage(ChatSession session, String userMessage, MultipartFile[] attachments) {
        InteractiveContext context = loadContext(session);
        mergeAttachmentContext(context, attachments);
        mergeFields(context, extractFields(context, userMessage));

        InteractiveDocument latestDocument = interactiveDocumentMapper.selectLatestBySessionId(session.getId());
        if (latestDocument != null && isInProgress(latestDocument.getStatus())) {
            saveContext(session, ChatSession.Stage.GENERATING, context);
            return withCard(
                    "已记录这次补充，但当前不会自动续改。请等右侧工作区生成完成后，再发一次修改指令。",
                    toCardData(latestDocument)
            );
        }

        if (hasCompletedDocument(latestDocument)) {
            context.setCurrentVersion(resolveNextVersion(context));
            saveContext(session, ChatSession.Stage.GENERATING, context);
            chatSessionService.updateGenerationStatus(
                    session.getId(),
                    ChatSession.GenerationStatus.PROCESSING,
                    "interactive_workspace_refining"
            );
            interactiveWorkspaceService.refineDocumentAsync(
                    session.getId(),
                    latestDocument.getId(),
                    context,
                    normalizeMessage(userMessage)
            );
            return withCard(
                    "我会基于当前页面继续调整，右侧工作区会自动刷新运行效果。",
                    InteractiveStageCardData.builder()
                            .documentId(latestDocument.getId())
                            .mode(ChatSession.Mode.INTERACTIVE)
                            .modeName("互动内容")
                            .title(latestDocument.getTitle())
                            .status(InteractiveDocument.Status.REFINING)
                            .statusText("调整中")
                            .summary("正在根据最新要求重做当前互动页面。")
                            .build()
            );
        }

        if (!isReadyToImplement(context)) {
            saveContext(session, ChatSession.Stage.CLARIFYING, context);
            return InteractiveModeResult.builder()
                    .message(buildFollowUpMessage(context))
                    .build();
        }

        context.setCurrentVersion(1);
        InteractiveStageCardData workspaceCard = interactiveWorkspaceService.createWorkspace(session, context);
        saveContext(session, ChatSession.Stage.GENERATING, context);
        chatSessionService.updateGenerationStatus(
                session.getId(),
                ChatSession.GenerationStatus.PROCESSING,
                "interactive_workspace_generating"
        );
        interactiveWorkspaceService.generateInitialDocumentAsync(session.getId(), workspaceCard.getDocumentId(), context);
        return withCard(
                "我会把这个教学想法直接实现成一个可运行的互动页面，右侧工作区会优先展示运行效果。",
                workspaceCard
        );
    }

    private InteractiveModeResult withCard(String message, InteractiveStageCardData cardData) {
        return InteractiveModeResult.builder()
                .message(message)
                .cardType("interactive_stage_entry")
                .cardData(cardData)
                .build();
    }

    private InteractiveContext loadContext(ChatSession session) {
        if (session.getTeachingElements() == null || session.getTeachingElements().isBlank()) {
            return InteractiveContext.empty();
        }
        try {
            return InteractiveContext.fromMap(objectMapper.readValue(session.getTeachingElements(), MAP_TYPE));
        } catch (Exception e) {
            log.warn("Parse interactive context failed: sessionId={}", session.getId(), e);
            return InteractiveContext.empty();
        }
    }

    private void saveContext(ChatSession session, String stage, InteractiveContext context) {
        try {
            chatSessionService.updateTeachingMode(
                    session.getId(),
                    ChatSession.Mode.INTERACTIVE,
                    stage,
                    objectMapper.writeValueAsString(context.toMap())
            );
        } catch (Exception e) {
            log.error("Save interactive context failed: sessionId={}", session.getId(), e);
        }
    }

    private Map<String, Object> extractFields(InteractiveContext context, String userMessage) {
        Map<String, Object> fallback = extractFallbackFields(userMessage);
        try {
            String prompt = buildExtractionPrompt(context, userMessage);
            String raw = llmService.chatWithModel(List.of(new ILLMService.ChatMessage("user", prompt)), extractModel);
            return backfillMissingFields(parseJsonMap(raw), fallback);
        } catch (Exception e) {
            log.warn("Extract interactive fields failed", e);
            return fallback;
        }
    }

    private String buildExtractionPrompt(InteractiveContext context, String userMessage) {
        return """
                你是互动教学需求信息提取器。
                只输出 JSON 对象，不要解释，不要 Markdown。
                规则：
                1. 只提取用户明确表达或高度可确定的信息。
                2. 如果用户是在修改已有想法，输出最新值。
                3. mustHaveInteractions、constraints 尽量输出数组。
                4. 未提及字段输出 null。

                当前上下文：
                %s

                用户最新消息：
                %s

                输出格式：
                {
                  "subject": null,
                  "grade": null,
                  "topic": null,
                  "scene": null,
                  "interactionType": null,
                  "pageGoal": null,
                  "studentAction": null,
                  "mustHaveInteractions": null,
                  "teachingFocus": null,
                  "styleHint": null,
                  "constraints": null,
                  "notes": null
                }
                """.formatted(
                toJson(context.toMap()),
                truncate(userMessage, MAX_EXTRACTION_MESSAGE_LENGTH)
        );
    }

    private Map<String, Object> extractFallbackFields(String userMessage) {
        Map<String, Object> fields = new LinkedHashMap<>();
        if (!hasText(userMessage)) {
            return fields;
        }

        String subject = extractSubjectCandidate(userMessage);
        if (subject != null) {
            fields.put("subject", subject);
        }

        String grade = extractGradeCandidate(userMessage);
        if (grade != null) {
            fields.put("grade", grade);
        }

        String interactionType = detectInteractionType(userMessage);
        if (interactionType != null) {
            fields.put("interactionType", interactionType);
        }

        return fields;
    }

    private Map<String, Object> parseJsonMap(String raw) throws Exception {
        if (!hasText(raw)) {
            return Map.of();
        }
        String trimmed = raw.trim();
        if (trimmed.startsWith("```")) {
            trimmed = trimmed.replaceFirst("^```[a-zA-Z]*", "").replaceFirst("```$", "").trim();
        }
        try {
            return objectMapper.readValue(trimmed, MAP_TYPE);
        } catch (Exception ignored) {
            Matcher matcher = Pattern.compile("\\{[\\s\\S]*}", Pattern.MULTILINE).matcher(trimmed);
            if (matcher.find()) {
                return objectMapper.readValue(matcher.group(), MAP_TYPE);
            }
            throw ignored;
        }
    }

    private Map<String, Object> backfillMissingFields(Map<String, Object> primary, Map<String, Object> fallback) {
        Map<String, Object> merged = new LinkedHashMap<>();
        if (primary != null) {
            merged.putAll(primary);
        }
        if (fallback == null || fallback.isEmpty()) {
            return merged;
        }
        fallback.forEach((key, value) -> {
            if (!hasMeaningfulValue(merged.get(key))) {
                merged.put(key, value);
            }
        });
        return merged;
    }

    private boolean hasMeaningfulValue(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof String text) {
            return hasText(text);
        }
        if (value instanceof Collection<?> collection) {
            return !collection.isEmpty();
        }
        return true;
    }

    private void mergeFields(InteractiveContext context, Map<String, Object> fields) {
        putText(fields, "subject", context::setSubject);
        putText(fields, "grade", context::setGrade);
        putText(fields, "topic", context::setTopic);
        putText(fields, "scene", context::setScene);
        putText(fields, "interactionType", context::setInteractionType);
        putText(fields, "pageGoal", context::setPageGoal);
        putText(fields, "studentAction", context::setStudentAction);
        putText(fields, "teachingFocus", context::setTeachingFocus);
        putText(fields, "styleHint", context::setStyleHint);
        putText(fields, "notes", context::setNotes);

        List<String> mustHaveInteractions = normalizeList(fields.get("mustHaveInteractions"));
        if (!mustHaveInteractions.isEmpty()) {
            context.setMustHaveInteractions(mustHaveInteractions);
        }

        List<String> constraints = normalizeList(fields.get("constraints"));
        if (!constraints.isEmpty()) {
            context.setConstraints(constraints);
        }
    }

    private boolean isReadyToImplement(InteractiveContext context) {
        boolean hasCore = hasText(context.getSubject()) && hasText(context.getGrade()) && hasText(context.getTopic());
        boolean hasImplementationHint = hasText(context.getInteractionType())
                || hasText(context.getStudentAction())
                || hasText(context.getPageGoal())
                || !context.safeMustHaveInteractions().isEmpty();
        return hasCore && hasImplementationHint;
    }

    private String buildFollowUpMessage(InteractiveContext context) {
        if (!hasText(context.getTopic())) {
            return "你想围绕哪个知识点或主题来做这个互动页面？";
        }
        if (!hasText(context.getGrade())) {
            return "这个互动页面主要面向哪个学段或年级？";
        }
        if (!hasText(context.getSubject())) {
            return "这是哪个学科的内容？";
        }
        if (!hasText(context.getInteractionType())
                && !hasText(context.getStudentAction())
                && !hasText(context.getPageGoal())
                && context.safeMustHaveInteractions().isEmpty()) {
            return "你更希望它是讲解型、练习型、演示型，还是游戏型？学生在页面里具体要做什么？";
        }
        if (!hasText(context.getScene())) {
            return "这个页面更偏课堂引入、知识讲解、课堂练习，还是课后巩固？";
        }
        return "如果你还有交互步骤、视觉风格或反馈方式上的偏好，也可以继续补充，我会直接开始实现。";
    }

    private boolean hasCompletedDocument(InteractiveDocument latestDocument) {
        return latestDocument != null
                && InteractiveDocument.Status.COMPLETED.equalsIgnoreCase(latestDocument.getStatus())
                && hasText(latestDocument.getHtmlContent());
    }

    private boolean isInProgress(String status) {
        return InteractiveDocument.Status.PREPARING.equalsIgnoreCase(status)
                || InteractiveDocument.Status.IMPLEMENTING.equalsIgnoreCase(status)
                || InteractiveDocument.Status.REFINING.equalsIgnoreCase(status);
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

    private int resolveNextVersion(InteractiveContext context) {
        Integer currentVersion = context.getCurrentVersion();
        if (currentVersion == null || currentVersion <= 0) {
            return 1;
        }
        return currentVersion + 1;
    }

    private void mergeAttachmentContext(InteractiveContext context, MultipartFile[] attachments) {
        if (attachments == null || attachments.length == 0) {
            return;
        }

        StringBuilder referenceText = new StringBuilder();
        if (hasText(context.getReferenceText())) {
            referenceText.append(context.getReferenceText()).append("\n\n");
        }

        LinkedHashSet<String> attachmentNames = new LinkedHashSet<>(context.safeAttachmentNames());
        for (MultipartFile attachment : attachments) {
            if (attachment == null || attachment.isEmpty()) {
                continue;
            }
            String fileName = attachment.getOriginalFilename() == null ? "未命名附件" : attachment.getOriginalFilename();
            attachmentNames.add(fileName);
            try {
                String text = textExtractorService.extractText(attachment);
                if (!hasText(text)) {
                    continue;
                }
                if (referenceText.length() > 0) {
                    referenceText.append("\n\n");
                }
                referenceText.append("【附件】")
                        .append(fileName)
                        .append('\n')
                        .append(truncate(text, 4000));
            } catch (Exception e) {
                log.warn("Extract interactive attachment text failed: {}", fileName, e);
            }
        }

        if (referenceText.length() > 0) {
            context.setReferenceText(truncate(referenceText.toString(), MAX_REFERENCE_TEXT));
        }
        if (!attachmentNames.isEmpty()) {
            context.setAttachmentNames(new ArrayList<>(attachmentNames));
        }
    }

    private void putText(Map<String, Object> fields, String key, java.util.function.Consumer<String> setter) {
        String value = normalizeText(fields.get(key));
        if (value != null) {
            setter.accept(value);
        }
    }

    private String normalizeMessage(String userMessage) {
        return userMessage == null ? "" : userMessage.trim();
    }

    private String normalizeText(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() || "null".equalsIgnoreCase(text) ? null : text;
    }

    private List<String> normalizeList(Object raw) {
        if (raw == null) {
            return List.of();
        }
        if (raw instanceof Collection<?> collection) {
            return collection.stream()
                    .map(this::normalizeText)
                    .filter(Objects::nonNull)
                    .distinct()
                    .toList();
        }
        String text = normalizeText(raw);
        if (text == null) {
            return List.of();
        }
        return Stream.of(text.split("[,，。；;\\n]"))
                .map(String::trim)
                .filter(part -> !part.isBlank())
                .distinct()
                .toList();
    }

    private String extractSubjectCandidate(String message) {
        if (!hasText(message)) {
            return null;
        }
        String normalized = message.toLowerCase(Locale.ROOT);
        return SUBJECT_CANDIDATES.stream()
                .filter(subject -> normalized.contains(subject.toLowerCase(Locale.ROOT)))
                .findFirst()
                .orElse(null);
    }

    private String extractGradeCandidate(String message) {
        if (!hasText(message)) {
            return null;
        }
        List<Pattern> patterns = List.of(
                Pattern.compile("(幼儿园(?:大班|中班|小班))"),
                Pattern.compile("((?:小学|初中|高中)?[一二三四五六七八九十1-9]+年级)"),
                Pattern.compile("((?:初|高)[一二三1-3])")
        );
        for (Pattern pattern : patterns) {
            Matcher matcher = pattern.matcher(message);
            if (matcher.find()) {
                return matcher.group(1);
            }
        }
        return null;
    }

    private String detectInteractionType(String message) {
        if (!hasText(message)) {
            return null;
        }
        String normalized = message.toLowerCase(Locale.ROOT);
        if (normalized.contains("拖拽") || normalized.contains("配对")) {
            return "拖拽练习";
        }
        if (normalized.contains("点击") || normalized.contains("探索")) {
            return "点击探索";
        }
        if (normalized.contains("动画") || normalized.contains("演示") || normalized.contains("模拟")) {
            return "演示模拟";
        }
        if (normalized.contains("游戏") || normalized.contains("闯关")) {
            return "课堂小游戏";
        }
        if (normalized.contains("练习") || normalized.contains("题")) {
            return "交互练习";
        }
        return null;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return "{}";
        }
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength) + "...";
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
