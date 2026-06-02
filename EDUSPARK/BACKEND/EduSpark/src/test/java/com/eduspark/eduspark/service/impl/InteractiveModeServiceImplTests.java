package com.eduspark.eduspark.service.impl;

import com.eduspark.eduspark.dto.interactive.InteractiveContext;
import com.eduspark.eduspark.dto.interactive.InteractiveModeResult;
import com.eduspark.eduspark.dto.interactive.InteractiveStageCardData;
import com.eduspark.eduspark.mapper.InteractiveDocumentMapper;
import com.eduspark.eduspark.pojo.entity.ChatSession;
import com.eduspark.eduspark.pojo.entity.InteractiveDocument;
import com.eduspark.eduspark.service.IChatSessionService;
import com.eduspark.eduspark.service.IInteractiveWorkspaceService;
import com.eduspark.eduspark.service.ILLMService;
import com.eduspark.eduspark.service.ITextExtractorService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InteractiveModeServiceImplTests {

    @Mock
    private ILLMService llmService;

    @Mock
    private ITextExtractorService textExtractorService;

    @Mock
    private IChatSessionService chatSessionService;

    @Mock
    private IInteractiveWorkspaceService interactiveWorkspaceService;

    @Mock
    private InteractiveDocumentMapper interactiveDocumentMapper;

    private InteractiveModeServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new InteractiveModeServiceImpl(
                llmService,
                textExtractorService,
                chatSessionService,
                interactiveWorkspaceService,
                interactiveDocumentMapper,
                new ObjectMapper()
        );
        ReflectionTestUtils.setField(service, "extractModel", "extract-test");
    }

    @Test
    void handleMessageShouldCreateWorkspaceAndStartGenerationWhenContextIsReady() {
        ChatSession session = ChatSession.builder()
                .id(11L)
                .sessionId("sess-11")
                .userId(8L)
                .mode(ChatSession.Mode.INTERACTIVE)
                .stage(ChatSession.Stage.IDLE)
                .build();
        when(llmService.chatWithModel(anyList(), eq("extract-test"))).thenReturn("""
                {
                  "subject": "物理",
                  "grade": "高中",
                  "topic": "动量守恒",
                  "interactionType": "演示模拟",
                  "studentAction": "点击按钮开始碰撞演示"
                }
                """);
        when(interactiveDocumentMapper.selectLatestBySessionId(11L)).thenReturn(null);
        when(interactiveWorkspaceService.createWorkspace(eq(session), any(InteractiveContext.class)))
                .thenReturn(InteractiveStageCardData.builder()
                        .documentId(21L)
                        .mode(ChatSession.Mode.INTERACTIVE)
                        .modeName("互动内容")
                        .title("动量守恒")
                        .status(InteractiveDocument.Status.PREPARING)
                        .build());

        InteractiveModeResult result = service.handleMessage(session, "做一个高中物理动量守恒碰撞演示", null);

        assertThat(result.getCardType()).isEqualTo("interactive_stage_entry");
        assertThat(result.getCardData()).isInstanceOf(InteractiveStageCardData.class);
        InteractiveStageCardData cardData = (InteractiveStageCardData) result.getCardData();
        assertThat(cardData.getDocumentId()).isEqualTo(21L);
        assertThat(result.getMessage()).contains("互动页面");

        ArgumentCaptor<InteractiveContext> contextCaptor = ArgumentCaptor.forClass(InteractiveContext.class);
        verify(interactiveWorkspaceService).createWorkspace(eq(session), contextCaptor.capture());
        verify(interactiveWorkspaceService).generateInitialDocumentAsync(eq(11L), eq(21L), any(InteractiveContext.class));
        verify(chatSessionService).updateGenerationStatus(
                11L,
                ChatSession.GenerationStatus.PROCESSING,
                "interactive_workspace_generating"
        );
        verify(chatSessionService).updateTeachingMode(
                eq(11L),
                eq(ChatSession.Mode.INTERACTIVE),
                eq(ChatSession.Stage.GENERATING),
                anyString()
        );

        InteractiveContext captured = contextCaptor.getValue();
        assertThat(captured.getSubject()).isEqualTo("物理");
        assertThat(captured.getGrade()).isEqualTo("高中");
        assertThat(captured.getTopic()).isEqualTo("动量守恒");
        assertThat(captured.getInteractionType()).isEqualTo("演示模拟");
        assertThat(captured.getStudentAction()).isEqualTo("点击按钮开始碰撞演示");
        assertThat(captured.getCurrentVersion()).isEqualTo(1);
    }

    @Test
    void handleMessageShouldRefineExistingCompletedDocumentInsteadOfRecreatingWorkspace() {
        ChatSession session = ChatSession.builder()
                .id(12L)
                .sessionId("sess-12")
                .userId(8L)
                .mode(ChatSession.Mode.INTERACTIVE)
                .stage(ChatSession.Stage.COMPLETED)
                .teachingElements("""
                        {
                          "subject": "数学",
                          "grade": "初二",
                          "topic": "一次函数",
                          "interactionType": "参数探索",
                          "currentVersion": 2
                        }
                        """)
                .build();
        InteractiveDocument latestDocument = InteractiveDocument.builder()
                .id(32L)
                .userId(8L)
                .title("一次函数")
                .status(InteractiveDocument.Status.COMPLETED)
                .htmlContent("<html>ready</html>")
                .build();
        when(llmService.chatWithModel(anyList(), eq("extract-test"))).thenReturn("""
                {
                  "notes": "增加更明显的拖动提示"
                }
                """);
        when(interactiveDocumentMapper.selectLatestBySessionId(12L)).thenReturn(latestDocument);

        InteractiveModeResult result = service.handleMessage(session, "把拖动提示做得更明显一点", null);

        assertThat(result.getCardType()).isEqualTo("interactive_stage_entry");
        assertThat(result.getCardData()).isInstanceOf(InteractiveStageCardData.class);
        InteractiveStageCardData cardData = (InteractiveStageCardData) result.getCardData();
        assertThat(cardData.getDocumentId()).isEqualTo(32L);
        assertThat(cardData.getStatus()).isEqualTo(InteractiveDocument.Status.REFINING);

        ArgumentCaptor<InteractiveContext> contextCaptor = ArgumentCaptor.forClass(InteractiveContext.class);
        verify(interactiveWorkspaceService).refineDocumentAsync(
                eq(12L),
                eq(32L),
                contextCaptor.capture(),
                eq("把拖动提示做得更明显一点")
        );
        verify(interactiveWorkspaceService, never()).createWorkspace(any(), any());
        verify(interactiveWorkspaceService, never()).generateInitialDocumentAsync(any(), any(), any());
        verify(chatSessionService).updateGenerationStatus(
                12L,
                ChatSession.GenerationStatus.PROCESSING,
                "interactive_workspace_refining"
        );

        InteractiveContext captured = contextCaptor.getValue();
        assertThat(captured.getSubject()).isEqualTo("数学");
        assertThat(captured.getGrade()).isEqualTo("初二");
        assertThat(captured.getTopic()).isEqualTo("一次函数");
        assertThat(captured.getInteractionType()).isEqualTo("参数探索");
        assertThat(captured.getNotes()).isEqualTo("增加更明显的拖动提示");
        assertThat(captured.getCurrentVersion()).isEqualTo(3);
    }
}
