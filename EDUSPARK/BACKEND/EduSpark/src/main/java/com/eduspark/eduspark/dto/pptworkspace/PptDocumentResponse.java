package com.eduspark.eduspark.dto.pptworkspace;

import com.eduspark.eduspark.dto.courseware.PptDeckPlan;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Frontend payload for the PPT workspace.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PptDocumentResponse {

    private Long documentId;

    private String title;

    private String status;

    private String statusText;

    private String summary;

    private String planningMarkdown;

    private String downloadUrl;

    private String fileName;

    private String errorMessage;

    private String templateId;

    private String templateName;

    private Map<String, Object> sourceBlueprint;

    private Map<String, Object> enrichedBlueprint;

    private List<PptKnowledgeSourceItem> knowledgeSources;

    private List<String> enrichmentHighlights;

    private List<String> outline;

    private PptDeckPlan plan;

    private List<PptSlideProgressEvent> slidesProgress;

    private LocalDateTime updateTime;
}
