package com.eduspark.eduspark.controller.client;

import com.eduspark.eduspark.dto.common.Result;
import com.eduspark.eduspark.dto.lessonplan.LessonPlanContentUpdateRequest;
import com.eduspark.eduspark.dto.lessonplan.LessonPlanDocumentResponse;
import com.eduspark.eduspark.dto.lessonplan.LessonPlanRewriteRequest;
import com.eduspark.eduspark.dto.lessonplan.LessonPlanRewriteResponse;
import com.eduspark.eduspark.service.ILessonPlanWorkspaceService;
import com.eduspark.eduspark.service.ILessonPlanWorkspaceStreamService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Lesson-plan workspace APIs.
 */
@Validated
@RestController
@RequestMapping("/v1/lesson-plan/documents")
public class LessonPlanController {

    private final ILessonPlanWorkspaceService lessonPlanWorkspaceService;
    private final ILessonPlanWorkspaceStreamService lessonPlanWorkspaceStreamService;

    public LessonPlanController(ILessonPlanWorkspaceService lessonPlanWorkspaceService,
                                ILessonPlanWorkspaceStreamService lessonPlanWorkspaceStreamService) {
        this.lessonPlanWorkspaceService = lessonPlanWorkspaceService;
        this.lessonPlanWorkspaceStreamService = lessonPlanWorkspaceStreamService;
    }

    @GetMapping("/{documentId}")
    public Result<LessonPlanDocumentResponse> getDocument(
            @PathVariable Long documentId,
            HttpServletRequest httpRequest
    ) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        return Result.success(lessonPlanWorkspaceService.getDocument(documentId, userId));
    }

    @GetMapping(value = "/{documentId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamDocument(
            @PathVariable Long documentId,
            HttpServletRequest httpRequest
    ) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        LessonPlanDocumentResponse snapshot = lessonPlanWorkspaceService.getDocument(documentId, userId);
        return lessonPlanWorkspaceStreamService.subscribe(documentId, snapshot);
    }

    @PutMapping("/{documentId}/content")
    public Result<LessonPlanDocumentResponse> updateContent(
            @PathVariable Long documentId,
            @Valid @RequestBody LessonPlanContentUpdateRequest request,
            HttpServletRequest httpRequest
    ) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        return Result.success(lessonPlanWorkspaceService.updateDocumentContent(documentId, userId, request.getContent()));
    }

    @PostMapping("/{documentId}/rewrite")
    public Result<LessonPlanRewriteResponse> rewriteSelection(
            @PathVariable Long documentId,
            @Valid @RequestBody LessonPlanRewriteRequest request,
            HttpServletRequest httpRequest
    ) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        return Result.success(lessonPlanWorkspaceService.rewriteSelection(
                documentId,
                userId,
                request.getSelectedText(),
                request.getInstruction()
        ));
    }

    @PostMapping("/{documentId}/export")
    public Result<LessonPlanDocumentResponse> exportDocument(
            @PathVariable Long documentId,
            HttpServletRequest httpRequest
    ) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        return Result.success(lessonPlanWorkspaceService.exportDocument(documentId, userId));
    }
}
