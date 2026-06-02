package com.eduspark.eduspark.dto.interactive;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Structured template planning result for interactive workspaces.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InteractiveTemplatePlan {

    private String renderMode;

    private String interactiveType;

    private String templateId;

    private String templateVersion;

    private String title;

    private Map<String, Object> spec;
}
