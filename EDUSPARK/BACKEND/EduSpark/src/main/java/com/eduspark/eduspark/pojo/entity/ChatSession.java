package com.eduspark.eduspark.pojo.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 对话会话实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("chat_session")
public class ChatSession implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String sessionId;

    private Long userId;

    private String title;

    private Integer status;

    private String lastMessage;

    private Integer messageCount;

    private String mode;

    private String stage;

    private String teachingElements;

    /**
     * 生成状态：pending / processing / completed / failed
     */
    private String generationStatus;

    /**
     * 生成结果（JSON或错误信息）
     */
    private String generationResult;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer isDeleted;

    public static final class Status {
        public static final int ACTIVE = 1;
        public static final int ENDED = 0;
    }

    public static final class Mode {
        public static final String PPT = "ppt";
        public static final String LESSON_PLAN = "lesson_plan";
        public static final String INTERACTIVE = "interactive";
    }

    public static final class Stage {
        public static final String IDLE = "idle";
        public static final String CLARIFYING = "clarifying";
        public static final String CONFIRMING = "confirming";
        public static final String GENERATING = "generating";
        public static final String COMPLETED = "completed";
    }

    public static final class GenerationStatus {
        public static final String PENDING = "pending";
        public static final String PROCESSING = "processing";
        public static final String COMPLETED = "completed";
        public static final String FAILED = "failed";
    }
}