package com.eduspark.eduspark.pojo.entity;

/**
 * 意图类型枚举
 */
public enum IntentType {
    /**
     * 教学问题：学科知识、习题解答、考试相关
     * → 走知识库检索 + LLM生成
     */
    TEACHING,

    /**
     * 事实查询：天气、时间、实时信息
     * → 联网搜索 + LLM生成
     */
    FACTUAL,

    /**
     * 闲聊：问候、情感、日常对话
     * → LLM直接回答
     */
    CASUAL,

    /**
     * 敏感内容：暴力、色情、政治敏感
     * → 拒绝回答
     */
    SENSITIVE,

    /**
     * 课件生成：生成教案/PPT/互动内容
     * → 引导用户使用侧边栏专用功能
     */
    COURSEWARE,

    /**
     * 工具操作：上传、下载、登录、注册
     * → 工具操作类请求
     */
    TOOL
}
