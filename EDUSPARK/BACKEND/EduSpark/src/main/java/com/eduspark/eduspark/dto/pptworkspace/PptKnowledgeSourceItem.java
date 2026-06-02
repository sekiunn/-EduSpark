package com.eduspark.eduspark.dto.pptworkspace;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Knowledge source used during PPT blueprint enrichment.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PptKnowledgeSourceItem {

    private Long fileId;

    private String fileName;

    private String excerpt;

    private Float score;
}
