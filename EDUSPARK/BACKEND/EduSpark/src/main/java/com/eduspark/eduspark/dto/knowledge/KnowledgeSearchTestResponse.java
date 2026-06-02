package com.eduspark.eduspark.dto.knowledge;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeSearchTestResponse implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String query;

    private Integer topK;

    private Integer maxTokens;

    private Float vectorWeight;

    private Float bm25Weight;

    private Long costMs;

    private Integer total;

    private Integer usedChunkCount;

    private Integer contextLength;

    private String context;

    private List<KnowledgeSearchResponse.KnowledgeSearchResult> results;
}
