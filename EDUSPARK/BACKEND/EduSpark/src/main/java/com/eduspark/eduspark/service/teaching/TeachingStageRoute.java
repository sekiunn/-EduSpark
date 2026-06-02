package com.eduspark.eduspark.service.teaching;

/**
 * Routing decision produced by the teaching-stage state machine.
 *
 * @param operation operation that the caller should execute
 * @param responseMessage optional plain-text response for locked or invalid states
 * @param resetStage optional stage to assign before returning
 */
public record TeachingStageRoute(TeachingStageOperation operation, String responseMessage, String resetStage) {

    public static TeachingStageRoute startClarification() {
        return new TeachingStageRoute(TeachingStageOperation.START_CLARIFICATION, null, null);
    }

    public static TeachingStageRoute continueClarification() {
        return new TeachingStageRoute(TeachingStageOperation.CONTINUE_CLARIFICATION, null, null);
    }

    public static TeachingStageRoute confirmGeneration() {
        return new TeachingStageRoute(TeachingStageOperation.CONFIRM_GENERATION, null, null);
    }

    public static TeachingStageRoute returnMessage(String responseMessage) {
        return new TeachingStageRoute(TeachingStageOperation.RETURN_MESSAGE, responseMessage, null);
    }

    public static TeachingStageRoute resetAndReturn(String resetStage, String responseMessage) {
        return new TeachingStageRoute(TeachingStageOperation.RETURN_MESSAGE, responseMessage, resetStage);
    }
}
