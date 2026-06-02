package com.eduspark.eduspark.dto.pptworkspace;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Card payload for entering the PPT workspace.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PptWorkspaceCardData {

    private Long documentId;

    private String mode;

    private String modeName;

    private String title;

    private String status;

    private String statusText;

    private String summary;

    private String templateId;

    private String templateName;
}
