package com.eduspark.eduspark.service.impl;

import com.eduspark.eduspark.dto.chat.TeachingBlueprint;
import com.eduspark.eduspark.dto.knowledge.KnowledgeSearchResponse;
import com.eduspark.eduspark.service.IKnowledgeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LessonPlanBlueprintEnrichmentServiceImplTests {

    @Mock
    private IKnowledgeService knowledgeService;

    private LessonPlanBlueprintEnrichmentServiceImpl enrichmentService;

    @BeforeEach
    void setUp() {
        enrichmentService = new LessonPlanBlueprintEnrichmentServiceImpl(knowledgeService);
    }

    @Test
    void enrichBlueprintShouldAttachRelevantKnowledgeWithoutChangingCoreFields() {
        TeachingBlueprint sourceBlueprint = TeachingBlueprint.fromLegacyMap(Map.of(
                "subject", "Java",
                "grade", "大一",
                "topic", "java的基础语法",
                "duration", 40
        ));
        when(knowledgeService.search(any())).thenReturn(KnowledgeSearchResponse.builder()
                .results(List.of(buildRelevantJavaResult()))
                .build());

        TeachingBlueprint result = enrichmentService.enrichBlueprint(1L, sourceBlueprint);

        assertThat(result.getCore().getSubject()).isEqualTo("Java");
        assertThat(result.getCore().getGrade()).isEqualTo("大一");
        assertThat(result.getCore().getTopic()).isEqualTo("java的基础语法");
        assertThat(result.getCore().getDuration()).isEqualTo(40);
        assertThat(String.valueOf(result.getExtension("referenceText"))).contains("Java");
        assertThat(String.valueOf(result.getExtension("knowledgeSources"))).contains("Java");
        verify(knowledgeService).search(any());
    }

    @Test
    void enrichBlueprintShouldDropUnrelatedKnowledgeHits() {
        TeachingBlueprint sourceBlueprint = TeachingBlueprint.fromLegacyMap(Map.of(
                "subject", "Java",
                "grade", "大一",
                "topic", "java的基础语法",
                "duration", 40
        ));
        when(knowledgeService.search(any())).thenReturn(KnowledgeSearchResponse.builder()
                .results(List.of(
                        KnowledgeSearchResponse.KnowledgeSearchResult.builder()
                                .fileId(101L)
                                .fileName("高中化学铝及其化合物知识点复习教案")
                                .text("铝和氢氧化钠反应生成偏铝酸钠。")
                                .score(0.08F)
                                .build()
                ))
                .build());

        TeachingBlueprint result = enrichmentService.enrichBlueprint(1L, sourceBlueprint);

        assertThat(result.getCore().getSubject()).isEqualTo("Java");
        assertThat(result.getCore().getTopic()).isEqualTo("java的基础语法");
        assertThat(result.getExtension("referenceText")).isNull();
        assertThat(result.getExtension("knowledgeSources")).isNull();
        verify(knowledgeService).search(any());
    }

    @Test
    void enrichBlueprintShouldReturnSourceBlueprintWhenSearchIsEmpty() {
        TeachingBlueprint sourceBlueprint = TeachingBlueprint.fromLegacyMap(Map.of(
                "subject", "Java",
                "grade", "大一",
                "topic", "java的基础语法",
                "duration", 40
        ));
        when(knowledgeService.search(any())).thenReturn(KnowledgeSearchResponse.builder()
                .results(List.of())
                .build());

        TeachingBlueprint result = enrichmentService.enrichBlueprint(1L, sourceBlueprint);

        assertThat(result.getCore().getSubject()).isEqualTo("Java");
        assertThat(result.getCore().getGrade()).isEqualTo("大一");
        assertThat(result.getCore().getTopic()).isEqualTo("java的基础语法");
        assertThat(result.getExtension("referenceText")).isNull();
        verify(knowledgeService).search(any());
    }

    private KnowledgeSearchResponse.KnowledgeSearchResult buildRelevantJavaResult() {
        return KnowledgeSearchResponse.KnowledgeSearchResult.builder()
                .fileId(201L)
                .fileName("Java 基础语法课堂示例")
                .text("Java 基础语法包括变量、数据类型、JDK JRE JVM 和 HelloWorld 示例。")
                .score(0.21F)
                .build();
    }
}
