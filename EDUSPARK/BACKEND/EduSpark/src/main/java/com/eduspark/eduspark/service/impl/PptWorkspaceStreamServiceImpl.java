package com.eduspark.eduspark.service.impl;

import com.eduspark.eduspark.dto.pptworkspace.PptDocumentResponse;
import com.eduspark.eduspark.dto.pptworkspace.PptSlideProgressEvent;
import com.eduspark.eduspark.dto.pptworkspace.PptStreamDeltaResponse;
import com.eduspark.eduspark.service.IPptWorkspaceStreamService;
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
public class PptWorkspaceStreamServiceImpl implements IPptWorkspaceStreamService {

    private static final long STREAM_TIMEOUT_MS = 30L * 60L * 1000L;

    private final Map<Long, CopyOnWriteArrayList<SseEmitter>> emitterRegistry = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;

    public PptWorkspaceStreamServiceImpl(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public SseEmitter subscribe(Long documentId, PptDocumentResponse snapshot) {
        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MS);
        emitterRegistry.computeIfAbsent(documentId, key -> new CopyOnWriteArrayList<>()).add(emitter);

        emitter.onCompletion(() -> removeEmitter(documentId, emitter));
        emitter.onTimeout(() -> {
            emitter.complete();
            removeEmitter(documentId, emitter);
        });
        emitter.onError(ex -> removeEmitter(documentId, emitter));

        sendEvent(emitter, "snapshot", snapshot);
        return emitter;
    }

    @Override
    public void publishSnapshot(Long documentId, PptDocumentResponse snapshot) {
        broadcast(documentId, "snapshot", snapshot, false);
    }

    @Override
    public void publishStatus(Long documentId, PptDocumentResponse snapshot) {
        broadcast(documentId, "status", snapshot, false);
    }

    @Override
    public void publishContentDelta(Long documentId, PptStreamDeltaResponse deltaResponse) {
        broadcast(documentId, "content_delta", deltaResponse, false);
    }

    @Override
    public void publishSlideSkeleton(Long documentId, PptSlideProgressEvent event) {
        broadcast(documentId, "slide.skeleton", event, false);
    }

    @Override
    public void publishSlideBackground(Long documentId, PptSlideProgressEvent event) {
        broadcast(documentId, "slide.background", event, false);
    }

    @Override
    public void publishSlideContentDelta(Long documentId, PptStreamDeltaResponse deltaResponse) {
        broadcast(documentId, "slide.content_delta", deltaResponse, false);
    }

    @Override
    public void publishSlideCompleted(Long documentId, PptSlideProgressEvent event) {
        broadcast(documentId, "slide.completed", event, false);
    }

    @Override
    public void publishCompleted(Long documentId, PptDocumentResponse snapshot) {
        broadcast(documentId, "completed", snapshot, true);
    }

    @Override
    public void publishFailed(Long documentId, PptDocumentResponse snapshot) {
        broadcast(documentId, "failed", snapshot, true);
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
            log.debug("PPT SSE send failed: {}", e.getMessage());
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
