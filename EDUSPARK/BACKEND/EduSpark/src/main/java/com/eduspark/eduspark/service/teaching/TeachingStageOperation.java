package com.eduspark.eduspark.service.teaching;

/**
 * High-level operation selected by the teaching-stage state machine.
 */
public enum TeachingStageOperation {
    START_CLARIFICATION,
    CONTINUE_CLARIFICATION,
    CONFIRM_GENERATION,
    RETURN_MESSAGE
}
