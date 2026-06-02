package com.eduspark.eduspark.service;

import com.eduspark.eduspark.dto.knowledge.KnowledgeSearchResponse;

/**
 * 混合检索服务接口
 */
public interface ISearchService {

    /**
     * 混合检索（向量 + BM25 + RRF融合）
     *
     * @param workspaceId 课程空间过滤；null 表示不限定课程空间
     */
    KnowledgeSearchResponse hybridSearch(
            String query,
            int topK,
            Long userId,
            Long workspaceId,
            Float vectorWeight,
            Float bm25Weight
    );
}
