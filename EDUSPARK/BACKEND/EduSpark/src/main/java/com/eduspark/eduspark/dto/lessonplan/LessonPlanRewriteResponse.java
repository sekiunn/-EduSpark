package com.eduspark.eduspark.dto.lessonplan;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response payload for AI-assisted lesson-plan fragment rewriting.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LessonPlanRewriteResponse {

    private Long documentId;

    private String selectedText;

    private String instruction;

    private String suggestion;
}
