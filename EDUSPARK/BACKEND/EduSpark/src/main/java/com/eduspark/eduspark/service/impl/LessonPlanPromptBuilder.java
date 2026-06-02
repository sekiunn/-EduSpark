package com.eduspark.eduspark.service.impl;

import com.eduspark.eduspark.dto.courseware.LessonPlanRequest;
import org.springframework.stereotype.Component;

/**
 * Shared prompt builder for lesson-plan drafting.
 */
@Component
public class LessonPlanPromptBuilder {

    private static final String SYSTEM_PROMPT = """
            You are a senior lesson-plan writer.
            Produce only the final lesson plan in clean Markdown.
            The lesson plan must be written in Chinese.
            The base blueprint is the highest-priority source of truth.
            Knowledge-base references are secondary supporting evidence.
            If a knowledge-base reference conflicts with the base blueprint, ignore the conflicting reference.
            If a knowledge-base reference conflicts with your general knowledge but does not conflict with the base blueprint, prefer the knowledge-base reference.
            Do not change the subject, grade, topic, duration, or the core teaching scope defined by the base blueprint.
            Do not output explanations, analysis, XML, or code fences.
            """;

    public String getSystemPrompt() {
        return SYSTEM_PROMPT;
    }

    public String buildDraftPrompt(LessonPlanRequest request) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Generate a complete lesson plan in Chinese Markdown.\n\n");
        prompt.append("Base blueprint (highest priority):\n");
        appendLine(prompt, "Subject", request.getSubject());
        appendLine(prompt, "Grade", request.getGrade());
        appendLine(prompt, "Topic", request.getTopic());
        if (request.getDuration() != null) {
            prompt.append("Duration: ").append(request.getDuration()).append(" minutes\n");
        }
        if (request.getKnowledgePoints() != null && !request.getKnowledgePoints().isEmpty()) {
            prompt.append("Knowledge points: ").append(String.join("; ", request.getKnowledgePoints())).append('\n');
        }
        appendLine(prompt, "Teaching goals", request.getTeachingGoal());
        appendLine(prompt, "Key points", request.getKeyPoint());
        appendLine(prompt, "Difficult points", request.getDifficultPoint());
        appendLine(prompt, "Teaching method", request.getTeachingMethod());
        appendLine(prompt, "Additional requirements", request.getUserDescription());

        prompt.append("\nKnowledge-base references (secondary priority, optional):\n");
        if (hasText(request.getReferenceText())) {
            prompt.append(request.getReferenceText().trim()).append('\n');
        } else {
            prompt.append("No reliable reference provided.\n");
        }

        prompt.append("\nPriority rules:\n");
        prompt.append("1. Follow the base blueprint first.\n");
        prompt.append("2. Use knowledge-base references only to enrich domain facts, terminology, examples, activities, and constraints.\n");
        prompt.append("3. Ignore any reference content that is unrelated to the base blueprint.\n");
        prompt.append("4. If references conflict with the base blueprint, keep the base blueprint.\n");
        prompt.append("5. If references conflict with general world knowledge but fit the base blueprint context, keep the references.\n");

        prompt.append("\nOutput requirements:\n");
        prompt.append("1. Output Chinese Markdown only.\n");
        prompt.append("2. Use headings, bullet lists, and numbered steps when useful.\n");
        prompt.append("3. Include at least these sections in Chinese: teaching goals, teaching key points and difficulties, teaching process, classroom exercises, homework.\n");
        prompt.append("4. Keep the article tightly aligned with the base blueprint topic.\n");
        prompt.append("5. Do not add explanations outside the lesson plan.\n");
        return prompt.toString();
    }

    private void appendLine(StringBuilder builder, String label, String value) {
        if (hasText(value)) {
            builder.append(label).append(": ").append(value.trim()).append('\n');
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
