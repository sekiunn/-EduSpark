package com.eduspark.eduspark.service.impl;

import com.eduspark.eduspark.dto.lessonplan.LessonPlanDocumentResponse;
import com.eduspark.eduspark.dto.lessonplan.LessonPlanStreamDeltaResponse;
import com.eduspark.eduspark.service.ILessonPlanWorkspaceStreamService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
@Service
public class LessonPlanWorkspaceStreamServiceImpl implements ILessonPlanWorkspaceStreamService {

    private static final long STREAM_TIMEOUT_MS = 30L * 60L * 1000L;

    private final Map<Long, CopyOnWriteArrayList<SseEmitter>> emitterRegistry = new ConcurrentHashMap<>();
    // 每个文档的"已流式内容"累积缓冲：content_delta 来时既广播又累积，
    // 让迟到连上的订阅者能用它一次性追平当前进度（解决"连上前的 delta 被丢弃"导致看不到流式）。
    private final Map<Long, StringBuilder> contentBuffers = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;

    public LessonPlanWorkspaceStreamServiceImpl(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public SseEmitter subscribe(Long documentId, LessonPlanDocumentResponse snapshot) {
        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MS);

        emitter.onCompletion(() -> removeEmitter(documentId, emitter));
        emitter.onTimeout(() -> {
            emitter.complete();
            removeEmitter(documentId, emitter);
        });
        emitter.onError(ex -> removeEmitter(documentId, emitter));

        // 在缓冲锁内"注册 + 发首个 snapshot"，与并发的 content_delta（同一把锁里累积+广播）互斥，
        // 既保证迟到的订阅者拿到"到目前为止"的全部流式内容，又不会漏发或重复某条 delta。
        StringBuilder buffer = contentBuffers.computeIfAbsent(documentId, key -> new StringBuilder());
        synchronized (buffer) {
            emitterRegistry.computeIfAbsent(documentId, key -> new CopyOnWriteArrayList<>()).add(emitter);
            String accumulated = buffer.toString();
            if (snapshot != null && !accumulated.isEmpty()) {
                // 用累积内容覆盖快照正文，前端一连上就追平当前进度；之后的 delta 继续 live 追加。
                snapshot.setContent(accumulated);
            }
            sendEvent(emitter, "snapshot", snapshot);
        }
        return emitter;
    }

    @Override
    public void publishSnapshot(Long documentId, LessonPlanDocumentResponse snapshot) {
        broadcast(documentId, "snapshot", snapshot, false);
    }

    @Override
    public void publishStatus(Long documentId, LessonPlanDocumentResponse snapshot) {
        broadcast(documentId, "status", snapshot, false);
    }

    @Override
    public void publishContentDelta(Long documentId, LessonPlanStreamDeltaResponse deltaResponse) {
        // 既广播给已连接订阅者，又累积进缓冲（供之后迟到的订阅者补齐进度）。同一把锁与 subscribe 互斥。
        StringBuilder buffer = contentBuffers.computeIfAbsent(documentId, key -> new StringBuilder());
        synchronized (buffer) {
            if (deltaResponse != null && deltaResponse.getDelta() != null) {
                buffer.append(deltaResponse.getDelta());
            }
            broadcast(documentId, "content_delta", deltaResponse, false);
        }
    }

    @Override
    public void publishCompleted(Long documentId, LessonPlanDocumentResponse snapshot) {
        broadcast(documentId, "completed", snapshot, true);
        contentBuffers.remove(documentId);
    }

    @Override
    public void publishFailed(Long documentId, LessonPlanDocumentResponse snapshot) {
        broadcast(documentId, "failed", snapshot, true);
        contentBuffers.remove(documentId);
    }

    private void broadcast(Long documentId, String eventName, Object payload, boolean closeAfterSend) {
        List<SseEmitter> emitters = emitterRegistry.get(documentId);
        if (emitters == null || emitters.isEmpty()) {
            return;
        }

        for (SseEmitter emitter : emitters) {
            if (!sendEvent(emitter, eventName, payload)) {
                removeEmitter(documentId, emitter);
                continue;
            }

            if (closeAfterSend) {
                try {
                    emitter.complete();
                } catch (Exception ignored) {
                    // no-op
                }
                removeEmitter(documentId, emitter);
            }
        }
    }

    private boolean sendEvent(SseEmitter emitter, String eventName, Object payload) {
        try {
            Object eventData = payload instanceof String ? payload : objectMapper.writeValueAsString(payload);
            emitter.send(SseEmitter.event().name(eventName).data(eventData));
            return true;
        } catch (IOException | IllegalStateException e) {
            log.debug("Lesson-plan SSE send failed: {}", e.getMessage());
            return false;
        }
    }

    private void removeEmitter(Long documentId, SseEmitter emitter) {
        CopyOnWriteArrayList<SseEmitter> emitters = emitterRegistry.get(documentId);
        if (emitters == null) {
            return;
        }

        emitters.remove(emitter);
        if (emitters.isEmpty()) {
            emitterRegistry.remove(documentId);
        }
    }
}
