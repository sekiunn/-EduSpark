package com.eduspark.eduspark.service;

import com.eduspark.eduspark.dto.knowledge.KnowledgeSearchResponse;

/**
 * 三层检索服务接口
 * <p>
 * 架构说明：
 * - 第一层：本地知识库检索（向量 + BM25）
 * - 第二层：联网搜索（用户开启时）
 * - 第三层：LLM直接回答（兜底）
 */
public interface ITriLevelSearchService {

    /**
     * 三层检索
     *
     * @param request 检索请求
     * @return 检索结果
     */
    TriLevelSearchResult search(TriLevelSearchRequest request);

    // ==================== 请求 ====================

    record TriLevelSearchRequest(
            String query,              // 用户查询（含历史上下文，用于检索）
            String originalQuery,      // 原始问题（不含历史，用于显示）
            int topK,                  // 知识库检索数量
            boolean webSearchEnabled,   // 是否启用联网搜索
            Long userId                // 用户ID
    ) {}

    // ==================== 结果 ====================

    record TriLevelSearchResult(
            String query,              // 查询文本
            String answer,             // 最终回答（仅引导类消息，如"知识库为空"）
            long costMs,               // 耗时（毫秒）
            int layer,                 // 使用的最高层：0-引导 1-知识库 2-联网 3-LLM兜底
            String layerDesc,          // 层描述

            // 第一层结果
            KnowledgeSearchResponse localResult,

            // 第二层结果
            IWebSearchService.SearchResult webResult,

            // 推荐上传
            String recommendedUpload,
            boolean hasPartialKnowledge, // 知识库是否有部分相关内容
            boolean hasFullKnowledge,    // 知识库是否完全覆盖

            // 上下文信息（供调用方用于 LLM 流式生成）
            String context,             // 构建好的上下文（知识库/联网内容）
            String promptType           // 提示词类型：rag / webSearch / llmFallback / null(引导)
    ) {}
}
