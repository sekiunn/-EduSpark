package com.eduspark.eduspark.dto.interactive;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * Card payload for entering the interactive workspace.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InteractiveStageCardData implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long documentId;

    private String mode;

    private String modeName;

    private String title;

    private String status;

    private String statusText;

    private String summary;
}
