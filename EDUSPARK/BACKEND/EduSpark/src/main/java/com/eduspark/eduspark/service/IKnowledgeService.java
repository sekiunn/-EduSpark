package com.eduspark.eduspark.service;

import com.eduspark.eduspark.dto.common.PageResponse;
import com.eduspark.eduspark.dto.knowledge.*;

/**
 * 知识库服务接口
 */
public interface IKnowledgeService {

    /**
     * 上传文件并异步处理
     */
    KnowledgeFileResponse uploadFile(
            String fileName,
            String fileType,
            byte[] fileBytes,
            Long userId,
            Long workspaceId,
            String category,
            String description
    );

    /**
     * 分页查询用户文件
     */
    PageResponse<KnowledgeFileResponse> listFiles(KnowledgeFilePageRequest request);

    /**
     * 获取文件详情
     */
    KnowledgeFileResponse getFileById(Long fileId, Long userId);

    /**
     * 删除文件
     */
    void deleteFile(Long fileId, Long userId);

    /**
     * 重新处理文件（用于处理失败的文件）
     */
    void reprocessFile(Long fileId, Long userId);

    /**
     * 混合检索
     */
    KnowledgeSearchResponse search(KnowledgeSearchRequest request);

    /**
     * 检索测试，返回命中块和最终上下文。
     */
    KnowledgeSearchTestResponse testSearch(KnowledgeSearchTestRequest request);

    /**
     * 检查文件处理状态
     */
    KnowledgeFileResponse getFileStatus(Long fileId, Long userId);

    /**
     * 获取文件预览及 chunk 摘要。
     */
    KnowledgeFilePreviewResponse getFilePreview(Long fileId, Long userId, Integer chunkLimit);

    /**
     * 获取文件 chunk 列表。
     */
    java.util.List<KnowledgeChunkPreviewResponse> getFileChunks(Long fileId, Long userId, Integer limit);

    /**
     * 获取文件访问URL
     */
    String getFileUrl(Long fileId);
}
