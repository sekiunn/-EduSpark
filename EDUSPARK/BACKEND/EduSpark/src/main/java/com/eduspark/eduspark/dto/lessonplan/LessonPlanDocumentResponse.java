package com.eduspark.eduspark.dto.lessonplan;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Frontend payload for the lesson-plan workspace.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LessonPlanDocumentResponse {

    private Long documentId;

    private String title;

    private String status;

    private String statusText;

    private String summary;

    private String content;

    private String preview;

    private String downloadUrl;

    private String errorMessage;

    private Map<String, Object> sourceBlueprint;

    private Map<String, Object> enrichedBlueprint;

    private List<LessonPlanKnowledgeSourceItem> knowledgeSources;

    private List<String> enrichmentHighlights;

    private LocalDateTime updateTime;
}
