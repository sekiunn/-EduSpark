package com.eduspark.eduspark.dto.courseware;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * PPT生成响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PptGenerateResponse {

    private boolean success;
    private String filePath;
    private String downloadUrl;
    private String fileName;
    private Long fileSize;

    /**
     * PPT内容大纲（用于预览）
     */
    private List<String> outline;

    private String planningMarkdown;

    private PptDeckPlan plan;

    private String error;
}
