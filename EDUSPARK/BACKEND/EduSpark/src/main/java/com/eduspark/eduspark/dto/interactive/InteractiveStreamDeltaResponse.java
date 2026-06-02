package com.eduspark.eduspark.dto.interactive;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * Incremental HTML payload sent to the interactive workspace SSE stream.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InteractiveStreamDeltaResponse implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long documentId;

    private String delta;
}
