package com.eduspark.eduspark.dto.chat;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GenerationStatusResponse {

    private String sessionId;
    private String generationStatus;
    private String generationResult;
}
