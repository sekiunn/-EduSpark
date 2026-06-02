package com.eduspark.eduspark.dto.ppt;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PptTemplateClientResponse implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    private String templateCode;

    private String name;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long sceneId;

    private String sceneName;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long styleId;

    private String styleName;

    private String coverUrl;

    private List<String> previewImages;

    private String description;

    private Integer isDefault;
}
