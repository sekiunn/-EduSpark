package com.eduspark.eduspark.service;

import com.eduspark.eduspark.dto.chat.TeachingBlueprint;

/**
 * Enriches a PPT blueprint with locally retrieved knowledge and references.
 */
public interface IPptBlueprintEnrichmentService {

    TeachingBlueprint enrichBlueprint(Long userId, TeachingBlueprint sourceBlueprint);
}
