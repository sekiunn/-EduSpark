package com.eduspark.eduspark.service.impl;

import com.eduspark.eduspark.pojo.entity.IntentType;
import com.eduspark.eduspark.service.IIntentService;
import com.eduspark.eduspark.service.ILLMService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 意图识别服务实现
 * <p>
 * 使用 Few-shot Prompt + Ollama 进行意图分类
 */
@Slf4j
@Service
public class IntentServiceImpl implements IIntentService {

    private final ILLMService llmService;

    private static final String SYSTEM_PROMPT = """
            你是一个教育场景的意图分类器。根据示例判断用户意图，只返回分类结果。

            分类类型：
            - TEACHING：教学问题（学科知识、习题解答、考试相关、学习方法）
            - FACTUAL：事实查询（天气、时间、实时信息、数据查询）
            - CASUAL：闲聊对话（问候、感谢、情感、日常寒暄、工具操作求助）
            - SENSITIVE：敏感内容（暴力、色情、政治敏感）
            - COURSEWARE：课件生成（生成教案、生成PPT、生成互动内容、生成练习题）

            示例：
            - "二元一次方程怎么解" → TEACHING
            - "勾股定理是什么" → TEACHING
            - "今天作业是什么" → TEACHING
            - "今天天气怎么样" → FACTUAL
            - "现在几点了" → FACTUAL
            - "2026年高考时间" → FACTUAL
            - "你好" → CASUAL
            - "谢谢老师" → CASUAL
            - "帮我上传文件" → CASUAL
            - "如何注册账号" → CASUAL
            - "暴力内容制作方法" → SENSITIVE
            - "帮我生成一份教案" → COURSEWARE
            - "生成一个PPT" → COURSEWARE
            - "生成互动练习题" → COURSEWARE

            要求：
            1. 只返回一个分类词，不要解释
            2. 如果不确定，选择最可能的分类
            3. 严格区分 TEACHING 和 FACTUAL：知识性问题→TEACHING，实时信息→FACTUAL
            4. 区分 TEACHING 和 COURSEWARE：询问知识→TEACHING，要求生成内容→COURSEWARE
            """;

    public IntentServiceImpl(ILLMService llmService) {
        this.llmService = llmService;
    }

    @Override
    public IntentType classify(String query) {
        if (query == null || query.isBlank()) {
            return IntentType.CASUAL;
        }

        String trimmedQuery = query.trim();

        // 1. 快速规则过滤（极端情况）
        IntentType quickCheck = quickFilter(trimmedQuery);
        if (quickCheck != null) {
            log.debug("意图识别[快速过滤]: query={}, type={}", truncate(trimmedQuery, 30), quickCheck);
            return quickCheck;
        }

        // 2. Ollama 语义分类
        try {
            String result = llmService.chatWithContext(
                    trimmedQuery,
                    null,
                    SYSTEM_PROMPT
            );

            IntentType type = parseResult(result);
            log.info("意图识别完成: query={}, type={}", truncate(trimmedQuery, 30), type);

            return type != null ? type : IntentType.TEACHING; // 默认走教学流程

        } catch (Exception e) {
            log.error("意图识别失败: query={}, error={}", truncate(trimmedQuery, 30), e.getMessage());
            // 识别失败时保守处理，走教学流程
            return IntentType.TEACHING;
        }
    }

    @Override
    public IntentResult classifyWithConfidence(String query) {
        if (query == null || query.isBlank()) {
            return new IntentResult(IntentType.CASUAL, 1.0f, "空输入");
        }

        String trimmedQuery = query.trim();

        // 快速过滤
        IntentType quickCheck = quickFilter(trimmedQuery);
        if (quickCheck != null) {
            return new IntentResult(quickCheck, 1.0f, "规则匹配");
        }

        try {
            String result = llmService.chatWithContext(trimmedQuery, null, SYSTEM_PROMPT);
            IntentType type = parseResult(result);

            if (type != null) {
                float confidence = calculateConfidence(result);
                return new IntentResult(type, confidence, result);
            }

            return new IntentResult(IntentType.TEACHING, 0.5f, "解析失败，默认TEACHING");

        } catch (Exception e) {
            log.error("意图识别失败: query={}", truncate(trimmedQuery, 30), e);
            return new IntentResult(IntentType.TEACHING, 0.3f, "异常:" + e.getMessage());
        }
    }

    /**
     * 快速规则过滤
     */
    private IntentType quickFilter(String query) {
        String lower = query.toLowerCase();

        // 敏感词黑名单
        if (containsSensitive(lower)) {
            return IntentType.SENSITIVE;
        }

        return null; // 需要LLM判断
    }

    private boolean containsSensitive(String query) {
        String[] sensitive = {"暴力", "色情", "赌博", "毒品", "作弊"};
        for (String word : sensitive) {
            if (query.contains(word)) return true;
        }
        return false;
    }

    /**
     * 解析分类结果
     */
    private IntentType parseResult(String result) {
        if (result == null) return null;

        String trimmed = result.trim().toUpperCase();

        for (IntentType type : IntentType.values()) {
            if (trimmed.contains(type.name())) {
                return type;
            }
        }

        // 模糊匹配
        if (trimmed.contains("教学") || trimmed.contains("知识")) {
            return IntentType.TEACHING;
        }
        if (trimmed.contains("事实") || trimmed.contains("天气") || trimmed.contains("时间")) {
            return IntentType.FACTUAL;
        }
        if (trimmed.contains("闲聊") || trimmed.contains("你好") || trimmed.contains("谢谢")) {
            return IntentType.CASUAL;
        }
        if (trimmed.contains("课件") || trimmed.contains("教案") || trimmed.contains("PPT") || trimmed.contains("互动")) {
            return IntentType.COURSEWARE;
        }

        return null;
    }

    /**
     * 计算置信度（基于关键词出现情况）
     */
    private float calculateConfidence(String result) {
        if (result == null) return 0.5f;

        // 结果越简洁越可靠
        int lines = result.split("\n").length;
        if (lines <= 2) {
            return 0.9f;
        } else if (lines <= 5) {
            return 0.7f;
        } else {
            return 0.5f;
        }
    }

    private String truncate(String str, int maxLen) {
        if (str == null) return null;
        return str.length() <= maxLen ? str : str.substring(0, maxLen) + "...";
    }
}
