package com.eduspark.eduspark.service.impl;

import com.eduspark.eduspark.dto.courseware.LessonPlanRequest;
import com.eduspark.eduspark.service.ILessonPlanWriterService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.function.Consumer;

/**
 * Routes lesson-plan writing to the configured writer backend.
 */
@Slf4j
@Service
public class LessonPlanWriterServiceImpl implements ILessonPlanWriterService {

    @Value("${lesson-plan.writer.mode:local}")
    private String writerMode;

    @Value("${lesson-plan.writer.fallback-to-local:true}")
    private boolean fallbackToLocal;

    private final ExternalLessonPlanWriterDelegate externalWriter;
    private final LocalLessonPlanWriterDelegate localWriter;

    public LessonPlanWriterServiceImpl(ExternalLessonPlanWriterDelegate externalWriter,
                                       LocalLessonPlanWriterDelegate localWriter) {
        this.externalWriter = externalWriter;
        this.localWriter = localWriter;
    }

    @Override
    public String streamDraft(LessonPlanRequest request, Consumer<String> onChunk) {
        if (!shouldUseExternal()) {
            log.info("Lesson-plan writer route: local (mode={})", writerMode);
            return localWriter.streamDraft(request, onChunk);
        }

        if (!externalWriter.isConfigured()) {
            if (!fallbackToLocal) {
                throw new IllegalStateException("External lesson-plan writer is not configured");
            }
            log.warn("Lesson-plan writer route: external requested but not configured, fallback to local");
            return localWriter.streamDraft(request, onChunk);
        }

        try {
            log.info("Lesson-plan writer route: external model={}, baseUrl={}",
                    externalWriter.getConfiguredModel(), externalWriter.getConfiguredBaseUrl());
            return externalWriter.streamDraft(request, onChunk);
        } catch (Exception e) {
            if (!fallbackToLocal) {
                throw e;
            }
            log.warn("Lesson-plan writer route: external failed, fallback to local writer: {}", e.getMessage());
            return localWriter.streamDraft(request, onChunk);
        }
    }

    private boolean shouldUseExternal() {
        return "external".equalsIgnoreCase(normalize(writerMode));
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
