package com.eduspark.eduspark.dto.knowledge;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 知识库检索请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeSearchRequest {

    /**
     * 检索关键词
     */
    @NotBlank(message = "检索关键词不能为空")
    private String query;

    /**
     * 返回结果数量
     */
    @Min(value = 1, message = "topK最小为1")
    @Max(value = 50, message = "topK最大为50")
    @Builder.Default
    private Integer topK = 5;

    /**
     * 用户ID
     */
    @NotNull(message = "用户ID不能为空")
    private Long userId;

    /**
     * 课程空间 ID（可选，默认全部）。
     */
    private Long workspaceId;

    /**
     * 最低相似度阈值（0-1）
     */
    @Min(value = 0, message = "阈值最小为0")
    @Max(value = 1, message = "阈值最大为1")
    private Float threshold;

    /**
     * RRF向量权重（可选）
     */
    @Min(value = 0, message = "权重最小为0")
    @Max(value = 1, message = "权重最大为1")
    @Builder.Default
    private Float vectorWeight = 0.6f;

    /**
     * RRF BM25权重（可选）
     */
    @Min(value = 0, message = "权重最小为0")
    @Max(value = 1, message = "权重最大为1")
    @Builder.Default
    private Float bm25Weight = 0.4f;
}
