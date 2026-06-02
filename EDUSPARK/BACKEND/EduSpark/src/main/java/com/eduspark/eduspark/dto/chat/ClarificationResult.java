package com.eduspark.eduspark.dto.chat;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * Result returned by the clarification flow.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClarificationResult implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String message;

    /**
     * Supported card types:
     * - blueprint_confirm
     * - generation_pending
     * - generation_complete
     * - null for plain messages
     */
    private String cardType;

    /**
     * Arbitrary card payload for the frontend.
     */
    private Object cardData;

    /**
     * Kept for compatibility with the existing response shape.
     */
    private boolean triggerGeneration;

    public static ClarificationResult of(String message) {
        return ClarificationResult.builder()
                .message(message)
                .build();
    }

    public static ClarificationResult withCard(String message, String cardType, Object cardData) {
        return ClarificationResult.builder()
                .message(message)
                .cardType(cardType)
                .cardData(cardData)
                .build();
    }

    public static ClarificationResult withCard(String message, String cardType) {
        return withCard(message, cardType, null);
    }

    public static ClarificationResult generationPending(String message, Object cardData) {
        return ClarificationResult.builder()
                .message(message)
                .cardType("generation_pending")
                .cardData(cardData)
                .triggerGeneration(true)
                .build();
    }
}
