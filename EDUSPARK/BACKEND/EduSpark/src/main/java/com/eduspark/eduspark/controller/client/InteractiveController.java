package com.eduspark.eduspark.controller.client;

import com.eduspark.eduspark.dto.common.Result;
import com.eduspark.eduspark.dto.interactive.InteractiveContentUpdateRequest;
import com.eduspark.eduspark.dto.interactive.InteractiveDownloadPayload;
import com.eduspark.eduspark.dto.interactive.InteractiveDocumentResponse;
import com.eduspark.eduspark.service.IInteractiveWorkspaceService;
import com.eduspark.eduspark.service.IInteractiveWorkspaceStreamService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.nio.charset.StandardCharsets;

/**
 * Interactive workspace APIs.
 */
@Validated
@RestController
@RequestMapping("/v1/interactive/documents")
public class InteractiveController {

    private final IInteractiveWorkspaceService interactiveWorkspaceService;
    private final IInteractiveWorkspaceStreamService interactiveWorkspaceStreamService;

    public InteractiveController(IInteractiveWorkspaceService interactiveWorkspaceService,
                                 IInteractiveWorkspaceStreamService interactiveWorkspaceStreamService) {
        this.interactiveWorkspaceService = interactiveWorkspaceService;
        this.interactiveWorkspaceStreamService = interactiveWorkspaceStreamService;
    }

    @GetMapping("/{documentId}")
    public Result<InteractiveDocumentResponse> getDocument(
            @PathVariable Long documentId,
            HttpServletRequest httpRequest
    ) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        return Result.success(interactiveWorkspaceService.getDocument(documentId, userId));
    }

    @GetMapping(value = "/{documentId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamDocument(
            @PathVariable Long documentId,
            HttpServletRequest httpRequest
    ) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        InteractiveDocumentResponse snapshot = interactiveWorkspaceService.getDocument(documentId, userId);
        return interactiveWorkspaceStreamService.subscribe(documentId, snapshot);
    }

    @PutMapping("/{documentId}/content")
    public Result<InteractiveDocumentResponse> updateContent(
            @PathVariable Long documentId,
            @Valid @RequestBody InteractiveContentUpdateRequest request,
            HttpServletRequest httpRequest
    ) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        return Result.success(interactiveWorkspaceService.updateDocumentContent(documentId, userId, request.getContent()));
    }

    @PostMapping("/{documentId}/export")
    public Result<InteractiveDocumentResponse> exportDocument(
            @PathVariable Long documentId,
            HttpServletRequest httpRequest
    ) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        return Result.success(interactiveWorkspaceService.exportDocument(documentId, userId));
    }

    @GetMapping("/{documentId}/download")
    public ResponseEntity<InputStreamResource> downloadDocument(
            @PathVariable Long documentId,
            HttpServletRequest httpRequest
    ) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        InteractiveDownloadPayload payload = interactiveWorkspaceService.downloadDocument(documentId, userId);
        String contentDisposition = ContentDisposition.attachment()
                .filename(payload.getFileName(), StandardCharsets.UTF_8)
                .build()
                .toString();

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition)
                .contentType(MediaType.parseMediaType(payload.getContentType()))
                .body(new InputStreamResource(payload.getInputStream()));
    }
}
