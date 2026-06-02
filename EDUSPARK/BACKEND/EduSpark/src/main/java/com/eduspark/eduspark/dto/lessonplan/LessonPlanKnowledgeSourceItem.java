package com.eduspark.eduspark.dto.lessonplan;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Knowledge source used during lesson-plan blueprint enrichment.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LessonPlanKnowledgeSourceItem {

    private Long fileId;

    private String fileName;

    private String excerpt;

    private Float score;
}
