package com.eduspark.eduspark.service;

import java.util.List;
import java.util.function.Consumer;

/**
 * LLM 对话服务接口
 */
public interface ILLMService {

    /**
     * 聊天生成
     *
     * @param messages 消息历史
     * @return 生成的回复
     */
    String chat(List<ChatMessage> messages);

    /**
     * 聊天生成，并为本次调用指定最大输出 token 上限（覆盖全局默认 max-tokens）。
     * 用于 PPT 全替换这类"一次性返回长 JSON"的场景——默认 1024 会把多文本块的改写结果截断。
     * 默认实现忽略 maxTokens、退回普通 chat（非主 LLM 实现可不感知该能力）。
     *
     * @param messages  消息历史
     * @param maxTokens 本次最大输出 token（&lt;=0 表示用全局默认）
     */
    default String chat(List<ChatMessage> messages, int maxTokens) {
        return chat(messages);
    }

    /**
     * 基于上下文的聊天生成（RAG模式）
     *
     * @param userMessage 用户消息
     * @param context     知识库检索到的上下文
     * @param systemPrompt 系统提示词
     * @return 生成的回复
     */
    String chatWithContext(String userMessage, String context, String systemPrompt);

    /**
     * 基于历史上下文的聊天生成（多轮对话模式）
     *
     * @param userMessage 当前用户消息
     * @param history     对话历史（role: user/assistant）
     * @param context     知识库上下文（可选）
     * @param systemPrompt 系统提示词
     * @return 生成的回复
     */
    String chatWithHistory(String userMessage, List<ChatMessage> history, String context, String systemPrompt);

    /**
     * 流式聊天生成
     *
     * @param messages 消息历史
     * @param onChunk  每个文本块的回调
     * @return 完整回复（所有块的拼接）
     */
    String chatStream(List<ChatMessage> messages, Consumer<String> onChunk);

    /**
     * 流式聊天，并指定最大输出 token 上限。
     * 流式下数据分段持续到达，read-timeout 按"每段读取"计、不会因总时长过长而超时——
     * 适合 PPT 全替换这类输出长、整体耗时数分钟的调用（阻塞式会被总读取超时打断）。
     * 默认实现退回不带 maxTokens 的流式。
     */
    default String chatStream(List<ChatMessage> messages, Consumer<String> onChunk, int maxTokens) {
        return chatStream(messages, onChunk);
    }

    /**
     * 检查服务是否可用
     */
    boolean isAvailable();

    /**
     * 获取模型名称
     */
    String getModelName();

    /**
     * 使用指定模型聊天（用于轻量模型提取场景）
     */
    String chatWithModel(List<ChatMessage> messages, String model);

    /**
     * 聊天消息
     */
    record ChatMessage(
            String role,    // system / user / assistant
            String content  // 消息内容
    ) {}
}
