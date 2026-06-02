package com.eduspark.eduspark.service;

import com.eduspark.eduspark.dto.knowledge.KnowledgeSearchResponse;
import com.eduspark.eduspark.service.IWebSearchService;

/**
 * 上下文构建服务（统一管理所有检索结果的格式化）
 * <p>
 * 将知识库/联网搜索结果转换为 LLM 可用的上下文字符串，
 * 避免在 Controller / Service 各处重复实现。
 * </p>
 */
public interface IContextBuilderService {

    /**
     * 构建知识库上下文（带编号，便于引用）
     *
     * @param result    知识库检索结果
     * @param maxTokens 最大 token 数限制（约等于字符数/2）
     * @return 格式化后的上下文，如 "[1] 【来源: 铝.doc】\n铝是一种金属...\n\n"
     */
    String buildKnowledgeContext(KnowledgeSearchResponse result, int maxTokens);

    /**
     * 构建联网搜索上下文
     *
     * @param webResult 联网搜索结果
     * @return 格式化后的上下文
     */
    String buildWebContext(IWebSearchService.SearchResult webResult);

    /**
     * 构建用户引用文档的上下文（不带编号，纯文本拼接）
     *
     * @param results   过滤后的检索结果列表
     * @return 格式化后的上下文
     */
    String buildReferencedFileContext(java.util.List<KnowledgeSearchResponse.KnowledgeSearchResult> results);
}
