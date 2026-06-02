package com.eduspark.eduspark.dto.knowledge;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 知识库文件分页查询请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeFilePageRequest {

    /**
     * 用户ID
     */
    @NotNull(message = "用户ID不能为空")
    private Long userId;

    /**
     * 课程空间 ID（可选）。null 表示返回全部；当传入时按该课程空间过滤。
     */
    private Long workspaceId;

    /**
     * 页码（从1开始）
     */
    @Min(value = 1, message = "页码最小为1")
    @Builder.Default
    private Integer page = 1;

    /**
     * 每页大小
     */
    @Min(value = 1, message = "每页大小最小为1")
    @Max(value = 100, message = "每页大小最大为100")
    @Builder.Default
    private Integer size = 10;

    /**
     * 文件状态筛选（可选）：0-处理中 1-成功 2-失败
     */
    private Integer status;

    /**
     * 文件类型筛选（可选）
     */
    private String fileType;

    /**
     * 关键词搜索（文件名）
     */
    private String keyword;
}
