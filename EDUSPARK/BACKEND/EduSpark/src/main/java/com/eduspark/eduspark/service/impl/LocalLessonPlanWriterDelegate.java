package com.eduspark.eduspark.service.impl;

import com.eduspark.eduspark.dto.courseware.LessonPlanRequest;
import com.eduspark.eduspark.service.ILLMService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.Consumer;

/**
 * Local fallback writer backed by the local Ollama chat model.
 */
@Component
public class LocalLessonPlanWriterDelegate {

    private final ILLMService llmService;
    private final LessonPlanPromptBuilder promptBuilder;

    public LocalLessonPlanWriterDelegate(ILLMService llmService, LessonPlanPromptBuilder promptBuilder) {
        this.llmService = llmService;
        this.promptBuilder = promptBuilder;
    }

    public String streamDraft(LessonPlanRequest request, Consumer<String> onChunk) {
        List<ILLMService.ChatMessage> messages = List.of(
                new ILLMService.ChatMessage("system", promptBuilder.getSystemPrompt()),
                new ILLMService.ChatMessage("user", promptBuilder.buildDraftPrompt(request))
        );
        Consumer<String> safeConsumer = onChunk != null ? onChunk : chunk -> {
        };
        return llmService.chatStream(messages, safeConsumer);
    }
}
