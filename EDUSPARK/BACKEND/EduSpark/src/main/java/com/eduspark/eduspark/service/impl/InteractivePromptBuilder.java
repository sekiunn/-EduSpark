package com.eduspark.eduspark.service.impl;

import com.eduspark.eduspark.dto.interactive.InteractiveContext;
import org.springframework.stereotype.Component;

/**
 * Shared prompt builder for interactive HTML generation.
 */
@Component
public class InteractivePromptBuilder {

    private static final String SYSTEM_PROMPT = """
            你是一位教学互动页面工程师。
            你的任务是输出一个可以直接运行的单文件 HTML 教学页面。
            必须严格遵守以下要求：
            1. 只输出最终 HTML，不要解释，不要 Markdown 代码块。
            2. 输出且只输出一个完整 HTML 文档，必须包含 <!DOCTYPE html>、<html>、<head>、<body>，不要嵌套第二个文档。
            3. 所有 CSS 和 JavaScript 必须内联。
            4. 禁止使用外部 CDN、外部脚本、外部样式、外部字体和任何网络请求。
            5. 页面必须真实可交互，不能只做静态说明。
            6. 默认中文文案，适配桌面端和移动端。
            7. 优先保证教学目标清晰、交互稳定、反馈明确，再考虑视觉包装。
            8. 除非需求明确要求复杂结构，否则优先使用简单、可靠、易维护的实现。
            9. 对于动画、模拟、实验类需求，优先使用单个 canvas 或少量 DOM 元素完成核心演示。
            10. 如果页面需要图示、结构图、示意图、场景图，优先直接使用 HTML、CSS、SVG、Canvas 在页面内绘制，不要要求用户额外提供图片。
            11. 所有标签必须正确闭合，script 和 style 必须完整闭合，不要输出半成品。
            """;

    public String getSystemPrompt() {
        return SYSTEM_PROMPT;
    }

    public String buildInitialPrompt(InteractiveContext context) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("请根据以下实现上下文，生成一个可直接运行的互动教学 HTML 页面。\n\n");
        appendContext(prompt, context);
        prompt.append("\n实现要求：\n");
        prompt.append("1. 页面打开后即可直接使用，不需要额外配置。\n");
        prompt.append("2. 交互逻辑必须完整，必要时给出即时反馈。\n");
        prompt.append("3. 视觉保持清晰、有层次，但不要堆砌无意义装饰。\n");
        prompt.append("4. 教师和学生都要容易理解如何使用。\n");
        prompt.append("5. 如果有步骤、动画、闯关、拖拽、点击探索等要求，务必真实实现。\n");
        prompt.append("6. 如果信息仍有空白，不要反问，基于当前上下文做合理实现。\n");
        prompt.append("7. 如果这是动画或模拟演示，优先先把演示逻辑做对，再做样式。\n");
        prompt.append("8. 除非用户明确要求复杂面板，否则控件数量保持精简。\n");
        prompt.append("9. 如果需要图示或插图，优先直接在 HTML 中用 SVG、Canvas、CSS 自行绘制，不要把关键效果留空。\n");
        return prompt.toString();
    }

    public String buildRefinePrompt(InteractiveContext context, String currentHtml, String instruction) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("请基于当前互动页面 HTML 和新的修改要求，输出一份完整的新 HTML。\n\n");
        appendContext(prompt, context);
        prompt.append("\n新的修改要求：\n");
        prompt.append(instruction == null ? "" : instruction.trim()).append("\n\n");
        prompt.append("当前 HTML：\n");
        prompt.append(currentHtml == null ? "" : currentHtml.trim()).append("\n\n");
        prompt.append("输出要求：\n");
        prompt.append("1. 输出完整 HTML，不要只输出差异片段。\n");
        prompt.append("2. 保留已有可用功能，按新要求调整。\n");
        prompt.append("3. 如果新要求与旧页面冲突，以最新要求为准。\n");
        prompt.append("4. 仍然禁止任何外部依赖。\n");
        prompt.append("5. 如果当前实现过于复杂或不稳定，可以主动简化，但不能丢失核心教学目标。\n");
        prompt.append("6. 如果页面需要图示或场景，优先直接在 HTML 中重绘，不要依赖外部图片。\n");
        return prompt.toString();
    }

    public String buildRepairPrompt(InteractiveContext context, String brokenHtml, String issueSummary) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("请修复下面这个互动教学页面 HTML，使它成为一份可直接运行的完整单文件 HTML。\n\n");
        appendContext(prompt, context);
        appendLine(prompt, "当前已发现的问题", issueSummary);
        prompt.append("\n修复要求：\n");
        prompt.append("1. 输出一份新的、完整的、可运行的 HTML，不要解释。\n");
        prompt.append("2. 只允许出现一个 <!DOCTYPE html> 和一个 <html> 根文档。\n");
        prompt.append("3. body、script、style 等标签必须闭合完整。\n");
        prompt.append("4. 优先保留原来的教学目标和主要交互；如果原实现太复杂或已损坏，可以改写成更简单但更稳的版本。\n");
        prompt.append("5. 如果这是动画或模拟页面，优先保证能正常演示，再考虑装饰。\n");
        prompt.append("6. 如果需要图示或场景，优先直接在 HTML 内用 SVG、Canvas、CSS 重绘。\n");
        prompt.append("7. 不要引入任何外部依赖或网络请求。\n\n");
        prompt.append("待修复 HTML：\n");
        prompt.append(brokenHtml == null ? "" : brokenHtml.trim()).append('\n');
        return prompt.toString();
    }

    private void appendContext(StringBuilder prompt, InteractiveContext context) {
        appendLine(prompt, "学科", context.getSubject());
        appendLine(prompt, "年级/对象", context.getGrade());
        appendLine(prompt, "主题", context.getTopic());
        appendLine(prompt, "使用场景", context.getScene());
        appendLine(prompt, "互动类型", context.getInteractionType());
        appendLine(prompt, "页面目标", context.getPageGoal());
        appendLine(prompt, "学生要做什么", context.getStudentAction());
        if (!context.safeMustHaveInteractions().isEmpty()) {
            prompt.append("必须实现的交互：")
                    .append(String.join("、", context.safeMustHaveInteractions()))
                    .append('\n');
        }
        appendLine(prompt, "教学侧重点", context.getTeachingFocus());
        appendLine(prompt, "风格偏好", context.getStyleHint());
        if (!context.safeConstraints().isEmpty()) {
            prompt.append("约束条件：")
                    .append(String.join("、", context.safeConstraints()))
                    .append('\n');
        }
        appendLine(prompt, "补充说明", context.getNotes());
        if (hasText(context.getReferenceText())) {
            prompt.append("参考资料：\n")
                    .append(context.getReferenceText().trim())
                    .append('\n');
        }
    }

    private void appendLine(StringBuilder builder, String label, String value) {
        if (!hasText(value)) {
            return;
        }
        builder.append(label).append("：").append(value.trim()).append('\n');
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
