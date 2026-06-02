package com.eduspark.eduspark.service;

/**
 * 系统提示词集中管理服务
 * <p>
 * 所有 LLM 系统提示词在此统一维护，避免散落在各处导致不一致。
 * </p>
 */
public interface ISystemPromptService {

    /**
     * RAG 模式提示词（基于知识库回答，带引用格式）
     *
     * @param context 知识库检索到的上下文（带编号和来源）
     * @return 完整的系统提示词
     */
    String ragPrompt(String context);

    /**
     * 联网搜索模式提示词
     */
    String webSearchPrompt();

    /**
     * 闲聊模式提示词
     */
    String casualPrompt();

    /**
     * LLM 兜底模式提示词（无知识库、无联网时）
     */
    String llmFallbackPrompt();

    /**
     * 简洁模式提示词（通用）
     */
    String simplePrompt();
}
