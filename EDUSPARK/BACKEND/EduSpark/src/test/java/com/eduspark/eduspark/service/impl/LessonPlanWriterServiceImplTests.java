package com.eduspark.eduspark.service.impl;

import com.eduspark.eduspark.dto.courseware.LessonPlanRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LessonPlanWriterServiceImplTests {

    @Mock
    private ExternalLessonPlanWriterDelegate externalWriter;

    @Mock
    private LocalLessonPlanWriterDelegate localWriter;

    private LessonPlanWriterServiceImpl lessonPlanWriterService;

    @BeforeEach
    void setUp() {
        lessonPlanWriterService = new LessonPlanWriterServiceImpl(externalWriter, localWriter);
    }

    @Test
    void streamDraftShouldUseExternalWriterWhenConfigured() {
        setWriterMode("external");
        setFallbackToLocal(false);
        when(externalWriter.isConfigured()).thenReturn(true);
        when(externalWriter.getConfiguredModel()).thenReturn("qwen3.5-plus");
        when(externalWriter.getConfiguredBaseUrl()).thenReturn("https://dashscope.aliyuncs.com/compatible-mode/v1");
        when(externalWriter.streamDraft(any(), any())).thenReturn("# Java 教案");

        String result = lessonPlanWriterService.streamDraft(sampleRequest(), chunk -> {
        });

        assertThat(result).isEqualTo("# Java 教案");
        verify(externalWriter).streamDraft(any(), any());
        verify(localWriter, never()).streamDraft(any(), any());
    }

    @Test
    void streamDraftShouldThrowWhenExternalWriterIsNotConfiguredAndFallbackDisabled() {
        setWriterMode("external");
        setFallbackToLocal(false);
        when(externalWriter.isConfigured()).thenReturn(false);

        assertThatThrownBy(() -> lessonPlanWriterService.streamDraft(sampleRequest(), chunk -> {
        }))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("External lesson-plan writer is not configured");

        verify(localWriter, never()).streamDraft(any(), any());
    }

    @Test
    void streamDraftShouldThrowWhenExternalWriterFailsAndFallbackDisabled() {
        setWriterMode("external");
        setFallbackToLocal(false);
        when(externalWriter.isConfigured()).thenReturn(true);
        when(externalWriter.getConfiguredModel()).thenReturn("qwen3.5-plus");
        when(externalWriter.getConfiguredBaseUrl()).thenReturn("https://dashscope.aliyuncs.com/compatible-mode/v1");
        when(externalWriter.streamDraft(any(), any()))
                .thenThrow(new IllegalStateException("External lesson-plan writing failed"));

        assertThatThrownBy(() -> lessonPlanWriterService.streamDraft(sampleRequest(), chunk -> {
        }))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("External lesson-plan writing failed");

        verify(localWriter, never()).streamDraft(any(), any());
    }

    @Test
    void streamDraftShouldFallbackToLocalWhenExplicitlyEnabled() {
        setWriterMode("external");
        setFallbackToLocal(true);
        when(externalWriter.isConfigured()).thenReturn(true);
        when(externalWriter.getConfiguredModel()).thenReturn("qwen3.5-plus");
        when(externalWriter.getConfiguredBaseUrl()).thenReturn("https://dashscope.aliyuncs.com/compatible-mode/v1");
        when(externalWriter.streamDraft(any(), any()))
                .thenThrow(new IllegalStateException("External lesson-plan writing failed"));
        when(localWriter.streamDraft(any(), any())).thenReturn("# 本地回退教案");

        String result = lessonPlanWriterService.streamDraft(sampleRequest(), chunk -> {
        });

        assertThat(result).isEqualTo("# 本地回退教案");
        verify(localWriter).streamDraft(any(), any());
    }

    private void setWriterMode(String writerMode) {
        ReflectionTestUtils.setField(lessonPlanWriterService, "writerMode", writerMode);
    }

    private void setFallbackToLocal(boolean fallbackToLocal) {
        ReflectionTestUtils.setField(lessonPlanWriterService, "fallbackToLocal", fallbackToLocal);
    }

    private LessonPlanRequest sampleRequest() {
        return LessonPlanRequest.builder()
                .subject("Java")
                .grade("大一")
                .topic("java的基础语法")
                .duration(40)
                .build();
    }
}
