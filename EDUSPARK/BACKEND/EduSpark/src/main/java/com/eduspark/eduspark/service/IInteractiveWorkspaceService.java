package com.eduspark.eduspark.service;

import com.eduspark.eduspark.dto.interactive.InteractiveContext;
import com.eduspark.eduspark.dto.interactive.InteractiveDownloadPayload;
import com.eduspark.eduspark.dto.interactive.InteractiveDocumentResponse;
import com.eduspark.eduspark.dto.interactive.InteractiveStageCardData;
import com.eduspark.eduspark.pojo.entity.ChatSession;

/**
 * Dedicated workflow service for interactive HTML workspaces.
 */
public interface IInteractiveWorkspaceService {

    InteractiveStageCardData createWorkspace(ChatSession session, InteractiveContext context);

    void generateInitialDocumentAsync(Long sessionId, Long documentId, InteractiveContext context);

    void refineDocumentAsync(Long sessionId, Long documentId, InteractiveContext context, String instruction);

    InteractiveDocumentResponse getDocument(Long documentId, Long userId);

    InteractiveDocumentResponse updateDocumentContent(Long documentId, Long userId, String content);

    InteractiveDocumentResponse exportDocument(Long documentId, Long userId);

    InteractiveDownloadPayload downloadDocument(Long documentId, Long userId);
}
