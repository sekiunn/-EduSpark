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
 * 文本分块实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("knowledge_chunk")
public class KnowledgeChunk implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long fileId;

    private Integer chunkIndex;

    private String chunkText;

    private String chunkHash;

    private Integer tokenCount;

    /**
     * 向量 embedding (PostgreSQL vector(1024))
     * 存储为 float[]，MyBatis-Plus 自动处理
     */
    private float[] embedding;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    // ==================== 非持久化字段（用于查询结果） ====================

    @TableField(exist = false)
    private String fileName;

    @TableField(exist = false)
    private Float score;

    @TableField(exist = false)
    private Float vectorScore;

    @TableField(exist = false)
    private Float bm25Score;

    @TableField(exist = false)
    private String source;
}