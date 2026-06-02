package com.eduspark.eduspark.dto.knowledge;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeSearchTestRequest {

    @NotBlank(message = "检索关键词不能为空")
    private String query;

    @NotNull(message = "用户ID不能为空")
    private Long userId;

    /** 课程空间过滤（可选）。 */
    private Long workspaceId;

    @Min(value = 1, message = "topK最小为1")
    @Max(value = 20, message = "topK最大为20")
    @Builder.Default
    private Integer topK = 5;

    @Min(value = 100, message = "maxTokens最小为100")
    @Max(value = 8000, message = "maxTokens最大为8000")
    @Builder.Default
    private Integer maxTokens = 2000;

    @Builder.Default
    private Float vectorWeight = 0.6f;

    @Builder.Default
    private Float bm25Weight = 0.4f;
}
