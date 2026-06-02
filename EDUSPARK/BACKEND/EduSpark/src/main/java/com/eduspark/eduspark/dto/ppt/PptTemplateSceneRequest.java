package com.eduspark.eduspark.dto.ppt;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PptTemplateSceneRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotBlank(message = "场景编码不能为空")
    private String sceneCode;

    @NotBlank(message = "场景名称不能为空")
    private String sceneName;

    private Integer sort;

    private Integer enabled;
}
