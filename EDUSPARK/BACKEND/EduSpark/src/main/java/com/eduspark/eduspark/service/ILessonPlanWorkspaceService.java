package com.eduspark.eduspark.service;

import com.eduspark.eduspark.dto.chat.TeachingBlueprint;
import com.eduspark.eduspark.dto.lessonplan.LessonPlanDocumentResponse;
import com.eduspark.eduspark.dto.lessonplan.LessonPlanRewriteResponse;
import com.eduspark.eduspark.dto.lessonplan.LessonPlanWorkspaceCardData;
import com.eduspark.eduspark.pojo.entity.ChatSession;

/**
 * Dedicated workflow service for lesson-plan workspaces after confirmation.
 */
public interface ILessonPlanWorkspaceService {

    LessonPlanWorkspaceCardData createWorkspace(ChatSession session, TeachingBlueprint blueprint);

    void generateInitialDraft(Long sessionId, TeachingBlueprint blueprint);

    LessonPlanDocumentResponse getDocument(Long documentId, Long userId);

    LessonPlanDocumentResponse updateDocumentContent(Long documentId, Long userId, String content);

    LessonPlanRewriteResponse rewriteSelection(Long documentId, Long userId, String selectedText, String instruction);

    LessonPlanDocumentResponse exportDocument(Long documentId, Long userId);
}
