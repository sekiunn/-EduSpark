package com.eduspark.eduspark.dto.knowledge;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 知识库文件响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeFileResponse implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 文件ID
     */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long fileId;

    /**
     * 文件名
     */
    private String fileName;

    /**
     * 文件类型
     */
    private String fileType;

    /**
     * 文件大小（字节）
     */
    private Long fileSize;

    /**
     * 文件大小（格式化）
     */
    private String fileSizeText;

    /**
     * 分块数量
     */
    private Integer chunkCount;

    /**
     * 处理状态：0-处理中 1-成功 2-失败
     */
    private Integer status;

    /**
     * 状态文本
     */
    private String statusText;

    /**
     * 错误信息
     */
    private String errorMessage;

    /**
     * 文件分类
     */
    private String category;

    /**
     * 所属课程空间 ID
     */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long workspaceId;

    /**
     * 文件描述
     */
    private String description;

    /**
     * 创建时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;

    /**
     * 计算文件大小文本
     */
    public String getFileSizeText() {
        if (fileSize == null) return "0 B";
        if (fileSize < 1024) return fileSize + " B";
        if (fileSize < 1024 * 1024) return String.format("%.1f KB", fileSize / 1024.0);
        if (fileSize < 1024 * 1024 * 1024) {
            return String.format("%.1f MB", fileSize / (1024.0 * 1024));
        }
        return String.format("%.1f GB", fileSize / (1024.0 * 1024 * 1024));
    }
}
