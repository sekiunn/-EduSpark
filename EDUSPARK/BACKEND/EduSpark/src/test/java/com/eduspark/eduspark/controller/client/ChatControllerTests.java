package com.eduspark.eduspark.controller.client;

import com.eduspark.eduspark.dto.chat.ChatRequest;
import com.eduspark.eduspark.dto.chat.ChatResponse;
import com.eduspark.eduspark.dto.chat.ClarificationResult;
import com.eduspark.eduspark.dto.common.Result;
import com.eduspark.eduspark.dto.interactive.InteractiveModeResult;
import com.eduspark.eduspark.dto.interactive.InteractiveStageCardData;
import com.eduspark.eduspark.pojo.entity.ChatSession;
import com.eduspark.eduspark.service.IChatSessionService;
import com.eduspark.eduspark.service.IClarificationService;
import com.eduspark.eduspark.service.IContextBuilderService;
import com.eduspark.eduspark.service.IInteractiveModeService;
import com.eduspark.eduspark.service.IIntentService;
import com.eduspark.eduspark.service.IKnowledgeService;
import com.eduspark.eduspark.service.ILLMService;
import com.eduspark.eduspark.service.ISystemPromptService;
import com.eduspark.eduspark.service.ITextExtractorService;
import com.eduspark.eduspark.service.ITriLevelSearchService;
import com.eduspark.eduspark.service.IVideoProcessService;
import com.eduspark.eduspark.service.IWebSearchService;
import com.eduspark.eduspark.service.teaching.TeachingStageMachine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatControllerTests {

    @Mock
    private IIntentService intentService;

    @Mock
    private ITriLevelSearchService triLevelSearchService;

    @Mock
    private IWebSearchService webSearchService;

    @Mock
    private ILLMService llmService;

    @Mock
    private ITextExtractorService textExtractorService;

    @Mock
    private IChatSessionService chatSessionService;

    @Mock
    private IClarificationService clarificationService;

    @Mock
    private IKnowledgeService knowledgeService;

    @Mock
    private IInteractiveModeService interactiveModeService;

    @Mock
    private ISystemPromptService systemPromptService;

    @Mock
    private IContextBuilderService contextBuilderService;

    @Mock
    private IVideoProcessService videoProcessService;

    private ChatController chatController;

    @BeforeEach
    void setUp() {
        chatController = new ChatController(
                intentService,
                triLevelSearchService,
                webSearchService,
                llmService,
                textExtractorService,
                chatSessionService,
                clarificationService,
                knowledgeService,
                interactiveModeService,
                systemPromptService,
                contextBuilderService,
                videoProcessService,
                new TeachingStageMachine()
        );
    }

    @Test
    void chatShouldReturnGeneratingLockedMessageWhenConfirmArrivesDuringGenerating() {
        ChatSession session = buildTeachingSession(ChatSession.Stage.GENERATING);
        session.setGenerationStatus(ChatSession.GenerationStatus.PROCESSING);
        when(chatSessionService.getBySessionId("session-1")).thenReturn(session);
        when(chatSessionService.getSessionWithMessages(1L, 100L)).thenReturn(session);

        Result<ChatResponse> result = chatController.chat(
                buildRequest("session-1", "我确认以上信息无误，请开始生成。", "confirm"),
                buildHttpRequest(100L)
        );

        assertThat(result.getCode()).isEqualTo(200);
        assertThat(result.getData().getAnswer()).isEqualTo(TeachingStageMachine.GENERATING_LOCKED_MESSAGE);
        assertThat(result.getData().getGenerationStatus()).isEqualTo(ChatSession.GenerationStatus.PROCESSING);
        assertThat(result.getData().getSessionId()).isEqualTo("session-1");
        verifyNoInteractions(clarificationService);
        verify(chatSessionService).saveMessage(
                eq(1L),
                eq("assistant"),
                eq(TeachingStageMachine.GENERATING_LOCKED_MESSAGE),
                eq(ChatSession.Mode.LESSON_PLAN),
                eq(0),
                eq("教学模式"),
                eq(0),
                isNull(),
                isNull()
        );
    }

    @Test
    void chatShouldPersistResetWhenStageIsInvalid() {
        ChatSession session = buildTeachingSession("broken-stage");
        session.setTeachingElements("{\"subject\":\"语文\"}");
        when(chatSessionService.getBySessionId("session-1")).thenReturn(session);
        when(chatSessionService.getSessionWithMessages(1L, 100L)).thenReturn(session);

        Result<ChatResponse> result = chatController.chat(
                buildRequest("session-1", "继续", null),
                buildHttpRequest(100L)
        );

        assertThat(result.getCode()).isEqualTo(200);
        assertThat(result.getData().getAnswer()).isEqualTo(TeachingStageMachine.INVALID_STAGE_MESSAGE);
        verifyNoInteractions(clarificationService);
        verify(chatSessionService).updateTeachingMode(
                eq(1L),
                eq(ChatSession.Mode.LESSON_PLAN),
                eq(ChatSession.Stage.IDLE),
                eq("{\"subject\":\"语文\"}")
        );
    }

    @Test
    void chatShouldDelegateConfirmActionToClarificationServiceDuringConfirming() {
        ChatSession session = buildTeachingSession(ChatSession.Stage.CONFIRMING);
        session.setGenerationStatus(ChatSession.GenerationStatus.PROCESSING);
        when(chatSessionService.getBySessionId("session-1")).thenReturn(session);
        when(chatSessionService.getSessionWithMessages(1L, 100L)).thenReturn(session);
        when(clarificationService.confirmAndGenerate(session)).thenReturn(
                ClarificationResult.generationPending("已开始生成，完成后结果会自动追加到当前对话。", Map.of("status", "processing"))
        );

        Result<ChatResponse> result = chatController.chat(
                buildRequest("session-1", "确认", "confirm"),
                buildHttpRequest(100L)
        );

        assertThat(result.getCode()).isEqualTo(200);
        assertThat(result.getData().getAnswer()).isEqualTo("已开始生成，完成后结果会自动追加到当前对话。");
        assertThat(result.getData().getCardType()).isEqualTo("generation_pending");
        assertThat(result.getData().getSessionId()).isEqualTo("session-1");
        verify(clarificationService).confirmAndGenerate(session);
        verify(clarificationService, never()).startClarification(any(), anyString(), any());
        verify(clarificationService, never()).continueClarification(any(), anyString());
    }

    @Test
    void chatShouldStartClarificationWhenSupplementActionArrivesAtIdle() {
        ChatSession session = buildTeachingSession(ChatSession.Stage.IDLE);
        when(chatSessionService.getBySessionId("session-1")).thenReturn(session);
        when(chatSessionService.getSessionWithMessages(1L, 100L)).thenReturn(session);
        when(clarificationService.startClarification(session, "补充一个活动设计", null))
                .thenReturn(ClarificationResult.of("先把基础信息定下来。"));

        Result<ChatResponse> result = chatController.chat(
                buildRequest("session-1", "补充一个活动设计", "supplement"),
                buildHttpRequest(100L)
        );

        assertThat(result.getCode()).isEqualTo(200);
        assertThat(result.getData().getAnswer()).isEqualTo("先把基础信息定下来。");
        verify(clarificationService).startClarification(session, "补充一个活动设计", null);
    }

    @Test
    void chatStreamShouldRejectTeachingModeRequests() throws Exception {
        ChatRequest request = ChatRequest.builder()
                .message("请帮我生成一份教案")
                .mode(ChatSession.Mode.LESSON_PLAN)
                .build();

        chatController.chatStream(request, buildHttpRequest(100L));

        Thread.sleep(200);

        verifyNoInteractions(chatSessionService);
        verifyNoInteractions(clarificationService);
    }

    @Test
    void chatShouldRecoverInteractiveModeForExistingModeLessSession() {
        ChatSession session = ChatSession.builder()
                .id(1L)
                .sessionId("session-1")
                .userId(100L)
                .mode(null)
                .stage(null)
                .teachingElements(null)
                .build();
        when(chatSessionService.getBySessionId("session-1")).thenReturn(session);
        when(chatSessionService.getSessionWithMessages(1L, 100L)).thenReturn(session);
        when(interactiveModeService.handleMessage(session, "做一个碰撞动画", null))
                .thenReturn(InteractiveModeResult.builder().message("已进入互动模式").build());

        Result<ChatResponse> result = chatController.chat(
                buildRequest("session-1", "做一个碰撞动画", null, ChatSession.Mode.INTERACTIVE),
                buildHttpRequest(100L)
        );

        assertThat(result.getCode()).isEqualTo(200);
        assertThat(result.getData().getAnswer()).isEqualTo("已进入互动模式");
        verify(chatSessionService).updateTeachingMode(
                eq(1L),
                eq(ChatSession.Mode.INTERACTIVE),
                eq(ChatSession.Stage.IDLE),
                isNull()
        );
        verify(interactiveModeService).handleMessage(session, "做一个碰撞动画", null);
    }

    @Test
    void chatShouldReturnInteractiveStageCardToFrontend() {
        ChatSession session = ChatSession.builder()
                .id(1L)
                .sessionId("session-1")
                .userId(100L)
                .mode(ChatSession.Mode.INTERACTIVE)
                .stage(ChatSession.Stage.GENERATING)
                .teachingElements("{}")
                .generationStatus(ChatSession.GenerationStatus.PROCESSING)
                .build();
        InteractiveStageCardData cardData = InteractiveStageCardData.builder()
                .documentId(6L)
                .mode(ChatSession.Mode.INTERACTIVE)
                .modeName("互动内容")
                .title("动量守恒")
                .status("preparing")
                .build();
        when(chatSessionService.getBySessionId("session-1")).thenReturn(session);
        when(chatSessionService.getSessionWithMessages(1L, 100L)).thenReturn(session);
        when(interactiveModeService.handleMessage(session, "做一个动量守恒碰撞演示", null))
                .thenReturn(InteractiveModeResult.builder()
                        .message("已进入互动工作区")
                        .cardType("interactive_stage_entry")
                        .cardData(cardData)
                        .build());

        Result<ChatResponse> result = chatController.chat(
                buildRequest("session-1", "做一个动量守恒碰撞演示", null),
                buildHttpRequest(100L)
        );

        assertThat(result.getCode()).isEqualTo(200);
        assertThat(result.getData().getAnswer()).isEqualTo("已进入互动工作区");
        assertThat(result.getData().getCardType()).isEqualTo("interactive_stage_entry");
        assertThat(result.getData().getCardData()).isInstanceOf(InteractiveStageCardData.class);
        InteractiveStageCardData responseCard = (InteractiveStageCardData) result.getData().getCardData();
        assertThat(responseCard.getDocumentId()).isEqualTo(6L);
        assertThat(result.getData().getGenerationStatus()).isEqualTo(ChatSession.GenerationStatus.PROCESSING);
        verify(chatSessionService).saveMessage(
                eq(1L),
                eq("assistant"),
                eq("已进入互动工作区"),
                eq(ChatSession.Mode.INTERACTIVE),
                eq(0),
                eq("互动模式"),
                eq(0),
                eq("interactive_stage_entry"),
                anyString()
        );
    }

    private ChatRequest buildRequest(String sessionId, String message, String action) {
        return buildRequest(sessionId, message, action, null);
    }

    private ChatRequest buildRequest(String sessionId, String message, String action, String mode) {
        return ChatRequest.builder()
                .sessionId(sessionId)
                .message(message)
                .action(action)
                .mode(mode)
                .build();
    }

    private MockHttpServletRequest buildHttpRequest(Long userId) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute("userId", userId);
        return request;
    }

    private ChatSession buildTeachingSession(String stage) {
        return ChatSession.builder()
                .id(1L)
                .sessionId("session-1")
                .userId(100L)
                .mode(ChatSession.Mode.LESSON_PLAN)
                .stage(stage)
                .teachingElements("{}")
                .build();
    }
}
