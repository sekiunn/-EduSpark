package com.eduspark.eduspark.dto.knowledge;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 知识库检索响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeSearchResponse implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 检索关键词
     */
    private String query;

    /**
     * 检索耗时（毫秒）
     */
    private Long costMs;

    /**
     * 结果总数
     */
    private Integer total;

    /**
     * 检索结果列表
     */
    private List<KnowledgeSearchResult> results;

    // ==================== 内部类 ====================

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class KnowledgeSearchResult implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        /**
         * 分块ID
         */
        private Long chunkId;

        /**
         * 文件ID
         */
        private Long fileId;

        /**
         * 文件名
         */
        private String fileName;

        /**
         * 分块文本内容
         */
        private String text;

        /**
         * 综合得分（RRF融合后）
         */
        private Float score;

        /**
         * 检索来源：vector / bm25 / hybrid
         */
        private String source;

        /**
         * 向量检索得分
         */
        private Float vectorScore;

        /**
         * BM25检索得分
         */
        private Float bm25Score;
    }
}
