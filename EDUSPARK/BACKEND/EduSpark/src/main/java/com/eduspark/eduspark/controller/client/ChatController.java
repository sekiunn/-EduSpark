package com.eduspark.eduspark.controller.client;

import com.eduspark.eduspark.dto.chat.ChatRequest;
import com.eduspark.eduspark.dto.chat.ChatResponse;
import com.eduspark.eduspark.dto.chat.ClarificationResult;
import com.eduspark.eduspark.dto.common.Result;
import com.eduspark.eduspark.dto.interactive.InteractiveModeResult;
import com.eduspark.eduspark.dto.knowledge.KnowledgeSearchResponse;
import com.eduspark.eduspark.dto.knowledge.KnowledgeSearchRequest;
import com.eduspark.eduspark.pojo.entity.ChatSession;
import com.eduspark.eduspark.pojo.entity.IntentType;
import com.eduspark.eduspark.service.IChatSessionService;
import com.eduspark.eduspark.service.IClarificationService;
import com.eduspark.eduspark.service.IInteractiveModeService;
import com.eduspark.eduspark.service.IKnowledgeService;
import com.eduspark.eduspark.service.IIntentService;
import com.eduspark.eduspark.service.ITextExtractorService;
import com.eduspark.eduspark.service.ITriLevelSearchService;
import com.eduspark.eduspark.service.IVideoProcessService;
import com.eduspark.eduspark.service.IWebSearchService;
import com.eduspark.eduspark.service.ILLMService;
import com.eduspark.eduspark.service.ISystemPromptService;
import com.eduspark.eduspark.service.IContextBuilderService;
import com.eduspark.eduspark.service.teaching.TeachingStageMachine;
import com.eduspark.eduspark.service.teaching.TeachingStageOperation;
import com.eduspark.eduspark.service.teaching.TeachingStageRoute;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * 对话接口（客户端 - AI教学助手）
 * <p>
 * 流程：意图识别 → 分流处理 → 返回回答 → 存储消息
 */
@Slf4j
@Validated
@RestController
@RequestMapping("/v1/chat")
public class ChatController {

    private final IIntentService intentService;
    private final ITriLevelSearchService triLevelSearchService;
    private final IWebSearchService webSearchService;
    private final ILLMService llmService;
    private final ITextExtractorService textExtractorService;
    private final IChatSessionService chatSessionService;
    private final IClarificationService clarificationService;
    private final IKnowledgeService knowledgeService;
    private final IInteractiveModeService interactiveModeService;
    private final ISystemPromptService systemPromptService;
    private final IContextBuilderService contextBuilderService;
    private final IVideoProcessService videoProcessService;
    private final TeachingStageMachine teachingStageMachine;

    @Autowired
    public ChatController(
            IIntentService intentService,
            ITriLevelSearchService triLevelSearchService,
            IWebSearchService webSearchService,
            ILLMService llmService,
            ITextExtractorService textExtractorService,
            IChatSessionService chatSessionService,
            IClarificationService clarificationService,
            IKnowledgeService knowledgeService,
            IInteractiveModeService interactiveModeService,
            ISystemPromptService systemPromptService,
            IContextBuilderService contextBuilderService,
            IVideoProcessService videoProcessService,
            TeachingStageMachine teachingStageMachine
    ) {
        this.intentService = intentService;
        this.triLevelSearchService = triLevelSearchService;
        this.webSearchService = webSearchService;
        this.llmService = llmService;
        this.textExtractorService = textExtractorService;
        this.chatSessionService = chatSessionService;
        this.clarificationService = clarificationService;
        this.knowledgeService = knowledgeService;
        this.interactiveModeService = interactiveModeService;
        this.systemPromptService = systemPromptService;
        this.contextBuilderService = contextBuilderService;
        this.videoProcessService = videoProcessService;
        this.teachingStageMachine = teachingStageMachine;
    }

    public ChatController(
            IIntentService intentService,
            ITriLevelSearchService triLevelSearchService,
            IWebSearchService webSearchService,
            ILLMService llmService,
            ITextExtractorService textExtractorService,
            IChatSessionService chatSessionService,
            IClarificationService clarificationService,
            IKnowledgeService knowledgeService,
            ISystemPromptService systemPromptService,
            IContextBuilderService contextBuilderService,
            IVideoProcessService videoProcessService,
            TeachingStageMachine teachingStageMachine
    ) {
        this(
                intentService,
                triLevelSearchService,
                webSearchService,
                llmService,
                textExtractorService,
                chatSessionService,
                clarificationService,
                knowledgeService,
                null,
                systemPromptService,
                contextBuilderService,
                videoProcessService,
                teachingStageMachine
        );
    }

    /**
     * 对话接口（带意图识别分流 + 会话存储）
     */
    @PostMapping
    public Result<ChatResponse> chat(
            @RequestBody @Valid ChatRequest request,
            HttpServletRequest httpRequest
    ) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        String message = request.getMessage();

        log.info("收到对话请求: message={}, webSearch={}, userId={}, sessionId={}, mode={}",
                truncate(message, 50),
                request.getWebSearchEnabled(),
                userId,
                request.getSessionId(),
                request.getMode());

        ChatSession session;
        boolean isNewSession = false;
        if (request.getSessionId() == null || request.getSessionId().isBlank()) {
            session = chatSessionService.createSession(userId, message);
            isNewSession = true;
            log.info("新建会话: sessionId={}", session.getSessionId());
        } else {
            session = chatSessionService.getBySessionId(request.getSessionId());
            if (session == null || !session.getUserId().equals(userId)) {
                return Result.fail("会话不存在");
            }
        }

        Long dbId = session.getId();

        if (isNewSession && request.getMode() != null && !request.getMode().isBlank()) {
            session.setMode(request.getMode());
            session.setStage(ChatSession.Stage.IDLE);
        }
        recoverRequestedModeIfMissing(session, request.getMode());

        chatSessionService.saveMessage(dbId, "user", message, null, null, null, null, null, null);

        if (session.getMode() != null && !session.getMode().isBlank()) {
            if (ChatSession.Mode.INTERACTIVE.equals(session.getMode())) {
                return handleInteractiveMode(session, message, null);
            }
            return handleTeachingMode(session, message, null, request.getAction(), request.getTemplateId());
        }

        long startTime = System.currentTimeMillis();
        ChatResponse response = switch (intentService.classify(message)) {
            case TEACHING -> handleTeaching(message, dbId, userId, request.getWebSearchEnabled());
            case FACTUAL -> handleFactual(message, dbId, userId);
            case CASUAL -> handleCasual(message, dbId);
            case COURSEWARE -> handleCourseware(message);
            case SENSITIVE -> handleSensitive();
            case TOOL -> handleTool(intentService.classify(message));
        };

        chatSessionService.saveMessage(
                dbId, "assistant", response.getAnswer(),
                intentService.classify(message).name(), response.getLayer(), response.getLayerDesc(),
                response.getCostMs() != null ? response.getCostMs().intValue() : null,
                null, null
        );

        log.info("对话完成: userId={}, layer={}, cost={}ms, sessionId={}",
                userId, response.getLayer(), response.getCostMs(), session.getSessionId());

        response.setSessionId(session.getSessionId());
        return Result.success(response);
    }

    /**
     * 流式对话接口（SSE）
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseBodyEmitter chatStream(
            @RequestBody @Valid ChatRequest request,
            HttpServletRequest httpRequest
    ) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        String message = request.getMessage();

        log.info("收到流式对话请求: message={}, userId={}, sessionId={}",
                truncate(message, 50), userId, request.getSessionId());

        SseEmitter emitter = new SseEmitter(180_000L);

        CompletableFuture.runAsync(() -> {
            try {
                if (request.getMode() != null && !request.getMode().isBlank()) {
                    log.warn("教学模式请求误入流式接口: userId={}, sessionId={}, mode={}",
                            userId, request.getSessionId(), request.getMode());
                    emitter.send(SseEmitter.event().name("error").data("教学模式已改为普通请求接口，请刷新页面后重试。"));
                    emitter.complete();
                    return;
                }

                ChatSession session;
                boolean isNewSession = false;
                if (request.getSessionId() == null || request.getSessionId().isBlank()) {
                    session = chatSessionService.createSession(userId, message);
                    isNewSession = true;
                    log.info("流式对话创建新会话: sessionId={}, userId={}", session.getSessionId(), userId);
                } else {
                    log.info("流式对话使用已有会话: sessionId={}, userId={}", request.getSessionId(), userId);
                    session = chatSessionService.getBySessionId(request.getSessionId());
                    if (session == null || !session.getUserId().equals(userId)) {
                        emitter.send(SseEmitter.event().name("error").data("会话不存在"));
                        emitter.complete();
                        return;
                    }
                }

                Long dbId = session.getId();
                String sessionId = session.getSessionId();

                if (session.getMode() != null && !session.getMode().isBlank()) {
                    log.warn("教学模式会话误入流式接口: userId={}, sessionId={}, mode={}",
                            userId, sessionId, session.getMode());
                    emitter.send(SseEmitter.event().name("error").data("教学模式已改为普通请求接口，请刷新页面后重试。"));
                    emitter.complete();
                    return;
                }

                if (isNewSession) {
                    emitter.send(SseEmitter.event().name("sessionId").data(sessionId));
                }

                chatSessionService.saveMessage(dbId, "user", message, null, null, null, null, null, null);

                IntentType intent = intentService.classify(message);
                log.info("流式对话意图识别: intent={}", intent);

                long startTime = System.currentTimeMillis();
                String answer;
                int layer;
                String layerDesc;

                switch (intent) {
                    case TEACHING -> {
                        ITriLevelSearchService.TriLevelSearchRequest searchReq =
                                new ITriLevelSearchService.TriLevelSearchRequest(
                                        message, message, 5,
                                        Boolean.TRUE.equals(request.getWebSearchEnabled()), userId
                                );
                        ITriLevelSearchService.TriLevelSearchResult result =
                                triLevelSearchService.search(searchReq);
                        layer = result.layer();
                        layerDesc = result.layerDesc();

                        String referencedContext = null;
                        if (request.getReferencedFileId() != null) {
                            try {
                                KnowledgeSearchResponse fileResult = knowledgeService.search(
                                        KnowledgeSearchRequest.builder()
                                                .query(message)
                                                .topK(5)
                                                .userId(userId)
                                                .build()
                                );
                                if (fileResult != null && fileResult.getTotal() > 0) {
                                    List<KnowledgeSearchResponse.KnowledgeSearchResult> filteredResults =
                                            fileResult.getResults().stream()
                                                    .filter(r -> r.getFileId() != null && r.getFileId().equals(request.getReferencedFileId()))
                                                    .limit(5)
                                                    .toList();
                                    if (!filteredResults.isEmpty()) {
                                        referencedContext = contextBuilderService.buildReferencedFileContext(filteredResults);
                                        log.info("引用文档上下文加载: fileId={}, chunks={}", request.getReferencedFileId(), filteredResults.size());
                                    }
                                }
                            } catch (Exception e) {
                                log.warn("加载引用文档上下文失败: fileId={}", request.getReferencedFileId(), e);
                            }
                        }

                        // 优先使用引用文档，否则使用检索返回的 context
                        String finalContext = referencedContext != null ? referencedContext : result.context();

                        // 根据 promptType 选择系统提示词
                        String effectivePrompt = switch (result.promptType()) {
                            case "rag" -> null; // streamChat 会自动用 ragPrompt(context)
                            case "webSearch" -> systemPromptService.webSearchPrompt();
                            case "llmFallback" -> systemPromptService.llmFallbackPrompt();
                            default -> null;
                        };

                        if (result.answer() != null) {
                            answer = result.answer();
                            ObjectMapper mapper = new ObjectMapper();
                            emitter.send(SseEmitter.event().name("chunk").data(mapper.writeValueAsString(answer)));
                        } else if (finalContext != null) {
                            List<ChatRequest.ChatMessage> history = getHistoryFromDb(dbId);
                            answer = streamChat(emitter, message, toLLMMessages(history), finalContext, effectivePrompt);
                        } else {
                            List<ChatRequest.ChatMessage> history = getHistoryFromDb(dbId);
                            answer = streamChat(emitter, message, toLLMMessages(history), null,
                                    systemPromptService.llmFallbackPrompt());
                        }
                    }
                    case FACTUAL -> {
                        List<ChatRequest.ChatMessage> history = getHistoryFromDb(dbId);
                        IWebSearchService.SearchResult webResult = webSearchService.search(message, 5);
                        if (webResult.success() && webResult.total() > 0) {
                            String context = contextBuilderService.buildWebContext(webResult);
                            answer = streamChat(emitter, message, toLLMMessages(history), context,
                                    systemPromptService.webSearchPrompt());
                            layer = 2;
                            layerDesc = "联网搜索";
                        } else {
                            answer = streamChat(emitter, message, toLLMMessages(history), null,
                                    systemPromptService.llmFallbackPrompt());
                            layer = 3;
                            layerDesc = "LLM兜底";
                        }
                    }
                    case CASUAL -> {
                        List<ChatRequest.ChatMessage> history = getHistoryFromDb(dbId);
                        answer = streamChat(emitter, message, toLLMMessages(history), null,
                                systemPromptService.casualPrompt());
                        layer = 3;
                        layerDesc = "闲聊模式";
                    }
                    case COURSEWARE -> {
                        answer = "请使用左侧导航栏的专用功能生成教学内容。";
                        layer = 0;
                        layerDesc = "课件引导";
                        ObjectMapper mapper = new ObjectMapper();
                        emitter.send(SseEmitter.event().name("chunk").data(mapper.writeValueAsString(answer)));
                        emitter.complete();
                        chatSessionService.saveMessage(dbId, "assistant", answer,
                                intent.name(), layer, layerDesc, 0, null, null);
                        return;
                    }
                    case SENSITIVE -> {
                        answer = "抱歉，您的问题涉及敏感内容，暂时无法回答。";
                        layer = 0;
                        layerDesc = "内容过滤";
                        ObjectMapper mapper = new ObjectMapper();
                        emitter.send(SseEmitter.event().name("chunk").data(mapper.writeValueAsString(answer)));
                        emitter.complete();
                        chatSessionService.saveMessage(dbId, "assistant", answer,
                                intent.name(), layer, layerDesc, 0, null, null);
                        return;
                    }
                    default -> {
                        answer = "您的事务性操作已转至专用接口处理。";
                        layer = 0;
                        layerDesc = "工具分流";
                        ObjectMapper mapper = new ObjectMapper();
                        emitter.send(SseEmitter.event().name("chunk").data(mapper.writeValueAsString(answer)));
                        emitter.complete();
                        chatSessionService.saveMessage(dbId, "assistant", answer,
                                intent.name(), layer, layerDesc, 0, null, null);
                        return;
                    }
                }

                long cost = System.currentTimeMillis() - startTime;

                chatSessionService.saveMessage(dbId, "assistant", answer,
                        intent.name(), layer, layerDesc, (int) cost, null, null);

                emitter.send(SseEmitter.event().name("done").data(String.valueOf(cost)));
                emitter.complete();

                log.info("流式对话完成: userId={}, layer={}, cost={}ms", userId, layer, cost);

            } catch (Exception e) {
                log.error("流式对话异常: {}", e.getMessage(), e);
                try {
                    emitter.send(SseEmitter.event().name("error").data("服务异常: " + e.getMessage()));
                } catch (IllegalStateException | IOException ex) {
                    log.warn("Emitter已关闭，无法发送错误信息: {}", ex.getMessage());
                }
                try {
                    emitter.complete();
                } catch (IllegalStateException ex) {
                    log.warn("Emitter已关闭，无法完成: {}", ex.getMessage());
                }
            }
        });

        return emitter;
    }

    /**
     * 流式调用LLM并通过SSE发送每个chunk
     */
    private String streamChat(SseEmitter emitter, String userMessage,
                             List<ILLMService.ChatMessage> history,
                             String context, String systemPrompt) throws IOException {
        StringBuilder fullResponse = new StringBuilder();
        ObjectMapper mapper = new ObjectMapper();

        List<ILLMService.ChatMessage> messages = new java.util.ArrayList<>();
        String effectiveSystemPrompt = systemPrompt != null ? systemPrompt :
                (context != null ? systemPromptService.ragPrompt(context) : systemPromptService.simplePrompt());
        messages.add(new ILLMService.ChatMessage("system", effectiveSystemPrompt));
        messages.addAll(history);
        
        String enhancedUserMessage = userMessage;
        if (context != null && !context.isBlank()) {
            enhancedUserMessage += "\n\n【重要】正文正常回答即可，不要在正文中标注来源。回答最后必须列出【参考文献】列表（含文档名）。";
        }
        messages.add(new ILLMService.ChatMessage("user", enhancedUserMessage));

        llmService.chatStream(messages, chunk -> {
            try {
                fullResponse.append(chunk);
                emitter.send(SseEmitter.event().name("chunk").data(mapper.writeValueAsString(chunk)));
            } catch (IOException e) {
                log.warn("SSE发送chunk失败: {}", e.getMessage());
            }
        });

        return fullResponse.toString();
    }

    /**
     * 处理教学模式（多轮澄清流程）
     */
    private Result<ChatResponse> handleInteractiveMode(ChatSession session,
                                                       String userMessage,
                                                       MultipartFile[] attachments) {
        InteractiveModeResult result;
        int layer = 0;
        String layerDesc = "互动模式";

        try {
            if (interactiveModeService == null) {
                throw new IllegalStateException("Interactive mode service is not configured");
            }
            result = interactiveModeService.handleMessage(session, userMessage, attachments);
        } catch (Exception e) {
            log.error("Interactive mode failed: {}", e.getMessage(), e);
            result = InteractiveModeResult.builder()
                    .message("发生错误：" + e.getMessage())
                    .build();
            layer = -1;
        }

        ObjectMapper saveMapper = new ObjectMapper();
        String cardDataJson = null;
        if (result != null && result.getCardData() != null) {
            try {
                cardDataJson = saveMapper.writeValueAsString(result.getCardData());
            } catch (Exception e) {
                log.error("Serialize interactive cardData failed", e);
            }
        }

        String replyContent = result != null && result.getMessage() != null
                ? result.getMessage()
                : "抱歉，发生了错误，请稍后重试。";

        chatSessionService.saveMessage(
                session.getId(),
                "assistant",
                replyContent,
                ChatSession.Mode.INTERACTIVE,
                layer,
                layerDesc,
                0,
                result != null ? result.getCardType() : null,
                cardDataJson
        );

        ChatSession latestSession = chatSessionService.getSessionWithMessages(session.getId(), session.getUserId());
        String generationStatus = latestSession != null ? latestSession.getGenerationStatus() : null;

        ChatResponse response = ChatResponse.builder()
                .sessionId(session.getSessionId())
                .answer(replyContent)
                .layer(layer)
                .layerDesc(layerDesc)
                .knowledgeSources(null)
                .hasPartialKnowledge(false)
                .costMs(0L)
                .recommendedToUpload(false)
                .generationStatus(generationStatus)
                .cardType(result != null ? result.getCardType() : null)
                .cardData(result != null ? result.getCardData() : null)
                .build();

        return Result.success(response);
    }

    private Result<ChatResponse> handleTeachingMode(ChatSession session, String userMessage,
                                                    MultipartFile[] attachments, String action, String templateId) {
        log.info("=== 进入 handleTeachingMode ===");
        log.info("SessionId: {}, Stage: {}, Mode: {}, Action: {}",
                session.getId(), session.getStage(), session.getMode(), action);

        ClarificationResult result;
        int layer = 0;
        String layerDesc = "教学模式";

        try {
            result = resolveTeachingModeResult(session, userMessage, attachments, action, templateId);
            log.info("ClarificationResult 获得: message={}, cardType={}",
                    result != null ? result.getMessage() : "null",
                    result != null ? result.getCardType() : "null");
        } catch (Exception e) {
            log.error("教学模式异常: {}", e.getMessage(), e);
            result = ClarificationResult.of("发生错误: " + e.getMessage());
            layer = -1;
        }

        // 保存助手回复消息
        // card_type 标识卡片类型（前端据此渲染卡片样式）
        // card_data 序列化为JSON存储，供前端渲染蓝图卡片
        ObjectMapper saveMapper = new ObjectMapper();
        String cardDataJson = null;
        if (result != null && result.getCardData() != null) {
            try {
                cardDataJson = saveMapper.writeValueAsString(result.getCardData());
            } catch (Exception e) {
                log.error("序列化cardData失败", e);
            }
        }
        String replyContent = result != null && result.getMessage() != null
                ? result.getMessage()
                : "抱歉，发生了错误，请稍后重试。";
        log.info("准备保存消息: replyContent={}", replyContent.substring(0, Math.min(50, replyContent.length())));

        chatSessionService.saveMessage(
                session.getId(), "assistant", replyContent,
                session.getMode(), layer, layerDesc, 0,
                result != null ? result.getCardType() : null, cardDataJson
        );

        ChatSession latestSession = chatSessionService.getSessionWithMessages(session.getId(), session.getUserId());
        String generationStatus = latestSession != null ? latestSession.getGenerationStatus() : null;

        ChatResponse response = ChatResponse.builder()
                .sessionId(session.getSessionId())
                .answer(replyContent)
                .layer(layer)
                .layerDesc(layerDesc)
                .knowledgeSources(null)
                .hasPartialKnowledge(false)
                .costMs(0L)
                .recommendedToUpload(false)
                .generationStatus(generationStatus)
                .cardType(result != null ? result.getCardType() : null)
                .cardData(result != null ? result.getCardData() : null)
                .build();

        log.info("=== handleTeachingMode 完成 ===");
        return Result.success(response);
    }

    private ClarificationResult resolveTeachingModeResult(ChatSession session, String userMessage,
                                                          MultipartFile[] attachments, String action, String templateId) {
        // 暂时关闭 PPT 快速直达：PPT 统一走「澄清 → 确认卡 → 确认后生成」，让用户在生成前
        // 看到并核对解析出的课题/学科/页数（fastPath 用纯启发式提取，不可靠；且秒开秒跑会让
        // 用户白等一份错主题的 PPT，没机会拦）。如需恢复，取消下面注释即可（fastPathForPpt 保留）。
        // if (shouldFastPathPpt(session, userMessage, attachments, action, templateId)) {
        //     log.info("PPT 快速直达触发: sessionId={}, templateId={}", session.getId(), templateId);
        //     return clarificationService.fastPathForPpt(session, userMessage, attachments, templateId);
        // }

        TeachingStageRoute route = teachingStageMachine.routeRequest(session.getStage(), action);
        if (route.resetStage() != null) {
            session.setStage(route.resetStage());
            chatSessionService.updateTeachingMode(
                    session.getId(),
                    session.getMode(),
                    route.resetStage(),
                    session.getTeachingElements()
            );
        }
        if ("confirm".equals(action) && route.operation() == TeachingStageOperation.CONTINUE_CLARIFICATION) {
            log.warn("忽略越阶段 confirm 动作: sessionId={}, stage={}", session.getId(), session.getStage());
        }
        return executeTeachingStageRoute(session, userMessage, attachments, route, templateId);
    }

    private boolean shouldFastPathPpt(ChatSession session, String userMessage,
                                      MultipartFile[] attachments, String action, String templateId) {
        if (!ChatSession.Mode.PPT.equals(session.getMode())) {
            return false;
        }
        if (!hasText(templateId)) {
            return false;
        }
        // 只有"首次发送 / 还在补全"的状态可以快速直达；已在 generating/completed 不要打断。
        String stage = session.getStage();
        boolean stageOk = stage == null || stage.isBlank()
                || ChatSession.Stage.IDLE.equals(stage)
                || ChatSession.Stage.CLARIFYING.equals(stage);
        if (!stageOk) {
            return false;
        }
        // 用户主动 confirm / supplement 的动作仍走原确认流，避免破坏卡片交互
        if ("confirm".equals(action) || "supplement".equals(action)) {
            return false;
        }
        boolean hasMeaningfulText = hasText(userMessage) && userMessage.trim().length() >= 5;
        boolean hasAttachments = attachments != null && attachments.length > 0;
        return hasMeaningfulText || hasAttachments;
    }

    private ClarificationResult executeTeachingStageRoute(ChatSession session, String userMessage,
                                                          MultipartFile[] attachments,
                                                          TeachingStageRoute route,
                                                          String templateId) {
        return switch (route.operation()) {
            case START_CLARIFICATION -> hasText(templateId)
                    ? clarificationService.startClarification(session, userMessage, attachments, templateId)
                    : clarificationService.startClarification(session, userMessage, attachments);
            case CONTINUE_CLARIFICATION -> hasText(templateId)
                    ? clarificationService.continueClarification(session, userMessage, templateId)
                    : clarificationService.continueClarification(session, userMessage);
            case CONFIRM_GENERATION -> clarificationService.confirmAndGenerate(session);
            case RETURN_MESSAGE -> ClarificationResult.of(route.responseMessage());
        };
    }

    /**
     * 对话接口（带附件上传）
     */
    @PostMapping(value = "/with-files", consumes = "multipart/form-data")
    public Result<ChatResponse> chatWithFiles(
            @RequestParam("message") String message,
            @RequestParam(value = "webSearchEnabled", required = false, defaultValue = "false") Boolean webSearchEnabled,
            @RequestParam(value = "attachments", required = false) MultipartFile[] attachments,
            @RequestParam(value = "sessionId", required = false) String sessionId,
            @RequestParam(value = "mode", required = false) String mode,
            @RequestParam(value = "action", required = false) String action,
            @RequestParam(value = "templateId", required = false) String templateId,
            HttpServletRequest httpRequest
    ) {
        Long userId = (Long) httpRequest.getAttribute("userId");

        log.info("收到带附件对话请求: message={}, web={}, userId={}, sessionId={}, mode={}, attachmentCount={}",
                truncate(message, 50), webSearchEnabled, userId, sessionId, mode,
                attachments != null ? attachments.length : 0);

        ChatSession session;
        boolean isNewSession = false;
        if (sessionId == null || sessionId.isBlank()) {
            session = chatSessionService.createSession(userId, message);
            isNewSession = true;
        } else {
            session = chatSessionService.getBySessionId(sessionId);
            if (session == null || !session.getUserId().equals(userId)) {
                return Result.fail("会话不存在");
            }
        }

        Long dbId = session.getId();

        if (isNewSession && mode != null && !mode.isBlank()) {
            session.setMode(mode);
            session.setStage(ChatSession.Stage.IDLE);
        }
        recoverRequestedModeIfMissing(session, mode);

        if (session.getMode() != null && !session.getMode().isBlank()) {
            if (ChatSession.Mode.INTERACTIVE.equals(session.getMode())) {
                chatSessionService.saveMessage(dbId, "user", message, null, null, null, null, null, null);
                return handleInteractiveMode(session, message, attachments);
            }
            String enrichedMessage = enrichMessageWithAttachments(message, attachments);
            chatSessionService.saveMessage(dbId, "user", enrichedMessage, null, null, null, null, null, null);
            return handleTeachingMode(session, enrichedMessage, attachments, action, templateId);
        }

        String enrichedMessage = enrichMessageWithAttachments(message, attachments);

        IntentType intent = intentService.classify(enrichedMessage);
        log.info("意图识别结果: intent={}", intent);

        ChatResponse response = switch (intent) {
            case TEACHING -> handleTeaching(enrichedMessage, null, userId, webSearchEnabled);
            case FACTUAL -> handleFactual(enrichedMessage, null, userId);
            case CASUAL -> handleCasual(enrichedMessage, null);
            case COURSEWARE -> handleCourseware(enrichedMessage);
            case SENSITIVE -> handleSensitive();
            case TOOL -> handleTool(intent);
        };

        chatSessionService.saveMessage(
                dbId, "assistant", response.getAnswer(),
                intent.name(), response.getLayer(), response.getLayerDesc(),
                response.getCostMs() != null ? response.getCostMs().intValue() : null,
                null, null
        );

        log.info("对话完成: userId={}, intent={}, layer={}, cost={}ms",
                userId, intent, response.getLayer(), response.getCostMs());

        response.setSessionId(session.getSessionId());
        return Result.success(response);
    }

    private String enrichMessageWithAttachments(String message, MultipartFile[] attachments) {
        if (attachments == null || attachments.length == 0) {
            return message;
        }

        StringBuilder sb = new StringBuilder();
        sb.append(message).append("\n\n");

        for (MultipartFile file : attachments) {
            if (file == null || file.isEmpty()) continue;

            String fileName = Objects.requireNonNullElse(file.getOriginalFilename(), "未知文件");
            log.info("正在解析附件: fileName={}, size={}", fileName, file.getSize());

            try {
                String lowerName = fileName.toLowerCase();
                
                if (isVideoFile(lowerName)) {
                    String videoContent = processVideoFile(file, fileName);
                    sb.append("【视频附件：").append(fileName).append("】\n")
                      .append(videoContent)
                      .append("\n\n");
                } else {
                    String content = textExtractorService.extractText(file);
                    sb.append("【附件：").append(fileName).append("】\n")
                      .append(content)
                      .append("\n\n");
                }
            } catch (Exception e) {
                log.warn("附件解析失败: fileName={}, error={}", fileName, e.getMessage());
                sb.append("【附件：").append(fileName).append("】（解析失败）\n\n");
            }
        }

        return sb.toString();
    }

    private boolean isVideoFile(String fileName) {
        return fileName.endsWith(".mp4") || fileName.endsWith(".mov") 
            || fileName.endsWith(".avi") || fileName.endsWith(".mkv")
            || fileName.endsWith(".webm") || fileName.endsWith(".flv")
            || fileName.endsWith(".wmv");
    }

    private void recoverRequestedModeIfMissing(ChatSession session, String requestedMode) {
        if (!hasText(requestedMode) || hasText(session.getMode())) {
            return;
        }

        session.setMode(requestedMode);
        if (!hasText(session.getStage())) {
            session.setStage(ChatSession.Stage.IDLE);
        }
        chatSessionService.updateTeachingMode(
                session.getId(),
                requestedMode,
                session.getStage(),
                session.getTeachingElements()
        );
        log.info("Recovered missing session mode from request: sessionId={}, mode={}", session.getId(), requestedMode);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String processVideoFile(MultipartFile file, String fileName) {
        try {
            log.info("开始处理视频文件: {}", fileName);
            
            if (!videoProcessService.isFFmpegAvailable()) {
                return "【视频处理不可用】FFmpeg 未安装或未配置，无法解析视频内容。\n" +
                       "请安装 FFmpeg 并添加到系统 PATH，或在配置中指定 ffmpeg-path。";
            }

            byte[] videoData = file.getBytes();
            IVideoProcessService.VideoAnalysisResult result = 
                    videoProcessService.analyzeVideo(videoData, fileName);

            StringBuilder sb = new StringBuilder();
            sb.append("【视频分析结果】\n");
            sb.append(String.format("视频时长: %.1f 秒\n", result.metadata().duration()));
            sb.append(String.format("分辨率: %dx%d\n", result.metadata().width(), result.metadata().height()));
            sb.append(String.format("分析帧数: %d\n\n", result.totalFramesAnalyzed()));
            sb.append(result.summary());

            return sb.toString();

        } catch (Exception e) {
            log.error("视频处理失败: {}", e.getMessage(), e);
            return "【视频处理失败】" + e.getMessage();
        }
    }

    // ==================== 分流处理（非流式）====================

    /**
     * TEACHING：教学问题 → 三层检索 + LLM（带历史）
     * 修复：不再将历史拼入检索query，改为只传当前问题检索，但LLM调用时带上历史
     */
    private ChatResponse handleTeaching(String message, Long sessionId, Long userId, Boolean webSearchEnabled) {
        long startTime = System.currentTimeMillis();

        List<ChatRequest.ChatMessage> history = getHistoryFromDb(sessionId);

        ITriLevelSearchService.TriLevelSearchRequest searchReq =
                new ITriLevelSearchService.TriLevelSearchRequest(
                        message, message, 5,
                        Boolean.TRUE.equals(webSearchEnabled), userId
                );

        ITriLevelSearchService.TriLevelSearchResult result =
                triLevelSearchService.search(searchReq);

        // 引导消息（知识库为空等），直接返回
        if (result.answer() != null) {
            return toChatResponse(result, null);
        }

        // 使用检索返回的 context（不再重复构建）
        String context = result.context();

        // 根据 promptType 选择系统提示词
        String effectivePrompt = switch (result.promptType()) {
            case "rag" -> systemPromptService.ragPrompt(context);
            case "webSearch" -> systemPromptService.webSearchPrompt();
            case "llmFallback" -> systemPromptService.llmFallbackPrompt();
            default -> systemPromptService.simplePrompt();
        };

        String answer = llmService.chatWithHistory(
                message, toLLMMessages(history), context, effectivePrompt
        );

        long cost = System.currentTimeMillis() - startTime;

        return ChatResponse.builder()
                .answer(answer)
                .layer(result.layer())
                .layerDesc(result.layerDesc())
                .knowledgeSources(null)
                .hasPartialKnowledge(result.hasPartialKnowledge())
                .costMs(cost)
                .recommendedToUpload(result.recommendedUpload() != null)
                .uploadRecommendation(result.recommendedUpload())
                .build();
    }

    /**
     * FACTUAL：事实查询 → 联网搜索 + LLM（带历史）
     */
    private ChatResponse handleFactual(String message, Long sessionId, Long userId) {
        long startTime = System.currentTimeMillis();

        List<ChatRequest.ChatMessage> history = getHistoryFromDb(sessionId);
        IWebSearchService.SearchResult webResult = webSearchService.search(message, 5);

        String answer;
        int layer;
        String layerDesc;

        if (webResult.success() && webResult.total() > 0) {
            String context = contextBuilderService.buildWebContext(webResult);
            answer = llmService.chatWithHistory(
                    message, toLLMMessages(history), context,
                    systemPromptService.webSearchPrompt()
            );
            layer = 2;
            layerDesc = "联网搜索";
        } else {
            answer = llmService.chatWithHistory(
                    message, toLLMMessages(history), null,
                    systemPromptService.llmFallbackPrompt()
            );
            layer = 3;
            layerDesc = "LLM兜底";
        }

        long cost = System.currentTimeMillis() - startTime;

        return ChatResponse.builder()
                .answer(answer)
                .layer(layer)
                .layerDesc(layerDesc)
                .knowledgeSources(null)
                .hasPartialKnowledge(false)
                .costMs(cost)
                .recommendedToUpload(false)
                .build();
    }

    /**
     * CASUAL：闲聊 → LLM直接回答（带历史）
     */
    private ChatResponse handleCasual(String message, Long sessionId) {
        long startTime = System.currentTimeMillis();

        List<ChatRequest.ChatMessage> history = getHistoryFromDb(sessionId);

        String answer = llmService.chatWithHistory(
                message, toLLMMessages(history), null,
                systemPromptService.casualPrompt()
        );

        long cost = System.currentTimeMillis() - startTime;

        return ChatResponse.builder()
                .answer(answer)
                .layer(3)
                .layerDesc("闲聊模式")
                .knowledgeSources(null)
                .hasPartialKnowledge(false)
                .costMs(cost)
                .recommendedToUpload(false)
                .build();
    }

    private ChatResponse handleCourseware(String message) {
        long startTime = System.currentTimeMillis();

        String lower = message.toLowerCase();
        String guidance;

        if (lower.contains("教案") || lower.contains("教学设计")) {
            guidance = "您想生成教案，建议您点击左侧导航栏的「教案生成」按钮，在专属页面填写信息后即可自动生成专业教案。\n\n相比直接描述，这样生成的教案内容更完整、结构更规范。";
        } else if (lower.contains("PPT") || lower.contains("ppt") || lower.contains("课件") || lower.contains("幻灯片")) {
            guidance = "您想生成PPT课件，建议您点击左侧导航栏的「PPT生成」按钮，在专属页面设置标题、知识点和页数后即可自动生成PPT。\n\n您也可以先在知识库中上传相关教材，生成效果会更好。";
        } else if (lower.contains("互动") || lower.contains("练习题")) {
            guidance = "您想生成互动练习题，建议您点击左侧导航栏的「互动内容生成」按钮，选择题目类型和数量即可快速生成课堂互动内容。\n\n支持选择题和填空题两种格式。";
        } else {
            guidance = "您想生成教学内容，建议您使用左侧导航栏的专用功能：\n\n" +
                    "• 「教案生成」→ 生成Word格式教案\n" +
                    "• 「PPT生成」→ 生成PPT课件\n" +
                    "• 「互动内容生成」→ 生成练习题\n\n" +
                    "这些功能针对各自的场景做了专门优化，效果优于直接描述。请告诉您想生成哪类内容？";
        }

        long cost = System.currentTimeMillis() - startTime;

        return ChatResponse.builder()
                .answer(guidance)
                .layer(0)
                .layerDesc("课件引导")
                .knowledgeSources(null)
                .hasPartialKnowledge(false)
                .costMs(cost)
                .recommendedToUpload(false)
                .build();
    }

    private ChatResponse handleTool(IntentType intent) {
        return ChatResponse.builder()
                .answer("您的事务性操作（如文件上传、下载、登录注册等）已转至专用接口处理。请使用侧边栏的上传功能或用户设置页面。")
                .layer(0)
                .layerDesc("工具分流")
                .knowledgeSources(null)
                .hasPartialKnowledge(false)
                .costMs(0L)
                .recommendedToUpload(false)
                .build();
    }

    private ChatResponse handleSensitive() {
        return ChatResponse.builder()
                .answer("抱歉，您的问题涉及敏感内容，暂时无法回答。如有教学相关问题，欢迎继续提问。")
                .layer(0)
                .layerDesc("内容过滤")
                .knowledgeSources(null)
                .hasPartialKnowledge(false)
                .costMs(0L)
                .recommendedToUpload(false)
                .build();
    }

    // ==================== 测试端点 ====================

    @GetMapping("/search")
    public Result<ChatResponse> searchOnly(
            @RequestParam("message") String message,
            @RequestParam("userId") Long userId
    ) {
        log.info("仅检索请求: message={}, userId={}", truncate(message, 50), userId);

        ITriLevelSearchService.TriLevelSearchRequest searchReq =
                new ITriLevelSearchService.TriLevelSearchRequest(
                        message, message, 5, false, userId
                );

        ITriLevelSearchService.TriLevelSearchResult result =
                triLevelSearchService.search(searchReq);

        return Result.success(toChatResponse(result, null));
    }

    @GetMapping("/web-search")
    public Result<IWebSearchService.SearchResult> testWebSearch(
            @RequestParam("query") String query
    ) {
        log.info("联网搜索测试: query={}", query);
        IWebSearchService.SearchResult result = webSearchService.search(query, 5);
        return Result.success(result);
    }

    @GetMapping("/intent")
    public Result<IIntentService.IntentResult> testIntent(
            @RequestParam("query") String query
    ) {
        log.info("意图识别测试: query={}", truncate(query, 50));
        IIntentService.IntentResult result = intentService.classifyWithConfidence(query);
        return Result.success(result);
    }

    // ==================== 私有辅助方法 ====================

    private ChatResponse toChatResponse(ITriLevelSearchService.TriLevelSearchResult result, String sessionId) {
        List<ChatResponse.KnowledgeSource> sources = null;
        if (result.localResult() != null && result.localResult().getResults() != null) {
            sources = result.localResult().getResults().stream()
                    .map(r -> ChatResponse.KnowledgeSource.builder()
                            .chunkId(r.getChunkId())
                            .fileId(r.getFileId())
                            .fileName(r.getFileName())
                            .text(r.getText())
                            .score(r.getScore())
                            .build())
                    .toList();
        }

        return ChatResponse.builder()
                .sessionId(sessionId)
                .answer(result.answer())
                .layer(result.layer())
                .layerDesc(result.layerDesc())
                .knowledgeSources(sources)
                .hasPartialKnowledge(result.hasPartialKnowledge())
                .costMs(result.costMs())
                .recommendedToUpload(result.recommendedUpload() != null)
                .uploadRecommendation(result.recommendedUpload())
                .build();
    }

    private List<ChatRequest.ChatMessage> getHistoryFromDb(Long sessionId) {
        if (sessionId == null) return List.of();
        return chatSessionService.getSessionMessages(sessionId).stream()
                .filter(m -> !"system".equals(m.getRole()))
                .map(m -> new ChatRequest.ChatMessage(m.getRole(), m.getContent()))
                .collect(Collectors.toList());
    }

    private List<ILLMService.ChatMessage> toLLMMessages(List<ChatRequest.ChatMessage> history) {
        if (history == null || history.isEmpty()) return List.of();
        return history.stream()
                .map(m -> new ILLMService.ChatMessage(m.getRole(), m.getContent()))
                .collect(Collectors.toList());
    }

    private String truncate(String str, int maxLen) {
        if (str == null) return null;
        return str.length() <= maxLen ? str : str.substring(0, maxLen) + "...";
    }
}
