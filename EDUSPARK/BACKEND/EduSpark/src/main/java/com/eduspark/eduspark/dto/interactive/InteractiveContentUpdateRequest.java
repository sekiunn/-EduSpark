package com.eduspark.eduspark.dto.interactive;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Update payload for the interactive workspace HTML content.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InteractiveContentUpdateRequest {

    @NotNull(message = "content 不能为空")
    private String content;
}
