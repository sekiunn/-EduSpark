package com.eduspark.eduspark.service.impl;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 关键词 → emoji 字符 映射，用作 PPT 渲染时的内置图标。
 *
 * <p>之所以用 emoji 而非 SVG/PNG 资源：emoji 直接渲染于 PowerPoint，无需打包二进制资源，
 * 且 Microsoft YaHei + Segoe UI Emoji 字体回退已能保证 Win / Mac / Linux 一致显示。</p>
 *
 * <p>查找策略：依次匹配中文关键词、英文关键词、视觉提示文本中的子串。命中第一个即返回；
 * 全部未命中时返回默认图标 🎯。</p>
 */
final class PptIconCatalog {

    private PptIconCatalog() {
    }

    private static final Map<String, String> KEYWORD_TO_ICON = buildKeywordMap();
    private static final String DEFAULT_ICON = "🎯";

    /**
     * 从一个或多个候选文本中检索最匹配的图标。
     */
    static String resolveIcon(List<String> candidates) {
        if (candidates == null) {
            return DEFAULT_ICON;
        }
        for (String candidate : candidates) {
            if (candidate == null || candidate.isBlank()) {
                continue;
            }
            String normalized = candidate.toLowerCase(Locale.ROOT);
            for (Map.Entry<String, String> entry : KEYWORD_TO_ICON.entrySet()) {
                if (normalized.contains(entry.getKey())) {
                    return entry.getValue();
                }
            }
        }
        return DEFAULT_ICON;
    }

    private static Map<String, String> buildKeywordMap() {
        // 用 LinkedHashMap 保留优先级 —— 越靠前越优先命中（更具体的关键词放前面）。
        Map<String, String> map = new LinkedHashMap<>();

        // 学习目标 / 重点
        map.put("目标", "🎯");
        map.put("重点", "🎯");
        map.put("target", "🎯");
        map.put("goal", "🎯");
        map.put("objective", "🎯");

        // 思路 / 创意
        map.put("思路", "💡");
        map.put("创新", "💡");
        map.put("启发", "💡");
        map.put("idea", "💡");
        map.put("creative", "💡");

        // 知识点 / 概念
        map.put("知识", "📚");
        map.put("概念", "📚");
        map.put("理论", "📚");
        map.put("definition", "📚");

        // 数据 / 图表
        map.put("数据", "📊");
        map.put("统计", "📊");
        map.put("图表", "📊");
        map.put("分析", "📊");
        map.put("data", "📊");
        map.put("chart", "📊");

        // 工具 / 实践
        map.put("工具", "🔧");
        map.put("实操", "🔧");
        map.put("演示", "🔧");
        map.put("tool", "🔧");

        // 实验 / 测试
        map.put("实验", "🧪");
        map.put("测试", "🧪");
        map.put("验证", "🧪");
        map.put("test", "🧪");

        // 步骤 / 流程
        map.put("步骤", "📝");
        map.put("流程", "📝");
        map.put("过程", "📝");
        map.put("操作", "📝");
        map.put("step", "📝");
        map.put("process", "📝");

        // 代码
        map.put("代码", "💻");
        map.put("程序", "💻");
        map.put("函数", "💻");
        map.put("算法", "💻");
        map.put("code", "💻");

        // 提问 / 思考
        map.put("提问", "❓");
        map.put("思考", "❓");
        map.put("讨论", "❓");
        map.put("question", "❓");

        // 总结 / 完成
        map.put("总结", "✅");
        map.put("回顾", "✅");
        map.put("小结", "✅");
        map.put("summary", "✅");

        // 启动 / 引入
        map.put("引入", "🚀");
        map.put("启动", "🚀");
        map.put("导入", "🚀");

        // 注意 / 警告
        map.put("注意", "⚡");
        map.put("警告", "⚡");
        map.put("难点", "⚡");

        // 结构 / 架构
        map.put("结构", "🧩");
        map.put("架构", "🧩");
        map.put("模块", "🧩");
        map.put("组件", "🧩");

        // 团队 / 协作
        map.put("团队", "👥");
        map.put("协作", "👥");
        map.put("小组", "👥");

        // 时间 / 计划
        map.put("时间", "⏰");
        map.put("计划", "⏰");
        map.put("安排", "⏰");

        // 课堂 / 教学
        map.put("课堂", "🏫");
        map.put("教学", "🏫");
        map.put("学习", "🏫");

        return map;
    }
}
