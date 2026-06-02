package com.eduspark.eduspark.pojo.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Editable PPT workspace document stored per chat session.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("ppt_document")
public class PptDocument implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long sessionId;

    private Long userId;

    private String title;

    private String status;

    private String summary;

    private String templateId;

    private String templateName;

    private String sourceBlueprintJson;

    private String enrichedBlueprintJson;

    private String planningMarkdown;

    private String planJson;

    private String slidesProgressJson;

    private String downloadUrl;

    private String exportFilePath;

    private String fileName;

    private String errorMessage;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer isDeleted;

    public static final class Status {
        public static final String PREPARING = "preparing";
        public static final String RETRIEVING = "retrieving";
        public static final String ENRICHING = "enriching";
        public static final String PLANNING = "planning";
        public static final String STRUCTURING = "structuring";
        public static final String RENDERING = "rendering";
        public static final String COMPLETED = "completed";
        public static final String FAILED = "failed";

        private Status() {
        }
    }
}
