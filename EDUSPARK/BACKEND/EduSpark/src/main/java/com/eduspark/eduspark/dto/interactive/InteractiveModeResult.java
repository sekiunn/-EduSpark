package com.eduspark.eduspark.dto.interactive;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Assistant response payload for the interactive mode.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InteractiveModeResult {

    private String message;

    private String cardType;

    private Object cardData;
}
