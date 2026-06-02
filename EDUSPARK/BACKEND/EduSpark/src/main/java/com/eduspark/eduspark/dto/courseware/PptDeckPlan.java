package com.eduspark.eduspark.dto.courseware;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Structured deck plan used for rendering a real PPT document.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PptDeckPlan {

    private String title;

    private String subtitle;

    private Integer slideCount;

    private String themeName;

    private String style;

    private List<PptSlidePlan> slides;
}
