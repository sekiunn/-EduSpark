package com.eduspark.eduspark.controller.client;

import com.eduspark.eduspark.dto.common.Result;
import com.eduspark.eduspark.dto.pptworkspace.PptDocumentResponse;
import com.eduspark.eduspark.service.IPptWorkspaceService;
import com.eduspark.eduspark.service.IPptWorkspaceStreamService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * PPT workspace APIs.
 */
@Validated
@RestController
@RequestMapping("/v1/ppt/documents")
public class PptWorkspaceController {

    private final IPptWorkspaceService pptWorkspaceService;
    private final IPptWorkspaceStreamService pptWorkspaceStreamService;

    public PptWorkspaceController(IPptWorkspaceService pptWorkspaceService,
                                  IPptWorkspaceStreamService pptWorkspaceStreamService) {
        this.pptWorkspaceService = pptWorkspaceService;
        this.pptWorkspaceStreamService = pptWorkspaceStreamService;
    }

    @GetMapping("/{documentId}")
    public Result<PptDocumentResponse> getDocument(
            @PathVariable Long documentId,
            HttpServletRequest httpRequest
    ) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        return Result.success(pptWorkspaceService.getDocument(documentId, userId));
    }

    @GetMapping(value = "/{documentId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamDocument(
            @PathVariable Long documentId,
            HttpServletRequest httpRequest
    ) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        PptDocumentResponse snapshot = pptWorkspaceService.getDocument(documentId, userId);
        return pptWorkspaceStreamService.subscribe(documentId, snapshot);
    }

    @PostMapping("/{documentId}/export")
    public Result<PptDocumentResponse> exportDocument(
            @PathVariable Long documentId,
            HttpServletRequest httpRequest
    ) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        return Result.success(pptWorkspaceService.exportDocument(documentId, userId));
    }
}
