package com.eduspark.eduspark.dto.courseware;

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
public class TemplateFillPlan implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private int targetSlideCount;

    private int extraContentPages;

    private List<TemplateSlideFill> slides;
}
