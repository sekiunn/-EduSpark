package com.eduspark.eduspark.dto.interactive;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Frontend payload for the interactive workspace.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InteractiveDocumentResponse {

    private Long documentId;

    private String title;

    private String status;

    private String statusText;

    private String summary;

    private String htmlContent;

    private String downloadUrl;

    private String errorMessage;

    private Map<String, Object> sourceContext;

    private Map<String, Object> enrichedContext;

    private LocalDateTime updateTime;
}
