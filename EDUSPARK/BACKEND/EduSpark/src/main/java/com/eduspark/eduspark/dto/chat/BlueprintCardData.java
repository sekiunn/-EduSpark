package com.eduspark.eduspark.dto.chat;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * 蓝图确认卡片数据
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BlueprintCardData implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 模式：lesson_plan / ppt / interactive
     */
    private String mode;

    /**
     * 模式名称（用于显示）
     */
    private String modeName;

    /**
     * 课程标题
     */
    private String title;

    /**
     * 基本信息
     */
    private String subject;
    private String grade;
    private Integer duration;
    private Integer slideCount;
    private String style;
    private String deliveryFormat;
    private String interactionIdea;
    private String usageScene;
    private String visualStyle;
    private String animationLevel;
    private Integer questionCount;
    private String questionType;
    private String notes;

    /**
     * 知识点列表
     */
    private List<KnowledgePoint> knowledgePoints;

    /**
     * 教学目标（教案模式）
     */
    private Map<String, String> teachingGoals;

    /**
     * 讲授流程（教案模式）
     */
    private Map<String, PhaseInfo> teachingFlow;

    /**
     * 重点难点（教案模式）
     */
    private List<FocusItem> keyPoints;
    private List<FocusItem> difficultPoints;
    private List<String> interactionHints;
    private List<String> userConstraints;

    /**
     * 按钮配置
     */
    private List<ActionButton> buttons;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class KnowledgePoint implements Serializable {
        private String id;
        private String name;
        private String difficulty;
        private Integer estimatedTime;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PhaseInfo implements Serializable {
        private Integer duration;
        private String description;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FocusItem implements Serializable {
        private String name;
        private String reason;
        private String strategy;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ActionButton implements Serializable {
        private String type;      // confirm / supplement
        private String label;     // 按钮文字
    }
}
