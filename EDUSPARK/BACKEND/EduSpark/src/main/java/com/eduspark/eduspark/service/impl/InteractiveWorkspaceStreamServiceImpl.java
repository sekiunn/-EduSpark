package com.eduspark.eduspark.service.impl;

import com.eduspark.eduspark.dto.interactive.InteractiveDocumentResponse;
import com.eduspark.eduspark.dto.interactive.InteractiveStreamDeltaResponse;
import com.eduspark.eduspark.service.IInteractiveWorkspaceStreamService;
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
public class InteractiveWorkspaceStreamServiceImpl implements IInteractiveWorkspaceStreamService {

    private static final long STREAM_TIMEOUT_MS = 30L * 60L * 1000L;

    private final Map<Long, CopyOnWriteArrayList<SseEmitter>> emitterRegistry = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;

    public InteractiveWorkspaceStreamServiceImpl(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public SseEmitter subscribe(Long documentId, InteractiveDocumentResponse snapshot) {
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
    public void publishSnapshot(Long documentId, InteractiveDocumentResponse snapshot) {
        broadcast(documentId, "snapshot", snapshot, false);
    }

    @Override
    public void publishStatus(Long documentId, InteractiveDocumentResponse snapshot) {
        broadcast(documentId, "status", snapshot, false);
    }

    @Override
    public void publishContentDelta(Long documentId, InteractiveStreamDeltaResponse deltaResponse) {
        broadcast(documentId, "content_delta", deltaResponse, false);
    }

    @Override
    public void publishCompleted(Long documentId, InteractiveDocumentResponse snapshot) {
        broadcast(documentId, "completed", snapshot, true);
    }

    @Override
    public void publishFailed(Long documentId, InteractiveDocumentResponse snapshot) {
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
            log.debug("Interactive SSE send failed: {}", e.getMessage());
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
