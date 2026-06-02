package com.eduspark.eduspark.service;

import com.eduspark.eduspark.dto.chat.TeachingBlueprint;
import com.eduspark.eduspark.dto.pptworkspace.PptDocumentResponse;
import com.eduspark.eduspark.dto.pptworkspace.PptWorkspaceCardData;
import com.eduspark.eduspark.pojo.entity.ChatSession;

/**
 * Dedicated workflow service for PPT workspaces after confirmation.
 */
public interface IPptWorkspaceService {

    PptWorkspaceCardData createWorkspace(ChatSession session, TeachingBlueprint blueprint);

    void generateInitialDocument(Long sessionId, TeachingBlueprint blueprint);

    PptDocumentResponse getDocument(Long documentId, Long userId);

    PptDocumentResponse exportDocument(Long documentId, Long userId);
}
