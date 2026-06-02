package com.eduspark.eduspark.service;

import com.eduspark.eduspark.dto.pptworkspace.PptDocumentResponse;
import com.eduspark.eduspark.dto.pptworkspace.PptSlideProgressEvent;
import com.eduspark.eduspark.dto.pptworkspace.PptStreamDeltaResponse;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * SSE transport for PPT workspace updates.
 */
public interface IPptWorkspaceStreamService {

    SseEmitter subscribe(Long documentId, PptDocumentResponse snapshot);

    void publishSnapshot(Long documentId, PptDocumentResponse snapshot);

    void publishStatus(Long documentId, PptDocumentResponse snapshot);

    void publishContentDelta(Long documentId, PptStreamDeltaResponse deltaResponse);

    void publishSlideSkeleton(Long documentId, PptSlideProgressEvent event);

    void publishSlideBackground(Long documentId, PptSlideProgressEvent event);

    void publishSlideContentDelta(Long documentId, PptStreamDeltaResponse deltaResponse);

    void publishSlideCompleted(Long documentId, PptSlideProgressEvent event);

    void publishCompleted(Long documentId, PptDocumentResponse snapshot);

    void publishFailed(Long documentId, PptDocumentResponse snapshot);
}
