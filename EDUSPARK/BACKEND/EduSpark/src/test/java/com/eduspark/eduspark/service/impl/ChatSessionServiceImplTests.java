package com.eduspark.eduspark.service.impl;

import com.eduspark.eduspark.mapper.ChatMessageMapper;
import com.eduspark.eduspark.mapper.ChatSessionMapper;
import com.eduspark.eduspark.pojo.entity.ChatSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ChatSessionServiceImplTests {

    @Mock
    private ChatSessionMapper sessionMapper;

    @Mock
    private ChatMessageMapper messageMapper;

    @Mock
    private JdbcTemplate jdbcTemplate;

    private ChatSessionServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ChatSessionServiceImpl(sessionMapper, messageMapper, jdbcTemplate);
    }

    @Test
    void clearTeachingModeShouldResetModeStageAndTeachingElements() {
        service.clearTeachingMode(42L);

        verify(jdbcTemplate).update(
                "UPDATE chat_session SET mode = NULL, stage = ?, teaching_elements = NULL, update_time = NOW() WHERE id = ? AND is_deleted = 0",
                ChatSession.Stage.IDLE,
                42L
        );
    }

    @Test
    void clearGenerationResultShouldResetStoredGenerationResult() {
        service.clearGenerationResult(42L, ChatSession.GenerationStatus.PENDING);

        verify(jdbcTemplate).update(
                "UPDATE chat_session SET generation_status = ?, generation_result = NULL, update_time = NOW() WHERE id = ? AND is_deleted = 0",
                ChatSession.GenerationStatus.PENDING,
                42L
        );
    }
}
