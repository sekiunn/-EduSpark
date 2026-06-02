package com.eduspark.eduspark.service.impl;

import com.eduspark.eduspark.dto.courseware.LessonPlanRequest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LessonPlanPromptBuilderTests {

    private final LessonPlanPromptBuilder promptBuilder = new LessonPlanPromptBuilder();

    @Test
    void buildDraftPromptShouldMarkKnowledgeAsSecondaryToBaseBlueprint() {
        LessonPlanRequest request = LessonPlanRequest.builder()
                .subject("Java")
                .grade("大一")
                .topic("java的基础语法")
                .duration(40)
                .knowledgePoints(List.of("变量", "流程控制"))
                .referenceText("【化学教案】铝及其化合物复习")
                .build();

        String prompt = promptBuilder.buildDraftPrompt(request);

        assertThat(prompt).contains("Base blueprint (highest priority)");
        assertThat(prompt).contains("Knowledge-base references (secondary priority, optional)");
        assertThat(prompt).contains("If references conflict with the base blueprint, keep the base blueprint.");
        assertThat(prompt).contains("Ignore any reference content that is unrelated to the base blueprint.");
    }
}
