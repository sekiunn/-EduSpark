package com.eduspark.eduspark.service;

/**
 * Ollama 向量化服务接口
 */
public interface IEmbeddingService {

    /**
     * 单条文本向量化
     */
    float[] embed(String text);

    /**
     * 批量文本向量化
     */
    java.util.List<float[]> batchEmbed(java.util.List<String> texts);

    /**
     * 检查服务是否可用
     */
    boolean isAvailable();

    /**
     * 获取当前模型名称
     */
    String getModelName();
}
