package com.eduspark.eduspark.service;

import com.eduspark.eduspark.dto.chat.TeachingBlueprint;

/**
 * Enriches a lesson-plan blueprint with locally retrieved knowledge and local LLM reasoning.
 */
public interface ILessonPlanBlueprintEnrichmentService {

    TeachingBlueprint enrichBlueprint(Long userId, TeachingBlueprint sourceBlueprint);
}
