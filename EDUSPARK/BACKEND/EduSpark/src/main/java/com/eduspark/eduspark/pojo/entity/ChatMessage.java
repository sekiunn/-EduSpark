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
 * 对话消息实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("chat_message")
public class ChatMessage implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long sessionId;

    private String role;

    private String content;

    private String intentType;

    private Integer layer;

    private String layerDesc;

    private Integer costMs;

    /**
     * 卡片类型：blueprint_confirm / generation_complete / null
     */
    private String cardType;

    /**
     * 卡片数据（JSON格式，供前端渲染特殊UI）
     */
    private String cardData;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}