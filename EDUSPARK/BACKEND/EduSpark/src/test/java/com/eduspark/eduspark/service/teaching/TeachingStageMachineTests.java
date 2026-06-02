package com.eduspark.eduspark.service.teaching;

import com.eduspark.eduspark.pojo.entity.ChatSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TeachingStageMachineTests {

    private TeachingStageMachine stageMachine;

    @BeforeEach
    void setUp() {
        stageMachine = new TeachingStageMachine();
    }

    @Test
    void routeRequestShouldConfirmGenerationOnlyInConfirmingStage() {
        TeachingStageRoute route = stageMachine.routeRequest(ChatSession.Stage.CONFIRMING, "confirm");

        assertThat(route.operation()).isEqualTo(TeachingStageOperation.CONFIRM_GENERATION);
        assertThat(route.responseMessage()).isNull();
    }

    @Test
    void routeRequestShouldLockWhenGenerationIsInProgress() {
        TeachingStageRoute route = stageMachine.routeRequest(ChatSession.Stage.GENERATING, "supplement");

        assertThat(route.operation()).isEqualTo(TeachingStageOperation.RETURN_MESSAGE);
        assertThat(route.responseMessage()).isEqualTo(TeachingStageMachine.GENERATING_LOCKED_MESSAGE);
    }

    @Test
    void routeRequestShouldResetUnknownStage() {
        TeachingStageRoute route = stageMachine.routeRequest("weird-stage", null);

        assertThat(route.operation()).isEqualTo(TeachingStageOperation.RETURN_MESSAGE);
        assertThat(route.resetStage()).isEqualTo(ChatSession.Stage.IDLE);
        assertThat(route.responseMessage()).isEqualTo(TeachingStageMachine.INVALID_STAGE_MESSAGE);
    }

    @Test
    void stateAfterClarificationShouldMatchBlueprintCompleteness() {
        assertThat(stageMachine.stageAfterClarification(false)).isEqualTo(ChatSession.Stage.CLARIFYING);
        assertThat(stageMachine.stageAfterClarification(true)).isEqualTo(ChatSession.Stage.CONFIRMING);
    }

    @Test
    void stateAfterGenerationShouldMatchOutcome() {
        assertThat(stageMachine.stageAfterGeneration(true)).isEqualTo(ChatSession.Stage.COMPLETED);
        assertThat(stageMachine.stageAfterGeneration(false)).isEqualTo(ChatSession.Stage.CONFIRMING);
    }
}
