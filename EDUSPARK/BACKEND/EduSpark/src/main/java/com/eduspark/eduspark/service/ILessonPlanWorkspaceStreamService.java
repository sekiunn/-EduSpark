package com.eduspark.eduspark.service;

import com.eduspark.eduspark.dto.lessonplan.LessonPlanDocumentResponse;
import com.eduspark.eduspark.dto.lessonplan.LessonPlanStreamDeltaResponse;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * SSE transport for lesson-plan workspace updates.
 */
public interface ILessonPlanWorkspaceStreamService {

    SseEmitter subscribe(Long documentId, LessonPlanDocumentResponse snapshot);

    void publishSnapshot(Long documentId, LessonPlanDocumentResponse snapshot);

    void publishStatus(Long documentId, LessonPlanDocumentResponse snapshot);

    void publishContentDelta(Long documentId, LessonPlanStreamDeltaResponse deltaResponse);

    void publishCompleted(Long documentId, LessonPlanDocumentResponse snapshot);

    void publishFailed(Long documentId, LessonPlanDocumentResponse snapshot);
}
