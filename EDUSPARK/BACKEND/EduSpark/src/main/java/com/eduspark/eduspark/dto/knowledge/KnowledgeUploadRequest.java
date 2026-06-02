package com.eduspark.eduspark.dto.knowledge;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 知识库文件上传请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeUploadRequest {

    /**
     * 用户ID
     */
    @NotNull(message = "用户ID不能为空")
    private Long userId;

    /**
     * 所属课程空间 ID（可选）。
     */
    private Long workspaceId;

    /**
     * 文件分类（可选）
     */
    @Size(max = 100, message = "分类名称最多100字符")
    private String category;

    /**
     * 文件描述（可选）
     */
    @Size(max = 500, message = "描述最多500字符")
    private String description;
}
