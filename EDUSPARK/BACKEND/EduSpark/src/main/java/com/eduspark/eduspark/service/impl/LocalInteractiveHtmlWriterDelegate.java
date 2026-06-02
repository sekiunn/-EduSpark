package com.eduspark.eduspark.service.impl;

import com.eduspark.eduspark.dto.interactive.InteractiveContext;
import com.eduspark.eduspark.service.ILLMService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.Consumer;

/**
 * Local interactive HTML writer backed by the default Ollama chat service.
 */
@Component
public class LocalInteractiveHtmlWriterDelegate {

    private final ILLMService llmService;
    private final InteractivePromptBuilder promptBuilder;

    public LocalInteractiveHtmlWriterDelegate(ILLMService llmService,
                                              InteractivePromptBuilder promptBuilder) {
        this.llmService = llmService;
        this.promptBuilder = promptBuilder;
    }

    public String streamInitialHtml(InteractiveContext context, Consumer<String> onChunk) {
        List<ILLMService.ChatMessage> messages = List.of(
                new ILLMService.ChatMessage("system", promptBuilder.getSystemPrompt()),
                new ILLMService.ChatMessage("user", promptBuilder.buildInitialPrompt(context))
        );
        return llmService.chatStream(messages, onChunk != null ? onChunk : chunk -> {
        });
    }

    public String streamRefinedHtml(InteractiveContext context,
                                    String currentHtml,
                                    String instruction,
                                    Consumer<String> onChunk) {
        List<ILLMService.ChatMessage> messages = List.of(
                new ILLMService.ChatMessage("system", promptBuilder.getSystemPrompt()),
                new ILLMService.ChatMessage("user", promptBuilder.buildRefinePrompt(context, currentHtml, instruction))
        );
        return llmService.chatStream(messages, onChunk != null ? onChunk : chunk -> {
        });
    }

    public String streamPrompt(String systemPrompt, String userPrompt, Consumer<String> onChunk) {
        List<ILLMService.ChatMessage> messages = List.of(
                new ILLMService.ChatMessage("system", systemPrompt),
                new ILLMService.ChatMessage("user", userPrompt)
        );
        return llmService.chatStream(messages, onChunk != null ? onChunk : chunk -> {
        });
    }
}
