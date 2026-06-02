package com.eduspark.eduspark.service;

import com.eduspark.eduspark.dto.interactive.InteractiveDocumentResponse;
import com.eduspark.eduspark.dto.interactive.InteractiveStreamDeltaResponse;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * SSE transport for interactive workspace updates.
 */
public interface IInteractiveWorkspaceStreamService {

    SseEmitter subscribe(Long documentId, InteractiveDocumentResponse snapshot);

    void publishSnapshot(Long documentId, InteractiveDocumentResponse snapshot);

    void publishStatus(Long documentId, InteractiveDocumentResponse snapshot);

    void publishContentDelta(Long documentId, InteractiveStreamDeltaResponse deltaResponse);

    void publishCompleted(Long documentId, InteractiveDocumentResponse snapshot);

    void publishFailed(Long documentId, InteractiveDocumentResponse snapshot);
}
