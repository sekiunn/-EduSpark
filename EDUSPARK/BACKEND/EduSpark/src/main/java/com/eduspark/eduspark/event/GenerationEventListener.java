package com.eduspark.eduspark.event;

import com.eduspark.eduspark.pojo.entity.ChatSession;
import com.eduspark.eduspark.service.IChatSessionService;
import com.eduspark.eduspark.service.IClarificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Runs generation asynchronously after the user confirms the blueprint.
 */
@Slf4j
@Component
public class GenerationEventListener {

    private final IClarificationService clarificationService;
    private final IChatSessionService chatSessionService;

    public GenerationEventListener(IClarificationService clarificationService,
                                   IChatSessionService chatSessionService) {
        this.clarificationService = clarificationService;
        this.chatSessionService = chatSessionService;
    }

    @Async("generationTaskExecutor")
    @EventListener
    public void handleGenerationRequested(GenerationRequestedEvent event) {
        log.info("Receive generation request: sessionId={}, mode={}", event.getSessionId(), event.getMode());
        try {
            clarificationService.doAsyncGenerate(event.getSessionId(), event.getMode(), event.getBlueprint());
        } catch (Exception e) {
            log.error("Async generation failed: sessionId={}", event.getSessionId(), e);
            chatSessionService.updateGenerationStatus(
                    event.getSessionId(),
                    ChatSession.GenerationStatus.FAILED,
                    "生成失败：" + e.getMessage()
            );
        }
    }
}
