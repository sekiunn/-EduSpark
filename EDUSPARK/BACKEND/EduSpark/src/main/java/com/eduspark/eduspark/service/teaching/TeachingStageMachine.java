package com.eduspark.eduspark.service.teaching;

import com.eduspark.eduspark.pojo.entity.ChatSession;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Central state machine for the first-stage teaching flow.
 * It owns stage routing rules and canonical next-state decisions.
 */
@Component
public class TeachingStageMachine {

    public static final String GENERATING_LOCKED_MESSAGE = "正在生成中，请稍候...";
    public static final String COMPLETED_LOCKED_MESSAGE = "本次生成已完成。如需重新生成，请开始新的对话。";
    public static final String INVALID_STAGE_MESSAGE = "会话状态异常，已重置。请重新描述您的需求。";

    private static final Set<String> KNOWN_STAGES = Set.of(
            ChatSession.Stage.IDLE,
            ChatSession.Stage.CLARIFYING,
            ChatSession.Stage.CONFIRMING,
            ChatSession.Stage.GENERATING,
            ChatSession.Stage.COMPLETED
    );

    public TeachingStageRoute routeRequest(String currentStage, String action) {
        String stage = normalizeStage(currentStage);
        if (!KNOWN_STAGES.contains(stage)) {
            return TeachingStageRoute.resetAndReturn(ChatSession.Stage.IDLE, INVALID_STAGE_MESSAGE);
        }
        if ("confirm".equals(action)) {
            return routeConfirmAction(stage);
        }
        if ("supplement".equals(action)) {
            return routeSupplementAction(stage);
        }
        return routeMessageInput(stage);
    }

    public String stageAfterClarification(boolean coreComplete) {
        return coreComplete ? ChatSession.Stage.CONFIRMING : ChatSession.Stage.CLARIFYING;
    }

    public String stageAfterConfirmAttempt(boolean coreComplete) {
        return coreComplete ? ChatSession.Stage.GENERATING : ChatSession.Stage.CLARIFYING;
    }

    public String stageAfterGeneration(boolean success) {
        return success ? ChatSession.Stage.COMPLETED : ChatSession.Stage.CONFIRMING;
    }

    private TeachingStageRoute routeConfirmAction(String stage) {
        if (ChatSession.Stage.GENERATING.equals(stage)) {
            return TeachingStageRoute.returnMessage(GENERATING_LOCKED_MESSAGE);
        }
        if (ChatSession.Stage.COMPLETED.equals(stage)) {
            return TeachingStageRoute.returnMessage(COMPLETED_LOCKED_MESSAGE);
        }
        if (ChatSession.Stage.CONFIRMING.equals(stage)) {
            return TeachingStageRoute.confirmGeneration();
        }
        if (ChatSession.Stage.IDLE.equals(stage)) {
            return TeachingStageRoute.startClarification();
        }
        return TeachingStageRoute.continueClarification();
    }

    private TeachingStageRoute routeSupplementAction(String stage) {
        if (ChatSession.Stage.GENERATING.equals(stage)) {
            return TeachingStageRoute.returnMessage(GENERATING_LOCKED_MESSAGE);
        }
        if (ChatSession.Stage.COMPLETED.equals(stage)) {
            return TeachingStageRoute.returnMessage(COMPLETED_LOCKED_MESSAGE);
        }
        if (ChatSession.Stage.IDLE.equals(stage)) {
            return TeachingStageRoute.startClarification();
        }
        return TeachingStageRoute.continueClarification();
    }

    private TeachingStageRoute routeMessageInput(String stage) {
        return switch (stage) {
            case ChatSession.Stage.IDLE -> TeachingStageRoute.startClarification();
            case ChatSession.Stage.CLARIFYING, ChatSession.Stage.CONFIRMING -> TeachingStageRoute.continueClarification();
            case ChatSession.Stage.GENERATING -> TeachingStageRoute.returnMessage(GENERATING_LOCKED_MESSAGE);
            case ChatSession.Stage.COMPLETED -> TeachingStageRoute.returnMessage(COMPLETED_LOCKED_MESSAGE);
            default -> TeachingStageRoute.resetAndReturn(ChatSession.Stage.IDLE, INVALID_STAGE_MESSAGE);
        };
    }

    private String normalizeStage(String currentStage) {
        return currentStage == null || currentStage.isBlank() ? ChatSession.Stage.IDLE : currentStage;
    }
}
