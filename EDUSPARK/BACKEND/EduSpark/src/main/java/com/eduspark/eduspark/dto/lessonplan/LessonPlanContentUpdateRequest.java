package com.eduspark.eduspark.dto.lessonplan;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Update payload for the lesson-plan workspace content.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LessonPlanContentUpdateRequest {

    @NotNull(message = "content 不能为空")
    private String content;
}
