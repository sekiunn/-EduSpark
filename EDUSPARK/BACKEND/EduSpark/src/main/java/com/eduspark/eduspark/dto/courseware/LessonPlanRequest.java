package com.eduspark.eduspark.dto.courseware;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 教案生成请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LessonPlanRequest {

    /**
     * 课题 / 主题
     */
    private String topic;

    /**
     * 学科，如"数学"
     */
    private String subject;

    /**
     * 年级，如"初中二年级"
     */
    private String grade;

    /**
     * 课时时长（分钟）
     */
    private Integer duration;

    /**
     * 知识点列表，如["勾股定理", "直角三角形的判定"]
     */
    private List<String> knowledgePoints;

    /**
     * 教学目标
     */
    private String teachingGoal;

    /**
     * 重点
     */
    private String keyPoint;

    /**
     * 难点
     */
    private String difficultPoint;

    /**
     * 教学方法，如"讲授法、讨论法"
     */
    private String teachingMethod;

    /**
     * 参考资料文本（从上传文件解析得到）
     */
    private String referenceText;

    /**
     * 用户原始描述（用于LLM提取结构化信息）
     */
    private String userDescription;

    /**
     * 教师姓名（可选，填入教案）
     */
    private String teacherName;
}
