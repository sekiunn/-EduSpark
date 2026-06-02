package com.eduspark.eduspark.dto.ppt;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
public class PptTemplateStyleRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotNull(message = "所属场景不能为空")
    private Long sceneId;

    @NotBlank(message = "风格编码不能为空")
    private String styleCode;

    @NotBlank(message = "风格名称不能为空")
    private String styleName;

    private Integer sort;

    private Integer enabled;
}
