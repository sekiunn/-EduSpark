package com.eduspark.eduspark.dto.courseware;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 教案生成响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LessonPlanResponse {

    /**
     * 是否成功
     */
    private boolean success;

    /**
     * 文件路径（服务器存储路径）
     */
    private String filePath;

    /**
     * 下载URL
     */
    private String downloadUrl;

    /**
     * 文件名
     */
    private String fileName;

    /**
     * 文件大小（字节）
     */
    private Long fileSize;

    /**
     * 生成的教案正文摘要（用于预览）
     */
    private String preview;

    private String content;

    /**
     * 错误信息
     */
    private String error;
}
