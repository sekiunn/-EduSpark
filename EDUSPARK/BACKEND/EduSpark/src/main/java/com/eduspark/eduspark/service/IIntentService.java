package com.eduspark.eduspark.service;

import com.eduspark.eduspark.pojo.entity.IntentType;

/**
 * 意图识别服务接口
 */
public interface IIntentService {

    /**
     * 识别用户意图
     *
     * @param query 用户输入
     * @return 意图类型
     */
    IntentType classify(String query);

    /**
     * 识别用户意图（带置信度）
     *
     * @param query 用户输入
     * @return 识别结果详情
     */
    IntentResult classifyWithConfidence(String query);

    /**
     * 识别结果详情（包含置信度）
     */
    record IntentResult(
            IntentType type,
            float confidence,
            String reasoning
    ) {}
}
