package com.eduspark.eduspark.dto.lessonplan;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request payload for AI-assisted lesson-plan fragment rewriting.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LessonPlanRewriteRequest {

    @NotBlank(message = "selectedText cannot be blank")
    private String selectedText;

    @NotBlank(message = "instruction cannot be blank")
    private String instruction;
}
