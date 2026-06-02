package com.eduspark.eduspark.service;

import com.eduspark.eduspark.dto.courseware.LessonPlanRequest;

import java.util.function.Consumer;

/**
 * Writes the final lesson-plan draft after blueprint enrichment.
 */
public interface ILessonPlanWriterService {

    String streamDraft(LessonPlanRequest request, Consumer<String> onChunk);
}
