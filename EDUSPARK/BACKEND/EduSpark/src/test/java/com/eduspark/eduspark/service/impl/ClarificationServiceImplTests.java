package com.eduspark.eduspark.service.impl;

import com.eduspark.eduspark.dto.chat.BlueprintCardData;
import com.eduspark.eduspark.dto.chat.ClarificationResult;
import com.eduspark.eduspark.dto.courseware.LessonPlanRequest;
import com.eduspark.eduspark.dto.courseware.LessonPlanResponse;
import com.eduspark.eduspark.event.GenerationRequestedEvent;
import com.eduspark.eduspark.pojo.entity.ChatSession;
import com.eduspark.eduspark.service.IChatSessionService;
import com.eduspark.eduspark.service.ICoursewareService;
import com.eduspark.eduspark.service.ILLMService;
import com.eduspark.eduspark.service.ILessonPlanWorkspaceService;
import com.eduspark.eduspark.service.ITextExtractorService;
import com.eduspark.eduspark.service.teaching.TeachingStageMachine;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClarificationServiceImplTests {

    @Mock
    private ILLMService llmService;

    @Mock
    private IChatSessionService chatSessionService;

    @Mock
    private ITextExtractorService textExtractorService;

    @Mock
    private ICoursewareService coursewareService;

    @Mock
    private ILessonPlanWorkspaceService lessonPlanWorkspaceService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private ClarificationServiceImpl clarificationService;

    @BeforeEach
    void setUp() {
        clarificationService = new ClarificationServiceImpl(
                llmService,
                chatSessionService,
                textExtractorService,
                coursewareService,
                lessonPlanWorkspaceService,
                eventPublisher,
                new ObjectMapper(),
                new TeachingStageMachine()
        );
    }

    @Test
    void continueClarificationShouldAcceptNaturalConfirmSentence() {
        ChatSession session = ChatSession.builder()
                .id(1L)
                .mode(ChatSession.Mode.LESSON_PLAN)
                .stage(ChatSession.Stage.CONFIRMING)
                .teachingElements("""
                        {"subject":"\u8bed\u6587","grade":"\u4e09\u5e74\u7ea7","topic":"\u53e4\u8bd7\u4e8c\u9996","duration":40}
                        """)
                .build();

        ClarificationResult result = clarificationService.continueClarification(
                session,
                "\u6211\u786e\u8ba4\u4ee5\u4e0a\u84dd\u56fe\u4fe1\u606f\u65e0\u8bef\uff0c\u8bf7\u5f00\u59cb\u751f\u6210\u3002"
        );

        assertThat(result.getCardType()).isEqualTo("generation_pending");
        assertThat(result.isTriggerGeneration()).isTrue();
        verify(chatSessionService).updateTeachingMode(
                eq(1L),
                eq(ChatSession.Mode.LESSON_PLAN),
                eq(ChatSession.Stage.GENERATING),
                anyString()
        );
        verify(chatSessionService).updateGenerationStatus(
                1L,
                ChatSession.GenerationStatus.PROCESSING,
                "\u6b63\u5728\u751f\u6210\u4e2d\uff0c\u8bf7\u7a0d\u5019\u3002"
        );

        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue()).isInstanceOf(GenerationRequestedEvent.class);

        GenerationRequestedEvent event = (GenerationRequestedEvent) eventCaptor.getValue();
        assertThat(event.getSessionId()).isEqualTo(1L);
        assertThat(event.getMode()).isEqualTo(ChatSession.Mode.LESSON_PLAN);
        assertThat(event.getBlueprint()).containsEntry("subject", "\u8bed\u6587");
        assertThat(event.getBlueprint()).containsEntry("topic", "\u53e4\u8bd7\u4e8c\u9996");
        verify(llmService, never()).chatWithModel(anyList(), nullable(String.class));
    }

    @Test
    void continueClarificationShouldMergeUpdatedDurationBeforeNaturalConfirmGeneration() {
        ChatSession session = ChatSession.builder()
                .id(11L)
                .mode(ChatSession.Mode.LESSON_PLAN)
                .stage(ChatSession.Stage.CONFIRMING)
                .teachingElements("""
                        {"subject":"\u8bed\u6587","grade":"\u4e09\u5e74\u7ea7","topic":"\u53e4\u8bd7\u4e8c\u9996","duration":40}
                        """)
                .build();
        when(llmService.chatWithModel(anyList(), nullable(String.class))).thenReturn("""
                {
                  "duration": 90
                }
                """);

        ClarificationResult result = clarificationService.continueClarification(
                session,
                "\u6211\u786e\u8ba4\u4ee5\u4e0a\u84dd\u56fe\u4fe1\u606f\u65e0\u8bef\uff0c\u628a\u8bfe\u65f6\u957f\u6539\u621090\u5206\u949f\u5e76\u5f00\u59cb\u751f\u6210\u3002"
        );

        assertThat(result.getCardType()).isEqualTo("generation_pending");
        assertThat(result.isTriggerGeneration()).isTrue();
        verify(llmService).chatWithModel(anyList(), nullable(String.class));

        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        GenerationRequestedEvent event = (GenerationRequestedEvent) eventCaptor.getValue();
        assertThat(event.getBlueprint()).containsEntry("duration", 90);
    }

    @Test
    void continueClarificationShouldBackfillMissingCoreFieldsFromHeuristics() {
        ChatSession session = ChatSession.builder()
                .id(12L)
                .mode(ChatSession.Mode.LESSON_PLAN)
                .stage(ChatSession.Stage.CLARIFYING)
                .build();
        when(llmService.chatWithModel(anyList(), nullable(String.class))).thenReturn("""
                {
                  "topic": "\u9178\u78b1\u4e2d\u548c"
                }
                """);

        ClarificationResult result = clarificationService.continueClarification(
                session,
                "\u521d\u4e8c\u5316\u5b66\uff0c45\u5206\u949f"
        );

        assertThat(result.getCardType()).isEqualTo("blueprint_confirm");
        assertThat(result.getCardData()).isInstanceOf(BlueprintCardData.class);

        BlueprintCardData cardData = (BlueprintCardData) result.getCardData();
        assertThat(cardData.getSubject()).isEqualTo("\u5316\u5b66");
        assertThat(cardData.getGrade()).isEqualTo("\u521d\u4e8c");
        assertThat(cardData.getTitle()).isEqualTo("\u9178\u78b1\u4e2d\u548c");
        assertThat(cardData.getDuration()).isEqualTo(45);
    }

    @Test
    void continueClarificationShouldUseHeuristicsWhenExtractionFails() {
        ChatSession session = ChatSession.builder()
                .id(13L)
                .mode(ChatSession.Mode.LESSON_PLAN)
                .stage(ChatSession.Stage.CLARIFYING)
                .teachingElements("""
                        {"topic":"\u9178\u78b1\u4e2d\u548c"}
                        """)
                .build();
        when(llmService.chatWithModel(anyList(), nullable(String.class))).thenThrow(new RuntimeException("boom"));

        ClarificationResult result = clarificationService.continueClarification(
                session,
                "\u521d\u4e8c\u5316\u5b66\uff0c45\u5206\u949f"
        );

        assertThat(result.getCardType()).isEqualTo("blueprint_confirm");
        assertThat(result.getCardData()).isInstanceOf(BlueprintCardData.class);

        BlueprintCardData cardData = (BlueprintCardData) result.getCardData();
        assertThat(cardData.getSubject()).isEqualTo("\u5316\u5b66");
        assertThat(cardData.getGrade()).isEqualTo("\u521d\u4e8c");
        assertThat(cardData.getTitle()).isEqualTo("\u9178\u78b1\u4e2d\u548c");
        assertThat(cardData.getDuration()).isEqualTo(45);
    }

    @Test
    void continueClarificationShouldRecognizeLabeledUniversityFieldsFromHeuristics() {
        ChatSession session = ChatSession.builder()
                .id(14L)
                .mode(ChatSession.Mode.LESSON_PLAN)
                .stage(ChatSession.Stage.CLARIFYING)
                .build();
        when(llmService.chatWithModel(anyList(), nullable(String.class))).thenThrow(new RuntimeException("boom"));

        ClarificationResult result = clarificationService.continueClarification(
                session,
                "\u5b66\u79d1\uff1aJava\uff1b\u5e74\u7ea7\uff1a\u5927\u4e00\uff1b\u8bfe\u9898\uff1ajava\u7684\u57fa\u7840\u8bed\u6cd5\uff1b\u8bfe\u65f6\uff1a40\u5206\u949f\u3002"
        );

        assertThat(result.getCardType()).isEqualTo("blueprint_confirm");
        assertThat(result.getCardData()).isInstanceOf(BlueprintCardData.class);

        BlueprintCardData cardData = (BlueprintCardData) result.getCardData();
        assertThat(cardData.getSubject()).isEqualTo("Java");
        assertThat(cardData.getGrade()).isEqualTo("\u5927\u4e00");
        assertThat(cardData.getTitle()).isEqualTo("java\u7684\u57fa\u7840\u8bed\u6cd5");
        assertThat(cardData.getDuration()).isEqualTo(40);
    }

    @Test
    void continueClarificationShouldKeepConfirmingWhenUserSaysNotToGenerateYet() {
        ChatSession session = ChatSession.builder()
                .id(2L)
                .mode(ChatSession.Mode.LESSON_PLAN)
                .stage(ChatSession.Stage.CONFIRMING)
                .teachingElements("""
                        {"subject":"\u8bed\u6587","grade":"\u4e09\u5e74\u7ea7","topic":"\u53e4\u8bd7\u4e8c\u9996","duration":40}
                        """)
                .build();
        when(llmService.chatWithModel(anyList(), nullable(String.class))).thenReturn("{}");

        ClarificationResult result = clarificationService.continueClarification(
                session,
                "\u5148\u522b\u751f\u6210\uff0c\u6211\u8fd8\u60f3\u8865\u5145\u4e00\u4e2a\u77e5\u8bc6\u70b9\u3002"
        );

        assertThat(result.getCardType()).isEqualTo("blueprint_confirm");
        assertThat(result.isTriggerGeneration()).isFalse();
        verify(chatSessionService).updateTeachingMode(
                eq(2L),
                eq(ChatSession.Mode.LESSON_PLAN),
                eq(ChatSession.Stage.CONFIRMING),
                anyString()
        );
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void confirmAndGenerateShouldReturnFollowUpWhenBlueprintIsIncomplete() {
        ChatSession session = ChatSession.builder()
                .id(3L)
                .mode(ChatSession.Mode.LESSON_PLAN)
                .stage(ChatSession.Stage.CONFIRMING)
                .teachingElements("""
                        {"subject":"\u8bed\u6587","grade":"\u4e09\u5e74\u7ea7","duration":40}
                        """)
                .build();

        ClarificationResult result = clarificationService.confirmAndGenerate(session);

        assertThat(result.getCardType()).isNull();
        assertThat(result.getMessage()).contains("\u8fd8\u5dee\u8bfe\u9898");
        verify(chatSessionService).updateTeachingMode(
                eq(3L),
                eq(ChatSession.Mode.LESSON_PLAN),
                eq(ChatSession.Stage.CLARIFYING),
                anyString()
        );
        verify(eventPublisher, never()).publishEvent(any());
        verify(chatSessionService, never()).updateGenerationStatus(
                eq(3L),
                eq(ChatSession.GenerationStatus.PROCESSING),
                anyString()
        );
    }

    @Test
    void continueClarificationShouldAllowPptBlueprintConfirmWithoutDuration() {
        ChatSession session = ChatSession.builder()
                .id(31L)
                .mode(ChatSession.Mode.PPT)
                .stage(ChatSession.Stage.CLARIFYING)
                .build();
        when(llmService.chatWithModel(anyList(), nullable(String.class))).thenReturn("""
                {
                  "subject": "\u8bed\u6587",
                  "grade": "\u516d\u5e74\u7ea7",
                  "topic": "\u72fc\u7259\u5c71\u4e94\u58ee\u58eb",
                  "slideCount": 10,
                  "style": "\u7b80\u6d01\u8bfe\u5802",
                  "keyPoints": ["\u5386\u53f2\u80cc\u666f", "\u4eba\u7269\u7cbe\u795e"]
                }
                """);

        ClarificationResult result = clarificationService.continueClarification(
                session,
                "\u516d\u5e74\u7ea7\u8bed\u6587\u300a\u72fc\u7259\u5c71\u4e94\u58ee\u58eb\u300b\uff0c\u505a\u621010\u9875\uff0c\u98ce\u683c\u7b80\u6d01\u4e00\u70b9\u3002"
        );

        assertThat(result.getCardType()).isEqualTo("blueprint_confirm");
        assertThat(result.isTriggerGeneration()).isFalse();
        assertThat(result.getCardData()).isInstanceOf(BlueprintCardData.class);

        BlueprintCardData cardData = (BlueprintCardData) result.getCardData();
        assertThat(cardData.getMode()).isEqualTo(ChatSession.Mode.PPT);
        assertThat(cardData.getSubject()).isEqualTo("\u8bed\u6587");
        assertThat(cardData.getGrade()).isEqualTo("\u516d\u5e74\u7ea7");
        assertThat(cardData.getTitle()).isEqualTo("\u72fc\u7259\u5c71\u4e94\u58ee\u58eb");
        assertThat(cardData.getSlideCount()).isEqualTo(10);
        assertThat(cardData.getStyle()).isEqualTo("\u7b80\u6d01\u8bfe\u5802");

        verify(chatSessionService).updateTeachingMode(
                eq(31L),
                eq(ChatSession.Mode.PPT),
                eq(ChatSession.Stage.CONFIRMING),
                anyString()
        );
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void continueClarificationShouldAllowInteractiveBlueprintConfirmWithoutDuration() {
        ChatSession session = ChatSession.builder()
                .id(32L)
                .mode(ChatSession.Mode.INTERACTIVE)
                .stage(ChatSession.Stage.CLARIFYING)
                .build();
        when(llmService.chatWithModel(anyList(), nullable(String.class))).thenReturn("""
                {
                  "subject": "\u751f\u7269",
                  "grade": "\u521d\u4e00",
                  "topic": "\u7ec6\u80de\u7ed3\u6784",
                  "deliveryFormat": "HTML\u4e92\u52a8\u5185\u5bb9",
                  "interactionIdea": "\u505a\u4e00\u4e2a\u53ef\u4ee5\u70b9\u51fb\u7ec6\u80de\u5668\u67e5\u770b\u8bf4\u660e\u7684\u4e92\u52a8\u9875\u9762",
                  "usageScene": "\u8bfe\u5802\u8bb2\u89e3\u540e\u7684\u5c0f\u7ec4\u63a2\u7d22",
                  "visualStyle": "\u5361\u901a\u79d1\u666e",
                  "animationLevel": "\u8f7b\u52a8\u753b",
                  "interactionHints": ["\u70b9\u51fb\u7ec6\u80de\u5668\u67e5\u770b\u529f\u80fd", "\u652f\u6301\u9010\u6b65\u5c55\u5f00\u8bf4\u660e"]
                }
                """);

        ClarificationResult result = clarificationService.continueClarification(
                session,
                "\u521d\u4e00\u751f\u7269\u7ec6\u80de\u7ed3\u6784\uff0c\u6211\u60f3\u505a\u6210HTML\u4e92\u52a8\u5185\u5bb9\uff0c\u53ef\u4ee5\u70b9\u51fb\u4e0d\u540c\u7ec6\u80de\u5668\u770b\u8bf4\u660e\uff0c\u8bfe\u5802\u8bb2\u89e3\u540e\u7ed9\u5c0f\u7ec4\u63a2\u7d22\u3002"
        );

        assertThat(result.getCardType()).isEqualTo("blueprint_confirm");
        assertThat(result.isTriggerGeneration()).isFalse();
        assertThat(result.getCardData()).isInstanceOf(BlueprintCardData.class);

        BlueprintCardData cardData = (BlueprintCardData) result.getCardData();
        assertThat(cardData.getMode()).isEqualTo(ChatSession.Mode.INTERACTIVE);
        assertThat(cardData.getSubject()).isEqualTo("\u751f\u7269");
        assertThat(cardData.getGrade()).isEqualTo("\u521d\u4e00");
        assertThat(cardData.getTitle()).isEqualTo("\u7ec6\u80de\u7ed3\u6784");
        assertThat(cardData.getDeliveryFormat()).isEqualTo("HTML\u4e92\u52a8\u5185\u5bb9");
        assertThat(cardData.getInteractionIdea()).contains("\u70b9\u51fb");
        assertThat(cardData.getInteractionHints()).contains("\u70b9\u51fb\u7ec6\u80de\u5668\u67e5\u770b\u529f\u80fd");

        verify(chatSessionService).updateTeachingMode(
                eq(32L),
                eq(ChatSession.Mode.INTERACTIVE),
                eq(ChatSession.Stage.CONFIRMING),
                anyString()
        );
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void confirmAndGenerateShouldAllowPptBlueprintWithoutDuration() {
        ChatSession session = ChatSession.builder()
                .id(33L)
                .mode(ChatSession.Mode.PPT)
                .stage(ChatSession.Stage.CONFIRMING)
                .teachingElements("""
                        {"subject":"\u8bed\u6587","grade":"\u516d\u5e74\u7ea7","topic":"\u72fc\u7259\u5c71\u4e94\u58ee\u58eb","slideCount":10}
                        """)
                .build();

        ClarificationResult result = clarificationService.confirmAndGenerate(session);

        assertThat(result.getCardType()).isEqualTo("generation_pending");
        assertThat(result.isTriggerGeneration()).isTrue();
        verify(chatSessionService).updateTeachingMode(
                eq(33L),
                eq(ChatSession.Mode.PPT),
                eq(ChatSession.Stage.GENERATING),
                anyString()
        );
        verify(chatSessionService).updateGenerationStatus(
                33L,
                ChatSession.GenerationStatus.PROCESSING,
                "\u6b63\u5728\u751f\u6210\u4e2d\uff0c\u8bf7\u7a0d\u5019\u3002"
        );
        verify(eventPublisher).publishEvent(any(GenerationRequestedEvent.class));
    }

    @Test
    void doAsyncGenerateShouldMoveSessionToCompletedAfterSuccess() {
        clarificationService.doAsyncGenerate(
                4L,
                ChatSession.Mode.LESSON_PLAN,
                Map.of(
                        "subject", "\u8bed\u6587",
                        "grade", "\u4e09\u5e74\u7ea7",
                        "topic", "\u53e4\u8bd7\u4e8c\u9996",
                        "duration", 40
                )
        );

        verify(lessonPlanWorkspaceService).generateInitialDraft(
                eq(4L),
                any()
        );
        verify(chatSessionService).updateTeachingMode(
                eq(4L),
                isNull(),
                eq(ChatSession.Stage.COMPLETED),
                anyString()
        );
    }
}
