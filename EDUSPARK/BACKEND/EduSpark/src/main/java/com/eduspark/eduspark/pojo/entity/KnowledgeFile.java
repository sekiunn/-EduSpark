package com.eduspark.eduspark.pojo.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 知识库文件实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("knowledge_file")
public class KnowledgeFile implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String fileName;

    private String fileType;

    private Long fileSize;

    private String filePath;

    private String fileHash;

    private Long userId;

    /** 所属课程空间 ID（可为空，兼容历史未归类的文件）。 */
    private Long workspaceId;

    private Integer status;

    private Integer chunkCount;

    private String errorMessage;

    private String category;

    private String description;

    private Map<String, Object> metadata;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer isDeleted;

    public static final class Status {
        public static final int PROCESSING = 0;
        public static final int SUCCESS = 1;
        public static final int FAILED = 2;

        private Status() {}
    }

    public String getStatusText() {
        return switch (this.status) {
            case Status.PROCESSING -> "处理中";
            case Status.SUCCESS -> "成功";
            case Status.FAILED -> "失败";
            default -> "未知";
        };
    }
}