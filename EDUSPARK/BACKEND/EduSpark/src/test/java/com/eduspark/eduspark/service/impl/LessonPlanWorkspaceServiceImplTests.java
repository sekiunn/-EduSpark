package com.eduspark.eduspark.service.impl;

import com.eduspark.eduspark.dto.chat.TeachingBlueprint;
import com.eduspark.eduspark.dto.chat.TeachingCore;
import com.eduspark.eduspark.dto.courseware.LessonPlanRequest;
import com.eduspark.eduspark.dto.courseware.LessonPlanResponse;
import com.eduspark.eduspark.exception.BusinessException;
import com.eduspark.eduspark.mapper.LessonPlanDocumentMapper;
import com.eduspark.eduspark.pojo.entity.LessonPlanDocument;
import com.eduspark.eduspark.service.IChatSessionService;
import com.eduspark.eduspark.service.ICoursewareService;
import com.eduspark.eduspark.service.ILessonPlanBlueprintEnrichmentService;
import com.eduspark.eduspark.service.ILessonPlanWorkspaceStreamService;
import com.eduspark.eduspark.service.ILessonPlanWriterService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LessonPlanWorkspaceServiceImplTests {

    @Mock
    private LessonPlanDocumentMapper lessonPlanDocumentMapper;

    @Mock
    private ILessonPlanBlueprintEnrichmentService blueprintEnrichmentService;

    @Mock
    private ILessonPlanWriterService lessonPlanWriterService;

    @Mock
    private ICoursewareService coursewareService;

    @Mock
    private IChatSessionService chatSessionService;

    @Mock
    private ILessonPlanWorkspaceStreamService workspaceStreamService;

    private LessonPlanWorkspaceServiceImpl lessonPlanWorkspaceService;

    @BeforeEach
    void setUp() {
        lessonPlanWorkspaceService = new LessonPlanWorkspaceServiceImpl(
                lessonPlanDocumentMapper,
                blueprintEnrichmentService,
                lessonPlanWriterService,
                coursewareService,
                chatSessionService,
                workspaceStreamService,
                new ObjectMapper()
        );
    }

    @Test
    void updateDocumentContentShouldRejectBlankOverwriteWhenExistingContentPresent() {
        LessonPlanDocument document = LessonPlanDocument.builder()
                .id(1L)
                .userId(1L)
                .status(LessonPlanDocument.Status.COMPLETED)
                .content("# Existing Lesson Plan\n\nBody content")
                .build();
        when(lessonPlanDocumentMapper.selectOwnedById(1L, 1L)).thenReturn(document);

        assertThatThrownBy(() -> lessonPlanWorkspaceService.updateDocumentContent(1L, 1L, "<p></p>"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("正文内容为空");

        verify(lessonPlanDocumentMapper, never()).updateById(any(LessonPlanDocument.class));
        verify(workspaceStreamService, never()).publishSnapshot(any(), any());
    }

    @Test
    void updateDocumentContentShouldRejectSaveWhenDraftNotCompleted() {
        LessonPlanDocument document = LessonPlanDocument.builder()
                .id(7L)
                .userId(1L)
                .status(LessonPlanDocument.Status.DRAFTING)
                .content("Partial content")
                .build();
        when(lessonPlanDocumentMapper.selectOwnedById(7L, 1L)).thenReturn(document);

        assertThatThrownBy(() -> lessonPlanWorkspaceService.updateDocumentContent(7L, 1L, "## New Section"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("尚未生成完成");

        verify(lessonPlanDocumentMapper, never()).updateById(any(LessonPlanDocument.class));
        verify(workspaceStreamService, never()).publishSnapshot(any(), any());
    }

    @Test
    void updateDocumentContentShouldPersistWhenIncomingContentHasBody() {
        LessonPlanDocument document = LessonPlanDocument.builder()
                .id(2L)
                .userId(1L)
                .title("test-plan")
                .status(LessonPlanDocument.Status.COMPLETED)
                .content("Old content")
                .build();
        when(lessonPlanDocumentMapper.selectOwnedById(2L, 1L)).thenReturn(document);

        var response = lessonPlanWorkspaceService.updateDocumentContent(2L, 1L, "## New Section\n\nFresh body");

        assertThat(response.getContent()).isEqualTo("## New Section\n\nFresh body");
        assertThat(document.getPreview()).contains("New Section");
        verify(lessonPlanDocumentMapper).updateById(document);
        verify(workspaceStreamService).publishSnapshot(2L, response);
    }

    @Test
    void generateInitialDraftShouldStreamDraftDirectlyToWorkspace() {
        LessonPlanDocument document = LessonPlanDocument.builder()
                .id(3L)
                .sessionId(38L)
                .userId(1L)
                .title("java的基础语法")
                .status(LessonPlanDocument.Status.PREPARING)
                .sourceBlueprintJson("{}")
                .content("")
                .preview("")
                .build();
        TeachingBlueprint blueprint = buildJavaBlueprint();
        TeachingBlueprint enrichedBlueprint = TeachingBlueprint.fromLegacyMap(Map.of(
                "subject", "Java",
                "grade", "大一",
                "topic", "java的基础语法",
                "duration", 40,
                "knowledgePoints", List.of("Java基础语法", "JDK、JRE、JVM", "HelloWorld"),
                "referenceText", "【Java 基础语法课堂示例】\nHelloWorld 示例与 JDK、JRE、JVM 关系。",
                "knowledgeSources", List.of(Map.of(
                        "fileId", 201L,
                        "fileName", "Java 基础语法课堂示例",
                        "excerpt", "HelloWorld 示例与 JDK、JRE、JVM 关系。",
                        "score", 0.21F
                ))
        ));

        when(lessonPlanDocumentMapper.selectLatestBySessionId(38L)).thenReturn(document);
        when(blueprintEnrichmentService.enrichBlueprint(1L, blueprint)).thenReturn(enrichedBlueprint);
        when(lessonPlanWriterService.streamDraft(any(), any())).thenAnswer(invocation -> {
            Consumer<String> onChunk = invocation.getArgument(1);
            onChunk.accept("# Java基础语法教学设计");
            onChunk.accept("\n\n## 教学目标\n- 理解 Java 基础语法。");
            return "# Java基础语法教学设计\n\n## 教学目标\n- 理解 Java 基础语法。";
        });

        lessonPlanWorkspaceService.generateInitialDraft(38L, blueprint);

        ArgumentCaptor<LessonPlanRequest> requestCaptor = ArgumentCaptor.forClass(LessonPlanRequest.class);
        verify(lessonPlanWriterService).streamDraft(requestCaptor.capture(), any());
        assertThat(requestCaptor.getValue().getSubject()).isEqualTo("Java");
        assertThat(requestCaptor.getValue().getTopic()).isEqualTo("java的基础语法");
        assertThat(requestCaptor.getValue().getReferenceText()).contains("HelloWorld");
        assertThat(document.getContent()).contains("Java基础语法");
        assertThat(document.getStatus()).isEqualTo(LessonPlanDocument.Status.COMPLETED);
        verify(workspaceStreamService, times(2)).publishContentDelta(eq(3L), any());
        verify(workspaceStreamService).publishCompleted(eq(3L), any());
        verify(coursewareService, never()).exportLessonPlanDocument(any(), any());
    }

    @Test
    void generateInitialDraftShouldFailWhenWriterReturnsBlankContent() {
        LessonPlanDocument document = LessonPlanDocument.builder()
                .id(4L)
                .sessionId(39L)
                .userId(1L)
                .title("java的基础语法")
                .status(LessonPlanDocument.Status.PREPARING)
                .sourceBlueprintJson("{}")
                .content("")
                .preview("")
                .build();
        TeachingBlueprint blueprint = buildJavaBlueprint();

        when(lessonPlanDocumentMapper.selectLatestBySessionId(39L)).thenReturn(document);
        when(blueprintEnrichmentService.enrichBlueprint(1L, blueprint)).thenReturn(blueprint);
        when(lessonPlanWriterService.streamDraft(any(), any())).thenReturn("");

        lessonPlanWorkspaceService.generateInitialDraft(39L, blueprint);

        assertThat(document.getStatus()).isEqualTo(LessonPlanDocument.Status.FAILED);
        verify(workspaceStreamService).publishFailed(eq(4L), any());
        verify(coursewareService, never()).exportLessonPlanDocument(any(), any());
    }

    @Test
    void exportDocumentShouldGenerateDocxOnDemand() {
        LessonPlanDocument document = LessonPlanDocument.builder()
                .id(5L)
                .userId(1L)
                .status(LessonPlanDocument.Status.COMPLETED)
                .content("# Java基础语法教学设计\n\n## 教学目标\n- 理解 Java")
                .sourceBlueprintJson("{\"subject\":\"Java\",\"grade\":\"\\u5927\\u4e00\",\"topic\":\"java\\u7684\\u57fa\\u7840\\u8bed\\u6cd5\",\"duration\":40}")
                .build();
        when(lessonPlanDocumentMapper.selectOwnedById(5L, 1L)).thenReturn(document);
        when(coursewareService.exportLessonPlanDocument(any(), any()))
                .thenReturn(LessonPlanResponse.builder()
                        .success(true)
                        .downloadUrl("/api/v1/courseware/download?path=lesson-plan.docx")
                        .filePath("lesson-plan.docx")
                        .preview("Java基础语法教学设计")
                        .build());

        var response = lessonPlanWorkspaceService.exportDocument(5L, 1L);

        assertThat(response.getDownloadUrl()).isEqualTo("/api/v1/courseware/download?path=lesson-plan.docx");
        assertThat(document.getExportFilePath()).isEqualTo("lesson-plan.docx");
        assertThat(document.getPreview()).contains("Java");
        verify(workspaceStreamService).publishSnapshot(5L, response);
    }

    @Test
    void exportDocumentShouldRejectIncompleteDraft() {
        LessonPlanDocument document = LessonPlanDocument.builder()
                .id(6L)
                .userId(1L)
                .status(LessonPlanDocument.Status.DRAFTING)
                .content("# Partial draft")
                .build();
        when(lessonPlanDocumentMapper.selectOwnedById(6L, 1L)).thenReturn(document);

        assertThatThrownBy(() -> lessonPlanWorkspaceService.exportDocument(6L, 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("导出");

        verify(coursewareService, never()).exportLessonPlanDocument(any(), any());
    }

    private TeachingBlueprint buildJavaBlueprint() {
        return TeachingBlueprint.builder()
                .core(TeachingCore.builder()
                        .subject("Java")
                        .grade("大一")
                        .topic("java的基础语法")
                        .duration(40)
                        .build())
                .extensions(Map.of(
                        "knowledgePoints", List.of("Java基础语法", "JDK、JRE、JVM", "HelloWorld")
                ))
                .build();
    }
}
