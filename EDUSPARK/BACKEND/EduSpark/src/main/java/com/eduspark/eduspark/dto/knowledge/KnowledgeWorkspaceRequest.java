package com.eduspark.eduspark.dto.knowledge;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 课程空间创建/更新请求。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeWorkspaceRequest {

    @NotNull(message = "用户ID不能为空")
    private Long userId;

    @NotBlank(message = "课程名称不能为空")
    @Size(max = 80, message = "课程名称最长 80 字符")
    private String name;

    @Size(max = 300, message = "课程描述最长 300 字符")
    private String description;

    @Size(max = 20)
    private String coverColor;

    private Integer sort;
}
