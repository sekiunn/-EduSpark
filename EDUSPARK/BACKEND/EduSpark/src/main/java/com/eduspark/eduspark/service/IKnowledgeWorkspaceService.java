package com.eduspark.eduspark.service;

import com.eduspark.eduspark.dto.knowledge.KnowledgeWorkspaceRequest;
import com.eduspark.eduspark.dto.knowledge.KnowledgeWorkspaceResponse;

import java.util.List;

/**
 * 课程空间服务：教师创建/管理"课程"维度的知识库容器。
 */
public interface IKnowledgeWorkspaceService {

    List<KnowledgeWorkspaceResponse> listByUser(Long userId);

    KnowledgeWorkspaceResponse create(KnowledgeWorkspaceRequest request);

    KnowledgeWorkspaceResponse update(Long workspaceId, KnowledgeWorkspaceRequest request);

    void delete(Long workspaceId, Long userId);

    /** 校验 workspace 是否归 userId 所有；不存在或越权时抛业务异常。 */
    void verifyOwnership(Long workspaceId, Long userId);
}
