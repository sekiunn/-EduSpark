package com.eduspark.eduspark.dto.ppt;

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
public class TemplateSlideStructure implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private int slideIndex;

    private String slideRole;

    private List<TemplateMarkerInfo> markers;
}
