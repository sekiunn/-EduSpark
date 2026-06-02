package com.eduspark.eduspark.service;

import java.util.List;

/**
 * 智能分块服务接口
 */
public interface ISmartChunkService {

    /**
     * 智能文本分块
     */
    List<ChunkResult> chunkText(String text);

    /**
     * 分块结果
     */
    record ChunkResult(
            String text,
            int tokenCount
    ) {}
}
