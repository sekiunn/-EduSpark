package com.eduspark.eduspark.dto.courseware;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * PPT生成请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PptGenerateRequest {

    /**
     * 课件标题
     */
    private String title;

    /**
     * 学科
     */
    private String subject;

    /**
     * 年级
     */
    private String grade;

    /**
     * 知识点列表
     */
    private List<String> knowledgePoints;

    /**
     * 幻灯片数量（默认10页）
     */
    private Integer slideCount;

    /**
     * 教学风格：formal/casual/explore
     */
    private String style;

    /**
     * 参考资料文本
     */
    private String referenceText;

    /**
     * 用户原始描述
     */
    private String userDescription;

    private String templateId;
}
