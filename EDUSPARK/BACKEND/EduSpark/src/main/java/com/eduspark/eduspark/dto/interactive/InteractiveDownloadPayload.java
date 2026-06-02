package com.eduspark.eduspark.dto.interactive;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.InputStream;

/**
 * Internal payload for authorized interactive document downloads.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InteractiveDownloadPayload {

    private InputStream inputStream;

    private String fileName;

    private String contentType;
}
