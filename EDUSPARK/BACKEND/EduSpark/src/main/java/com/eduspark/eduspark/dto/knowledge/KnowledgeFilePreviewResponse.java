package com.eduspark.eduspark.dto.knowledge;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
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
public class KnowledgeFilePreviewResponse implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long fileId;

    private String fileName;

    private String fileType;

    private String category;

    private String description;

    private Integer status;

    private String statusText;

    private String errorMessage;

    private Integer chunkCount;

    private Integer contentLength;

    private String contentPreview;

    private Boolean truncated;

    private List<KnowledgeChunkPreviewResponse> chunks;
}
