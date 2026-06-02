package com.eduspark.eduspark.service.impl;

import com.eduspark.eduspark.dto.chat.BlueprintCardData;
import com.eduspark.eduspark.dto.chat.ClarificationResult;
import com.eduspark.eduspark.dto.chat.GenerationCardData;
import com.eduspark.eduspark.dto.chat.TeachingBlueprint;
import com.eduspark.eduspark.dto.chat.TeachingCore;
import com.eduspark.eduspark.dto.courseware.LessonPlanRequest;
import com.eduspark.eduspark.dto.courseware.LessonPlanResponse;
import com.eduspark.eduspark.dto.courseware.PptGenerateRequest;
import com.eduspark.eduspark.dto.courseware.PptGenerateResponse;
import com.eduspark.eduspark.event.GenerationRequestedEvent;
import com.eduspark.eduspark.pojo.entity.ChatSession;
import com.eduspark.eduspark.service.IChatSessionService;
import com.eduspark.eduspark.service.IClarificationService;
import com.eduspark.eduspark.service.ICoursewareService;
import com.eduspark.eduspark.service.ILLMService;
import com.eduspark.eduspark.service.ILessonPlanWorkspaceService;
import com.eduspark.eduspark.service.IPptWorkspaceService;
import com.eduspark.eduspark.service.ITextExtractorService;
import com.eduspark.eduspark.service.teaching.TeachingStageMachine;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@Slf4j
@Service
public class ClarificationServiceImpl implements IClarificationService {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };
    private static final String DEFAULT_EXTRACT_MODEL = "qwen3.6-plus";
    private static final String PROCESSING_MESSAGE = "正在生成中，请稍候。";
    private static final String GENERATION_PENDING_MESSAGE = "已开始生成，完成后结果会自动追加到当前对话。";
    private static final int MAX_REFERENCE_TEXT = 12_000;

    private static final Set<String> HIGH_CONFIDENCE_SUBJECTS = Set.of(
            "语文", "数学", "英语", "物理", "化学", "生物", "历史", "地理", "政治", "科学",
            "信息技术", "信息科技", "道德与法治", "编程", "美术", "音乐", "体育", "心理健康", "劳动"
    );

    private static final List<String> CONFIRM_WORDS = List.of(
            "确认", "确认生成", "开始生成", "就这样", "没问题", "可以生成", "可以开始", "直接生成"
    );

    private static final List<String> NEGATIVE_CONFIRM_WORDS = List.of(
            "不要生成", "先别生成", "暂不生成", "不确认"
    );

    private static final List<String> STYLE_HINTS = List.of(
            "简洁课堂", "科技蓝", "清新自然", "活力童趣", "商务", "学术", "极简", "卡通", "未来感", "清新"
    );

    private static final Pattern GRADE_PATTERN = Pattern.compile(
            "(小学[一二三四五六]年级|初中[一二三]年级|高中[一二三]年级|[一二三四五六七八九]年级|高一|高二|高三|初一|初二|初三|大一|大二|大三|大四)"
    );
    private static final Pattern DURATION_PATTERN = Pattern.compile("(\\d{1,3})\\s*(分钟|min|mins)");
    private static final Pattern SLIDE_COUNT_PATTERN = Pattern.compile("(\\d{1,2})\\s*(页|张|p|P)");

    private final ILLMService llmService;
    private final IChatSessionService chatSessionService;
    private final ITextExtractorService textExtractorService;
    private final ICoursewareService coursewareService;
    private final ILessonPlanWorkspaceService lessonPlanWorkspaceService;
    private final IPptWorkspaceService pptWorkspaceService;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;
    private final TeachingStageMachine teachingStageMachine;
    private final boolean workspaceEntryEnabled;

    @Value("${lesson-plan.writer.external.model:qwen3.6-plus}")
    private String extractModel = DEFAULT_EXTRACT_MODEL;

    @Autowired
    public ClarificationServiceImpl(ILLMService llmService,
                                    IChatSessionService chatSessionService,
                                    ITextExtractorService textExtractorService,
                                    ICoursewareService coursewareService,
                                    ILessonPlanWorkspaceService lessonPlanWorkspaceService,
                                    IPptWorkspaceService pptWorkspaceService,
                                    ApplicationEventPublisher eventPublisher,
                                    ObjectMapper objectMapper,
                                    TeachingStageMachine teachingStageMachine) {
        this(
                llmService,
                chatSessionService,
                textExtractorService,
                coursewareService,
                lessonPlanWorkspaceService,
                pptWorkspaceService,
                eventPublisher,
                objectMapper,
                teachingStageMachine,
                true
        );
    }

    public ClarificationServiceImpl(ILLMService llmService,
                                    IChatSessionService chatSessionService,
                                    ITextExtractorService textExtractorService,
                                    ICoursewareService coursewareService,
                                    ILessonPlanWorkspaceService lessonPlanWorkspaceService,
                                    ApplicationEventPublisher eventPublisher,
                                    ObjectMapper objectMapper,
                                    TeachingStageMachine teachingStageMachine) {
        this(
                llmService,
                chatSessionService,
                textExtractorService,
                coursewareService,
                lessonPlanWorkspaceService,
                null,
                eventPublisher,
                objectMapper,
                teachingStageMachine,
                false
        );
    }

    private ClarificationServiceImpl(ILLMService llmService,
                                     IChatSessionService chatSessionService,
                                     ITextExtractorService textExtractorService,
                                     ICoursewareService coursewareService,
                                     ILessonPlanWorkspaceService lessonPlanWorkspaceService,
                                     IPptWorkspaceService pptWorkspaceService,
                                     ApplicationEventPublisher eventPublisher,
                                     ObjectMapper objectMapper,
                                     TeachingStageMachine teachingStageMachine,
                                     boolean workspaceEntryEnabled) {
        this.llmService = llmService;
        this.chatSessionService = chatSessionService;
        this.textExtractorService = textExtractorService;
        this.coursewareService = coursewareService;
        this.lessonPlanWorkspaceService = lessonPlanWorkspaceService;
        this.pptWorkspaceService = pptWorkspaceService;
        this.eventPublisher = eventPublisher;
        this.objectMapper = objectMapper;
        this.teachingStageMachine = teachingStageMachine;
        this.workspaceEntryEnabled = workspaceEntryEnabled;
    }

    @Override
    public ClarificationResult startClarification(ChatSession session,
                                                  String userMessage,
                                                  MultipartFile[] attachments,
                                                  String templateId) {
        TeachingBlueprint blueprint = loadBlueprint(session);
        applyTemplateId(blueprint, templateId);
        mergeAttachmentContext(blueprint, attachments);
        return handleTurn(session, blueprint, userMessage);
    }

    @Override
    public ClarificationResult continueClarification(ChatSession session, String userAnswer, String templateId) {
        TeachingBlueprint blueprint = loadBlueprint(session);
        applyTemplateId(blueprint, templateId);
        return handleTurn(session, blueprint, userAnswer);
    }

    @Override
    public ClarificationResult fastPathForPpt(ChatSession session,
                                              String userMessage,
                                              MultipartFile[] attachments,
                                              String templateId) {
        TeachingBlueprint blueprint = loadBlueprint(session);
        applyTemplateId(blueprint, templateId);
        mergeAttachmentContext(blueprint, attachments);

        String normalizedMessage = normalizeString(userMessage);
        if (hasText(normalizedMessage)) {
            // 快速直达只用启发式提取——避免 mergeMessageIntoBlueprint 里的 LLM 同步调用
            // 拖慢 HTTP 响应。真正的 LLM 内容生成发生在异步 reveal 阶段。
            ensureCore(blueprint);
            Map<String, Object> patch = extractPatchHeuristically(normalizedMessage, ChatSession.Mode.PPT);
            applyPatchToBlueprint(blueprint, patch);
            blueprint.putExtension("notes", truncate(
                    mergeText(asString(blueprint.getExtension("notes")), normalizedMessage), 2_000));
            TeachingCore core = ensureCore(blueprint);
            if (!hasText(core.getTopic())) {
                String fallbackTopic = extractTopic(normalizedMessage);
                if (hasText(fallbackTopic)) {
                    core.setTopic(fallbackTopic);
                }
            }
        }

        // 兜底必备字段：PPT 模式只需 subject/grade/topic 齐全即可触发生成。
        // 缺什么补什么，让用户不必再补一轮澄清。
        fillFastPathDefaults(blueprint, normalizedMessage);

        ClarificationResult result = confirmAndGenerate(session, blueprint, ChatSession.Mode.PPT);

        // 把"先检索知识库、再生成方案"的冗长解释压成一句简洁回执，
        // 真实进度让右侧工作区的 SSE 流来呈现。slideCount 落在 extension 里。
        Integer slideCount = blueprint.getExtension("slideCount") instanceof Number num
                ? num.intValue()
                : null;
        String concise = slideCount != null
                ? "✓ 已收到，正在结合知识库为您生成 " + slideCount + " 页 PPT，可在右侧工作区查看进度。"
                : "✓ 已收到，正在结合知识库生成 PPT，可在右侧工作区查看进度。";
        result.setMessage(concise);
        return result;
    }

    @Override
    public ClarificationResult confirmAndGenerate(ChatSession session) {
        return confirmAndGenerate(session, loadBlueprint(session), normalizeMode(session.getMode()));
    }

    private void fillFastPathDefaults(TeachingBlueprint blueprint, String userMessage) {
        TeachingCore core = ensureCore(blueprint);
        if (!hasText(core.getTopic())) {
            core.setTopic(hasText(userMessage) ? truncate(userMessage, 60) : "课件初稿");
        }
        if (!hasText(core.getSubject())) {
            core.setSubject("通用");
        }
        if (!hasText(core.getGrade())) {
            core.setGrade("全学段");
        }
    }

    @Override
    public void doAsyncGenerate(Long sessionId, String mode, Map<String, Object> blueprintMap) {
        TeachingBlueprint blueprint = TeachingBlueprint.fromLegacyMap(blueprintMap);
        String normalizedMode = normalizeMode(mode);

        try {
            if (ChatSession.Mode.LESSON_PLAN.equals(normalizedMode) && lessonPlanWorkspaceService != null) {
                lessonPlanWorkspaceService.generateInitialDraft(sessionId, blueprint);
                updateStage(sessionId, teachingStageMachine.stageAfterGeneration(true), blueprint);
                return;
            }
            if (ChatSession.Mode.PPT.equals(normalizedMode) && pptWorkspaceService != null) {
                pptWorkspaceService.generateInitialDocument(sessionId, blueprint);
                updateStage(sessionId, teachingStageMachine.stageAfterGeneration(true), blueprint);
                return;
            }

            GenerationOutcome outcome = switch (normalizedMode) {
                // 暂时关闭（PPT 链路收口）：PPT 已统一改走工作区生成
                // （上方 pptWorkspaceService != null 时已 return）。这里是旧的直接生成兜底，
                // 现停用以保证「只留工作区一个入口」。如需恢复，换回 generatePpt(blueprint)。
                case ChatSession.Mode.PPT -> throw new IllegalStateException(
                        "PPT 已统一改走工作区生成，旧的直接生成入口已关闭");
                case ChatSession.Mode.INTERACTIVE -> generateInteractive(blueprint);
                default -> generateLessonPlan(blueprint);
            };

            chatSessionService.updateGenerationStatus(
                    sessionId,
                    ChatSession.GenerationStatus.COMPLETED,
                    outcome.resultText()
            );
            updateStage(sessionId, teachingStageMachine.stageAfterGeneration(true), blueprint);
            saveAssistantCardMessage(sessionId, normalizedMode, outcome.message(), "generation_complete", outcome.cardData());
        } catch (Exception e) {
            log.error("Async generation failed: sessionId={}, mode={}", sessionId, normalizedMode, e);
            chatSessionService.updateGenerationStatus(
                    sessionId,
                    ChatSession.GenerationStatus.FAILED,
                    "生成失败：" + e.getMessage()
            );
            updateStage(sessionId, teachingStageMachine.stageAfterGeneration(false), blueprint);
            saveAssistantCardMessage(
                    sessionId,
                    normalizedMode,
                    "生成失败：" + e.getMessage(),
                    "generation_complete",
                    buildFailedCard(blueprint, normalizedMode, e.getMessage())
            );
        }
    }

    @Override
    public String getBlueprintJson(ChatSession session) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(loadBlueprint(session).toFlatMap());
        } catch (Exception e) {
            log.error("Serialize blueprint failed: sessionId={}", session.getId(), e);
            return "{}";
        }
    }

    private ClarificationResult handleTurn(ChatSession session, TeachingBlueprint blueprint, String userMessage) {
        String mode = normalizeMode(session.getMode());
        String normalizedMessage = normalizeString(userMessage);
        boolean readyBefore = isReadyForConfirmation(blueprint, mode);

        if (shouldMergeMessage(normalizedMessage, readyBefore)) {
            mergeMessageIntoBlueprint(blueprint, normalizedMessage, mode);
        }

        boolean readyAfter = isReadyForConfirmation(blueprint, mode);
        String nextStage = teachingStageMachine.stageAfterClarification(readyAfter);

        if (isNegativeConfirm(normalizedMessage)) {
            saveBlueprint(session, nextStage, blueprint);
            if (readyAfter) {
                return ClarificationResult.withCard(
                        buildReadyMessage(mode),
                        "blueprint_confirm",
                        buildBlueprintCard(blueprint, mode)
                );
            }
            return ClarificationResult.of(buildFollowUpMessage(blueprint, mode));
        }

        if (isConfirmIntent(normalizedMessage) && readyAfter) {
            return confirmAndGenerate(session, blueprint, mode);
        }

        saveBlueprint(session, nextStage, blueprint);

        if (readyAfter) {
            return ClarificationResult.withCard(
                    buildReadyMessage(mode),
                    "blueprint_confirm",
                    buildBlueprintCard(blueprint, mode)
            );
        }

        return ClarificationResult.of(buildFollowUpMessage(blueprint, mode));
    }

    private ClarificationResult confirmAndGenerate(ChatSession session, TeachingBlueprint blueprint, String mode) {
        boolean readyForConfirmation = isReadyForConfirmation(blueprint, mode);
        String nextStage = teachingStageMachine.stageAfterConfirmAttempt(readyForConfirmation);

        if (!readyForConfirmation) {
            saveBlueprint(session, nextStage, blueprint);
            return ClarificationResult.of(buildFollowUpMessage(blueprint, mode));
        }

        Object workspaceCard = null;
        String workspaceCardType = null;
        String workspaceMessage = null;

        if (workspaceEntryEnabled) {
            try {
                if (ChatSession.Mode.LESSON_PLAN.equals(mode) && lessonPlanWorkspaceService != null) {
                    workspaceCard = lessonPlanWorkspaceService.createWorkspace(session, blueprint);
                    workspaceCardType = "lesson_plan_stage_entry";
                    workspaceMessage = "我会先检索知识库并整理参考资料，然后在右侧工作区生成一份可继续编辑的教案初稿。";
                } else if (ChatSession.Mode.PPT.equals(mode) && pptWorkspaceService != null) {
                    workspaceCard = pptWorkspaceService.createWorkspace(session, blueprint);
                    workspaceCardType = "ppt_stage_entry";
                    workspaceMessage = "我会先检索知识库、整理 PPT 蓝图，然后在右侧工作区持续生成页面方案、结构化 JSON 和最终文件。";
                }
            } catch (Exception e) {
                log.warn("Create workspace failed, fallback to pending card: sessionId={}, mode={}", session.getId(), mode, e);
            }
        }

        saveBlueprint(session, nextStage, blueprint);
        chatSessionService.updateGenerationStatus(
                session.getId(),
                ChatSession.GenerationStatus.PROCESSING,
                PROCESSING_MESSAGE
        );
        eventPublisher.publishEvent(new GenerationRequestedEvent(
                session.getId(),
                mode,
                blueprint.toFlatMap()
        ));

        if (workspaceCard != null) {
            return ClarificationResult.withCard(workspaceMessage, workspaceCardType, workspaceCard);
        }

        return ClarificationResult.generationPending(
                GENERATION_PENDING_MESSAGE,
                buildPendingCard(blueprint, mode)
        );
    }

    private TeachingBlueprint loadBlueprint(ChatSession session) {
        if (!hasText(session.getTeachingElements())) {
            return emptyBlueprint();
        }

        try {
            return TeachingBlueprint.fromLegacyMap(objectMapper.readValue(session.getTeachingElements(), MAP_TYPE));
        } catch (Exception e) {
            log.warn("Parse teaching elements failed: sessionId={}", session.getId(), e);
            return emptyBlueprint();
        }
    }

    private void saveBlueprint(ChatSession session, String stage, TeachingBlueprint blueprint) {
        try {
            String json = objectMapper.writeValueAsString(blueprint.toFlatMap());
            session.setTeachingElements(json);
            session.setStage(stage);
            chatSessionService.updateTeachingMode(session.getId(), normalizeMode(session.getMode()), stage, json);
        } catch (Exception e) {
            log.error("Save blueprint failed: sessionId={}", session.getId(), e);
        }
    }

    private void updateStage(Long sessionId, String stage, TeachingBlueprint blueprint) {
        try {
            chatSessionService.updateTeachingMode(
                    sessionId,
                    null,
                    stage,
                    objectMapper.writeValueAsString(blueprint.toFlatMap())
            );
        } catch (Exception e) {
            log.error("Update stage failed: sessionId={}", sessionId, e);
        }
    }

    private void mergeAttachmentContext(TeachingBlueprint blueprint, MultipartFile[] attachments) {
        if (attachments == null || attachments.length == 0) {
            return;
        }

        LinkedHashSet<String> attachmentNames = new LinkedHashSet<>(extractStringList(blueprint, "attachmentNames"));
        StringBuilder referenceBuilder = new StringBuilder(asString(blueprint.getExtension("referenceText")));

        for (MultipartFile attachment : attachments) {
            if (attachment == null || !hasText(attachment.getOriginalFilename())) {
                continue;
            }

            attachmentNames.add(attachment.getOriginalFilename());
            try {
                String text = textExtractorService.extractText(attachment);
                if (hasText(text)) {
                    if (referenceBuilder.length() > 0) {
                        referenceBuilder.append("\n\n");
                    }
                    referenceBuilder.append("附件：")
                            .append(attachment.getOriginalFilename())
                            .append('\n')
                            .append(truncate(text, 3_000));
                }
            } catch (Exception e) {
                log.warn("Extract attachment text failed: file={}", attachment.getOriginalFilename(), e);
            }
        }

        if (!attachmentNames.isEmpty()) {
            blueprint.putExtension("attachmentNames", new ArrayList<>(attachmentNames));
        }
        if (referenceBuilder.length() > 0) {
            blueprint.putExtension("referenceText", truncate(referenceBuilder.toString(), MAX_REFERENCE_TEXT));
        }
    }

    private boolean shouldMergeMessage(String message, boolean readyBefore) {
        if (!hasText(message)) {
            return false;
        }
        if (readyBefore && isControlOnlyMessage(message)) {
            return false;
        }
        return true;
    }

    private boolean isControlOnlyMessage(String message) {
        return (isConfirmIntent(message) || isNegativeConfirm(message)) && !containsStructuredUpdateHint(message);
    }

    private boolean containsStructuredUpdateHint(String message) {
        if (!hasText(message)) {
            return false;
        }

        return DURATION_PATTERN.matcher(message).find()
                || SLIDE_COUNT_PATTERN.matcher(message).find()
                || message.contains("改成")
                || message.contains("更新")
                || message.contains("补充")
                || message.contains("知识点")
                || message.contains("重点")
                || message.contains("难点")
                || message.contains("风格")
                || message.contains("模板")
                || message.contains("学科")
                || message.contains("科目")
                || message.contains("年级")
                || message.contains("课题")
                || message.contains("主题")
                || message.contains("标题")
                || message.contains("HTML")
                || message.contains("互动")
                || message.contains("场景")
                || message.contains("动画")
                || message.contains("视觉")
                || message.contains("题型")
                || message.contains("题目");
    }

    private void mergeMessageIntoBlueprint(TeachingBlueprint blueprint, String message, String mode) {
        if (!hasText(message)) {
            return;
        }

        ensureCore(blueprint);
        Map<String, Object> llmPatch = extractPatchWithModel(message, mode);
        Map<String, Object> heuristicPatch = extractPatchHeuristically(message, mode);
        applyPatchToBlueprint(blueprint, llmPatch);
        applyPatchToBlueprint(blueprint, heuristicPatch);

        if (!isControlOnlyMessage(message) && (hasMeaningfulPatch(llmPatch) || hasMeaningfulPatch(heuristicPatch))) {
            String notes = mergeText(asString(blueprint.getExtension("notes")), message);
            blueprint.putExtension("notes", truncate(notes, 2_000));
        }

        TeachingCore core = ensureCore(blueprint);
        if (!hasText(core.getTopic())) {
            String fallbackTopic = extractTopic(message);
            if (hasText(fallbackTopic)) {
                core.setTopic(fallbackTopic);
            }
        }
    }

    private boolean hasMeaningfulPatch(Map<String, Object> patch) {
        return patch != null && !patch.isEmpty();
    }

    private Map<String, Object> extractPatchWithModel(String message, String mode) {
        if (!hasText(message) || !hasText(extractModel)) {
            return Map.of();
        }

        try {
            List<ILLMService.ChatMessage> messages = List.of(
                    new ILLMService.ChatMessage(
                            "system",
                            """
                            Extract teaching blueprint fields from the user message.
                            Return one JSON object only.
                            Allowed keys:
                            subject, grade, topic, duration, slideCount, style,
                            knowledgePoints, teachingGoals, keyPoints, difficultPoints,
                            userConstraints, notes,
                            deliveryFormat, interactionIdea, usageScene, visualStyle,
                            animationLevel, interactionHints, questionCount, questionType.
                            Omit unknown fields.
                            """
                    ),
                    new ILLMService.ChatMessage("user", "Mode: " + mode + "\nUser message:\n" + message)
            );
            String content = llmService.chatWithModel(messages, extractModel);
            String jsonBlock = extractJsonBlock(content);
            if (!hasText(jsonBlock)) {
                return Map.of();
            }
            Map<String, Object> parsed = objectMapper.readValue(jsonBlock, MAP_TYPE);
            return parsed != null ? parsed : Map.of();
        } catch (Exception e) {
            log.debug("LLM extraction failed, fallback to heuristics: {}", e.getMessage());
            return Map.of();
        }
    }

    private Map<String, Object> extractPatchHeuristically(String message, String mode) {
        Map<String, Object> patch = new LinkedHashMap<>();

        putIfHasText(patch, "subject", extractSubject(message));
        putIfHasText(patch, "grade", extractGrade(message));
        putIfHasText(patch, "topic", extractTopic(message));

        Integer duration = extractDuration(message);
        if (duration != null && duration > 0) {
            patch.put("duration", duration);
        }

        Integer slideCount = extractSlideCount(message);
        if (slideCount != null && slideCount > 0) {
            patch.put("slideCount", slideCount);
        }

        putIfHasText(patch, "style", extractStyle(message));

        List<String> knowledgePoints = extractKeywordList(message, "知识点", "重点内容", "核心内容");
        if (!knowledgePoints.isEmpty()) {
            patch.put("knowledgePoints", knowledgePoints);
        }

        List<String> keyPoints = extractKeywordList(message, "重点", "关键点");
        if (!keyPoints.isEmpty()) {
            patch.put("keyPoints", keyPoints);
        }

        List<String> difficultPoints = extractKeywordList(message, "难点");
        if (!difficultPoints.isEmpty()) {
            patch.put("difficultPoints", difficultPoints);
        }

        putIfHasText(patch, "deliveryFormat", extractLabeledValue(message, "呈现形式", "输出形式", "交付形式"));
        putIfHasText(patch, "interactionIdea", extractLabeledValue(message, "互动创意", "互动方式", "互动设计"));
        putIfHasText(patch, "usageScene", extractLabeledValue(message, "使用场景", "应用场景"));
        putIfHasText(patch, "visualStyle", extractLabeledValue(message, "视觉风格"));
        putIfHasText(patch, "animationLevel", extractLabeledValue(message, "动画程度", "动画强度"));
        putIfHasText(patch, "questionType", extractLabeledValue(message, "题型"));

        Integer questionCount = extractCount(message, "(\\d{1,2})\\s*(题|个问题)");
        if (questionCount != null && questionCount > 0) {
            patch.put("questionCount", questionCount);
        }

        List<String> interactionHints = extractKeywordList(message, "互动提示", "操作提示");
        if (!interactionHints.isEmpty()) {
            patch.put("interactionHints", interactionHints);
        }

        if (ChatSession.Mode.PPT.equals(mode)) {
            patch.putIfAbsent("slideCount", 10);
            patch.putIfAbsent("style", "简洁课堂");
        }

        if (ChatSession.Mode.INTERACTIVE.equals(mode) && message.contains("HTML")) {
            patch.putIfAbsent("deliveryFormat", "HTML互动内容");
        }

        return patch;
    }

    private void applyPatchToBlueprint(TeachingBlueprint blueprint, Map<String, Object> patch) {
        if (patch == null || patch.isEmpty()) {
            return;
        }

        TeachingCore core = ensureCore(blueprint);
        if (hasText(asString(patch.get("subject")))) {
            core.setSubject(asString(patch.get("subject")));
        }
        if (hasText(asString(patch.get("grade")))) {
            core.setGrade(asString(patch.get("grade")));
        }
        if (hasText(asString(patch.get("topic")))) {
            core.setTopic(asString(patch.get("topic")));
        }

        Integer duration = parseInteger(patch.get("duration"));
        if (duration != null && duration > 0) {
            core.setDuration(duration);
        }

        copyExtension(blueprint, patch, "slideCount");
        copyExtension(blueprint, patch, "style");
        copyExtension(blueprint, patch, "notes");
        copyExtension(blueprint, patch, "knowledgePoints");
        copyExtension(blueprint, patch, "teachingGoals");
        copyExtension(blueprint, patch, "keyPoints");
        copyExtension(blueprint, patch, "difficultPoints");
        copyExtension(blueprint, patch, "userConstraints");
        copyExtension(blueprint, patch, "deliveryFormat");
        copyExtension(blueprint, patch, "interactionIdea");
        copyExtension(blueprint, patch, "usageScene");
        copyExtension(blueprint, patch, "visualStyle");
        copyExtension(blueprint, patch, "animationLevel");
        copyExtension(blueprint, patch, "interactionHints");
        copyExtension(blueprint, patch, "questionCount");
        copyExtension(blueprint, patch, "questionType");
    }

    private void copyExtension(TeachingBlueprint blueprint, Map<String, Object> patch, String key) {
        Object value = patch.get(key);
        if (value == null) {
            return;
        }
        if (value instanceof String text && !hasText(text)) {
            return;
        }
        if (value instanceof Collection<?> collection && collection.isEmpty()) {
            return;
        }
        blueprint.putExtension(key, value);
    }

    private boolean isReadyForConfirmation(TeachingBlueprint blueprint, String mode) {
        TeachingCore core = ensureCore(blueprint);
        if (!hasText(core.getTopic())) {
            return false;
        }
        // PPT 走全替换：视觉由模板决定，学科/年级不是生成必需。只要有课题就出确认卡，
        // 不再因缺年级而追问，打断"首条消息 → 直接出确认卡"的体验（缺的字段可在卡上补/改）。
        if (ChatSession.Mode.PPT.equals(mode)) {
            return true;
        }
        if (!hasText(core.getSubject()) || !hasText(core.getGrade())) {
            return false;
        }
        if (ChatSession.Mode.LESSON_PLAN.equals(mode)) {
            return core.getDuration() != null && core.getDuration() > 0;
        }
        return true;
    }

    private List<String> getMissingFields(TeachingBlueprint blueprint, String mode) {
        TeachingCore core = ensureCore(blueprint);
        List<String> missing = new ArrayList<>();
        if (!hasText(core.getSubject())) {
            missing.add("学科");
        }
        if (!hasText(core.getGrade())) {
            missing.add("年级");
        }
        if (!hasText(core.getTopic())) {
            missing.add("课题");
        }
        if (ChatSession.Mode.LESSON_PLAN.equals(mode) && (core.getDuration() == null || core.getDuration() <= 0)) {
            missing.add("课时");
        }
        return missing;
    }

    private String buildFollowUpMessage(TeachingBlueprint blueprint, String mode) {
        List<String> missingFields = getMissingFields(blueprint, mode);
        if (missingFields.isEmpty()) {
            return buildReadyMessage(mode);
        }
        String example = buildDynamicExample(blueprint, mode, missingFields);
        String intro = missingFields.size() == 1
                ? "为了继续生成" + getModeName(mode) + "，还差" + missingFields.get(0) + "。"
                : "为了继续生成" + getModeName(mode) + "，还差这些关键信息：" + String.join("、", missingFields) + "。";
        return intro + "你可以直接一句话补充，例如“" + example + "”。";
    }

    private String buildDynamicExample(TeachingBlueprint blueprint, String mode, List<String> missingFields) {
        TeachingCore core = ensureCore(blueprint);
        String subject = hasText(core.getSubject()) ? core.getSubject() : "数学";
        String grade = hasText(core.getGrade()) ? core.getGrade() : "高一";
        String topic = hasText(core.getTopic()) ? core.getTopic() : "函数单调性";

        List<String> parts = new ArrayList<>();
        Set<String> missing = new LinkedHashSet<>(missingFields);

        if (missing.contains("年级")) parts.add(grade);
        if (missing.contains("学科")) parts.add(subject);
        if (missing.contains("课题")) parts.add(topic);
        if (missing.contains("课时")) parts.add("45分钟");

        if (parts.isEmpty()) {
            parts.addAll(List.of(grade, subject, topic, "45分钟"));
        }
        return String.join(",", parts);
    }

    private String buildReadyMessage(String mode) {
        return "我已经整理出" + getModeName(mode) + "蓝图，请确认后开始生成。";
    }

    private BlueprintCardData buildBlueprintCard(TeachingBlueprint blueprint, String mode) {
        TeachingCore core = ensureCore(blueprint);
        return BlueprintCardData.builder()
                .mode(mode)
                .modeName(getModeName(mode))
                .title(core.getTopic())
                .subject(core.getSubject())
                .grade(core.getGrade())
                .duration(core.getDuration())
                .slideCount(parseInteger(blueprint.getExtension("slideCount")))
                .style(firstString(blueprint, "style"))
                .deliveryFormat(firstString(blueprint, "deliveryFormat"))
                .interactionIdea(firstString(blueprint, "interactionIdea"))
                .usageScene(firstString(blueprint, "usageScene"))
                .visualStyle(firstString(blueprint, "visualStyle"))
                .animationLevel(firstString(blueprint, "animationLevel"))
                .questionCount(parseInteger(blueprint.getExtension("questionCount")))
                .questionType(firstString(blueprint, "questionType"))
                .notes(firstString(blueprint, "notes"))
                .knowledgePoints(toKnowledgePoints(extractStringList(blueprint, "knowledgePoints")))
                .teachingGoals(toGoalMap(extractStringList(blueprint, "teachingGoals")))
                .keyPoints(toFocusItems(extractStringList(blueprint, "keyPoints")))
                .difficultPoints(toFocusItems(extractStringList(blueprint, "difficultPoints")))
                .interactionHints(extractStringList(blueprint, "interactionHints"))
                .userConstraints(extractStringList(blueprint, "userConstraints"))
                .buttons(List.of(
                        BlueprintCardData.ActionButton.builder().type("supplement").label("补充细节").build(),
                        BlueprintCardData.ActionButton.builder().type("confirm").label("确认生成").build()
                ))
                .build();
    }

    private GenerationCardData buildPendingCard(TeachingBlueprint blueprint, String mode) {
        return GenerationCardData.builder()
                .mode(mode)
                .modeName(getModeName(mode))
                .title(ensureCore(blueprint).getTopic())
                .status("processing")
                .statusText("正在生成")
                .summary("已收到确认，系统正在后台生成结果。")
                .build();
    }

    private GenerationCardData buildFailedCard(TeachingBlueprint blueprint, String mode, String error) {
        return GenerationCardData.builder()
                .mode(mode)
                .modeName(getModeName(mode))
                .title(ensureCore(blueprint).getTopic())
                .status("failed")
                .statusText("生成失败")
                .summary(hasText(error) ? error : "请稍后重试。")
                .build();
    }

    private GenerationOutcome generateLessonPlan(TeachingBlueprint blueprint) {
        LessonPlanResponse response = coursewareService.generateLessonPlan(buildLessonPlanRequest(blueprint));
        if (!response.isSuccess()) {
            throw new IllegalStateException(response.getError());
        }

        GenerationCardData card = GenerationCardData.builder()
                .mode(ChatSession.Mode.LESSON_PLAN)
                .modeName(getModeName(ChatSession.Mode.LESSON_PLAN))
                .title(ensureCore(blueprint).getTopic())
                .status("completed")
                .statusText("已生成")
                .fileName(response.getFileName())
                .downloadUrl(response.getDownloadUrl())
                .preview(response.getPreview())
                .summary("教案已生成，可以直接下载查看。")
                .build();
        return new GenerationOutcome("教案已经生成完成。", buildResultText(card), card);
    }

    private GenerationOutcome generatePpt(TeachingBlueprint blueprint) {
        PptGenerateResponse response = coursewareService.generatePpt(buildPptRequest(blueprint));
        if (!response.isSuccess()) {
            throw new IllegalStateException(response.getError());
        }

        GenerationCardData card = GenerationCardData.builder()
                .mode(ChatSession.Mode.PPT)
                .modeName(getModeName(ChatSession.Mode.PPT))
                .title(ensureCore(blueprint).getTopic())
                .status("completed")
                .statusText("已生成")
                .fileName(response.getFileName())
                .downloadUrl(response.getDownloadUrl())
                .outline(response.getOutline())
                .summary("PPT 已生成，可以查看大纲并下载文件。")
                .build();
        return new GenerationOutcome("PPT 已经生成完成。", buildResultText(card), card);
    }

    private GenerationOutcome generateInteractive(TeachingBlueprint blueprint) {
        int questionCount = valueOrDefault(parseInteger(blueprint.getExtension("questionCount")), 5);
        String questionType = valueOrDefault(firstString(blueprint, "questionType"), "quiz");
        String content = coursewareService.generateInteractiveContent(
                ensureCore(blueprint).getTopic(),
                questionCount,
                questionType
        );

        GenerationCardData card = GenerationCardData.builder()
                .mode(ChatSession.Mode.INTERACTIVE)
                .modeName(getModeName(ChatSession.Mode.INTERACTIVE))
                .title(ensureCore(blueprint).getTopic())
                .status("completed")
                .statusText("已生成")
                .preview(content)
                .summary("互动内容已生成，可继续进入后续专用工作区细化。")
                .build();
        return new GenerationOutcome("互动内容已经生成完成。", buildResultText(card), card);
    }

    private LessonPlanRequest buildLessonPlanRequest(TeachingBlueprint blueprint) {
        TeachingCore core = ensureCore(blueprint);
        return LessonPlanRequest.builder()
                .subject(core.getSubject())
                .grade(core.getGrade())
                .topic(core.getTopic())
                .duration(core.getDuration())
                .knowledgePoints(extractStringList(blueprint, "knowledgePoints"))
                .teachingGoal(joinList(extractStringList(blueprint, "teachingGoals")))
                .keyPoint(joinList(extractStringList(blueprint, "keyPoints")))
                .difficultPoint(joinList(extractStringList(blueprint, "difficultPoints")))
                .referenceText(firstString(blueprint, "referenceText"))
                .userDescription(buildUserDescription(blueprint))
                .build();
    }

    private PptGenerateRequest buildPptRequest(TeachingBlueprint blueprint) {
        TeachingCore core = ensureCore(blueprint);
        return PptGenerateRequest.builder()
                .title(core.getTopic())
                .subject(core.getSubject())
                .grade(core.getGrade())
                .knowledgePoints(extractStringList(blueprint, "knowledgePoints"))
                .slideCount(valueOrDefault(parseInteger(blueprint.getExtension("slideCount")), 10))
                .style(valueOrDefault(firstString(blueprint, "style"), "简洁课堂"))
                .referenceText(firstString(blueprint, "referenceText"))
                .userDescription(buildUserDescription(blueprint))
                .templateId(firstString(blueprint, "templateId", "pptTemplateId"))
                .build();
    }

    private String buildUserDescription(TeachingBlueprint blueprint) {
        List<String> lines = new ArrayList<>();
        appendTextLine(lines, "用户补充", firstString(blueprint, "notes"));
        appendTextLine(lines, "知识点", joinList(extractStringList(blueprint, "knowledgePoints")));
        appendTextLine(lines, "重点", joinList(extractStringList(blueprint, "keyPoints")));
        appendTextLine(lines, "难点", joinList(extractStringList(blueprint, "difficultPoints")));
        appendTextLine(lines, "约束条件", joinList(extractStringList(blueprint, "userConstraints")));
        appendTextLine(lines, "互动形式", firstString(blueprint, "deliveryFormat"));
        appendTextLine(lines, "互动创意", firstString(blueprint, "interactionIdea"));
        appendTextLine(lines, "使用场景", firstString(blueprint, "usageScene"));
        appendTextLine(lines, "附件参考", joinList(extractStringList(blueprint, "attachmentNames")));
        return String.join("\n", lines);
    }

    private void saveAssistantCardMessage(Long sessionId, String mode, String message, String cardType, Object cardData) {
        try {
            chatSessionService.saveMessage(
                    sessionId,
                    "assistant",
                    message,
                    mode,
                    0,
                    "教学模式",
                    0,
                    cardType,
                    cardData == null ? null : objectMapper.writeValueAsString(cardData)
            );
        } catch (Exception e) {
            log.error("Save assistant card message failed: sessionId={}", sessionId, e);
        }
    }

    private TeachingBlueprint emptyBlueprint() {
        return TeachingBlueprint.builder()
                .core(new TeachingCore())
                .extensions(new LinkedHashMap<>())
                .build();
    }

    private TeachingCore ensureCore(TeachingBlueprint blueprint) {
        if (blueprint.getCore() == null) {
            blueprint.setCore(new TeachingCore());
        }
        if (blueprint.getExtensions() == null) {
            blueprint.setExtensions(new LinkedHashMap<>());
        }
        return blueprint.getCore();
    }

    private void applyTemplateId(TeachingBlueprint blueprint, String templateId) {
        String normalized = normalizeString(templateId);
        if (!hasText(normalized)) {
            return;
        }
        blueprint.putExtension("templateId", normalized);
        blueprint.putExtension("pptTemplateId", normalized);
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
        return Stream.of(value.split("[,，、\\n；;]"))
                .map(String::trim)
                .filter(this::hasText)
                .distinct()
                .toList();
    }

    private List<BlueprintCardData.KnowledgePoint> toKnowledgePoints(List<String> items) {
        List<BlueprintCardData.KnowledgePoint> result = new ArrayList<>();
        int index = 1;
        for (String item : items) {
            result.add(BlueprintCardData.KnowledgePoint.builder()
                    .id("kp-" + index)
                    .name(item)
                    .build());
            index++;
        }
        return result;
    }

    private Map<String, String> toGoalMap(List<String> items) {
        Map<String, String> result = new LinkedHashMap<>();
        int index = 1;
        for (String item : items) {
            result.put("goal_" + index, item);
            index++;
        }
        return result;
    }

    private List<BlueprintCardData.FocusItem> toFocusItems(List<String> items) {
        List<BlueprintCardData.FocusItem> result = new ArrayList<>();
        for (String item : items) {
            result.add(BlueprintCardData.FocusItem.builder().name(item).build());
        }
        return result;
    }

    private String getModeName(String mode) {
        return switch (normalizeMode(mode)) {
            case ChatSession.Mode.PPT -> "PPT";
            case ChatSession.Mode.INTERACTIVE -> "互动内容";
            default -> "教案";
        };
    }

    private String normalizeMode(String mode) {
        if (ChatSession.Mode.PPT.equals(mode)) {
            return ChatSession.Mode.PPT;
        }
        if (ChatSession.Mode.INTERACTIVE.equals(mode)) {
            return ChatSession.Mode.INTERACTIVE;
        }
        return ChatSession.Mode.LESSON_PLAN;
    }

    private boolean isConfirmIntent(String message) {
        return hasText(message) && CONFIRM_WORDS.stream().anyMatch(message::contains);
    }

    private boolean isNegativeConfirm(String message) {
        return hasText(message) && NEGATIVE_CONFIRM_WORDS.stream().anyMatch(message::contains);
    }

    private String extractSubject(String message) {
        String labeled = extractLabeledValue(message, "学科", "科目", "课程");
        if (hasText(labeled)) {
            return labeled;
        }
        for (String subject : HIGH_CONFIDENCE_SUBJECTS) {
            if (message.contains(subject)) {
                return subject;
            }
        }
        return "";
    }

    private String extractGrade(String message) {
        String labeled = extractLabeledValue(message, "年级", "学段");
        if (hasText(labeled)) {
            return labeled;
        }
        Matcher matcher = GRADE_PATTERN.matcher(message);
        return matcher.find() ? matcher.group(1) : "";
    }

    private String extractTopic(String message) {
        String labeled = extractLabeledValue(message, "课题", "主题", "标题");
        if (hasText(labeled)) {
            return labeled;
        }

        Matcher quoteMatcher = Pattern.compile("[《“\"]([^》”\"]{2,40})[》”\"]").matcher(message);
        if (quoteMatcher.find()) {
            return quoteMatcher.group(1).trim();
        }

        return "";
    }

    private Integer extractDuration(String message) {
        String labeled = extractLabeledValue(message, "课时", "时长", "时间");
        Integer labeledDuration = TeachingCore.parseDuration(labeled);
        if (labeledDuration != null) {
            return labeledDuration;
        }

        Matcher matcher = DURATION_PATTERN.matcher(message);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        }
        return TeachingCore.parseDuration(message);
    }

    private Integer extractSlideCount(String message) {
        String labeled = extractLabeledValue(message, "页数", "页数要求", "页数规模");
        Integer labeledCount = parseInteger(labeled);
        if (labeledCount != null) {
            return labeledCount;
        }
        Matcher matcher = SLIDE_COUNT_PATTERN.matcher(message);
        return matcher.find() ? parseInteger(matcher.group(1)) : null;
    }

    private Integer extractCount(String message, String regex) {
        Matcher matcher = Pattern.compile(regex).matcher(message);
        return matcher.find() ? parseInteger(matcher.group(1)) : null;
    }

    private String extractStyle(String message) {
        String labeled = extractLabeledValue(message, "风格", "视觉风格");
        if (hasText(labeled)) {
            return labeled;
        }
        for (String style : STYLE_HINTS) {
            if (message.contains(style)) {
                return style;
            }
        }
        return "";
    }

    private List<String> extractKeywordList(String message, String... keywords) {
        for (String keyword : keywords) {
            Matcher matcher = Pattern.compile(Pattern.quote(keyword) + "[:：]\\s*([^。；;\\n]+)").matcher(message);
            if (matcher.find()) {
                return normalizeToStringList(matcher.group(1));
            }
        }
        return List.of();
    }

    private String extractLabeledValue(String message, String... labels) {
        for (String label : labels) {
            Matcher matcher = Pattern.compile(Pattern.quote(label) + "[:：]\\s*([^，,；;。\\n]+)").matcher(message);
            if (matcher.find()) {
                String value = matcher.group(1).trim();
                if (hasText(value)) {
                    return value;
                }
            }
        }
        return "";
    }

    private void putIfHasText(Map<String, Object> patch, String key, String value) {
        if (hasText(value)) {
            patch.put(key, value);
        }
    }

    private Integer parseInteger(Object value) {
        try {
            if (value == null) {
                return null;
            }
            if (value instanceof Number number) {
                return number.intValue();
            }
            String text = String.valueOf(value).trim();
            return text.isEmpty() ? null : Integer.parseInt(text);
        } catch (Exception e) {
            return null;
        }
    }

    private String buildResultText(GenerationCardData card) {
        if (card == null) {
            return "";
        }
        List<String> lines = new ArrayList<>();
        appendTextLine(lines, "标题", card.getTitle());
        appendTextLine(lines, "状态", card.getStatusText());
        appendTextLine(lines, "说明", card.getSummary());
        return String.join("\n", lines);
    }

    private String extractJsonBlock(String value) {
        if (!hasText(value)) {
            return "";
        }
        int start = value.indexOf('{');
        int end = value.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return "";
        }
        return value.substring(start, end + 1);
    }

    private String joinList(List<String> values) {
        return values == null || values.isEmpty() ? "" : String.join("、", values);
    }

    private void appendTextLine(List<String> lines, String label, String value) {
        if (hasText(value)) {
            lines.add(label + "：" + value);
        }
    }

    private String firstString(TeachingBlueprint blueprint, String... keys) {
        for (String key : keys) {
            String value = asString(blueprint.getExtension(key));
            if (hasText(value)) {
                return value;
            }
        }
        return "";
    }

    private String normalizeString(String value) {
        return value == null ? "" : value.trim();
    }

    private String asString(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String mergeText(String base, String extra) {
        if (!hasText(base)) {
            return extra;
        }
        if (!hasText(extra)) {
            return base;
        }
        return base + "\n" + extra;
    }

    private String truncate(String value, int limit) {
        if (!hasText(value) || value.length() <= limit) {
            return normalizeString(value);
        }
        return value.substring(0, limit);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private <T> T valueOrDefault(T value, T fallback) {
        return value != null ? value : fallback;
    }

    private record GenerationOutcome(String message, String resultText, GenerationCardData cardData) {
    }
}
