package com.eduspark.eduspark.dto.chat;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 对话响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatResponse implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String sessionId;

    private String answer;

    /**
     * 本次检索层级：1-知识库 2-联网 3-LLM兜底
     */
    private Integer layer;

    /**
     * 层级描述
     */
    private String layerDesc;

    /**
     * 知识库检索结果（用于前端展示来源卡片）
     */
    private List<KnowledgeSource> knowledgeSources;

    /**
     * 是否推荐上传资料
     */
    private Boolean recommendedToUpload;

    /**
     * 推荐上传提示文本
     */
    private String uploadRecommendation;

    /**
     * 是否为部分知识（知识库只有部分相关内容）
     */
    private Boolean hasPartialKnowledge;

    /**
     * 检索耗时（毫秒）
     */
    private Long costMs;

    /**
     * 生成状态（teaching模式确认后可能返回processing）
     */
    private String generationStatus;

    /**
     * 卡片类型：blueprint_confirm / generation_complete / null
     */
    private String cardType;

    /**
     * 卡片数据（JSON格式，供前端渲染特殊UI）
     */
    private Object cardData;

    /**
     * 知识来源
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class KnowledgeSource implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        private Long chunkId;
        private Long fileId;
        private String fileName;
        private String text;
        private Float score;
    }
}
