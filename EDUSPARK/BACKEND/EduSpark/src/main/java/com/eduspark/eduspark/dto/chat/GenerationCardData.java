package com.eduspark.eduspark.dto.chat;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * Frontend payload for generation status cards.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GenerationCardData implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String mode;
    private String modeName;
    private String title;
    private String status;
    private String statusText;
    private String fileName;
    private String downloadUrl;
    private String preview;
    private String summary;
    private List<String> outline;
}
