package com.eduspark.eduspark.dto.lessonplan;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * Incremental text payload sent to the lesson-plan workspace SSE stream.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LessonPlanStreamDeltaResponse implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long documentId;

    private String delta;
}
