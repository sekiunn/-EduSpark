package com.eduspark.eduspark.controller.client;

import com.eduspark.eduspark.dto.common.PageResponse;
import com.eduspark.eduspark.dto.common.Result;
import com.eduspark.eduspark.dto.knowledge.KnowledgeChunkPreviewResponse;
import com.eduspark.eduspark.dto.knowledge.KnowledgeFilePageRequest;
import com.eduspark.eduspark.dto.knowledge.KnowledgeFilePreviewResponse;
import com.eduspark.eduspark.dto.knowledge.KnowledgeFileResponse;
import com.eduspark.eduspark.dto.knowledge.KnowledgeSearchRequest;
import com.eduspark.eduspark.dto.knowledge.KnowledgeSearchResponse;
import com.eduspark.eduspark.dto.knowledge.KnowledgeSearchTestRequest;
import com.eduspark.eduspark.dto.knowledge.KnowledgeSearchTestResponse;
import com.eduspark.eduspark.dto.knowledge.KnowledgeWorkspaceRequest;
import com.eduspark.eduspark.dto.knowledge.KnowledgeWorkspaceResponse;
import com.eduspark.eduspark.service.IContextBuilderService;
import com.eduspark.eduspark.service.IEmbeddingService;
import com.eduspark.eduspark.service.IKnowledgeService;
import com.eduspark.eduspark.service.IKnowledgeWorkspaceService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * 知识库管理接口（客户端）
 */
@Slf4j
@Validated
@RestController
@RequestMapping("/v1/knowledge")
public class KnowledgeController {

    private final IKnowledgeService knowledgeService;
    private final IEmbeddingService embeddingService;
    private final IContextBuilderService contextBuilderService;
    private final IKnowledgeWorkspaceService workspaceService;

    public KnowledgeController(
            IKnowledgeService knowledgeService,
            IEmbeddingService embeddingService,
            IContextBuilderService contextBuilderService,
            IKnowledgeWorkspaceService workspaceService
    ) {
        this.knowledgeService = knowledgeService;
        this.embeddingService = embeddingService;
        this.contextBuilderService = contextBuilderService;
        this.workspaceService = workspaceService;
    }

    // ==================== 课程空间 ====================

    @GetMapping("/workspaces")
    public Result<List<KnowledgeWorkspaceResponse>> listWorkspaces(
            @RequestParam("userId") @NotNull(message = "用户ID不能为空") Long userId
    ) {
        return Result.success(workspaceService.listByUser(userId));
    }

    @PostMapping("/workspaces")
    public Result<KnowledgeWorkspaceResponse> createWorkspace(
            @RequestBody @Valid KnowledgeWorkspaceRequest request
    ) {
        return Result.success(workspaceService.create(request));
    }

    @PutMapping("/workspaces/{workspaceId}")
    public Result<KnowledgeWorkspaceResponse> updateWorkspace(
            @PathVariable Long workspaceId,
            @RequestBody @Valid KnowledgeWorkspaceRequest request
    ) {
        return Result.success(workspaceService.update(workspaceId, request));
    }

    @DeleteMapping("/workspaces/{workspaceId}")
    public Result<Void> deleteWorkspace(
            @PathVariable Long workspaceId,
            @RequestParam("userId") @NotNull(message = "用户ID不能为空") Long userId
    ) {
        workspaceService.delete(workspaceId, userId);
        return Result.successMessage("删除成功");
    }

    @PostMapping("/upload")
    public Result<KnowledgeFileResponse> uploadFile(
            @RequestParam("file") @NotNull(message = "文件不能为空") MultipartFile file,
            @RequestParam("userId") @NotNull(message = "用户ID不能为空") Long userId,
            @RequestParam(value = "workspaceId", required = false) Long workspaceId,
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "description", required = false) String description
    ) {
        log.info("收到文件上传请求: fileName={}, size={}, userId={}, workspaceId={}",
                file.getOriginalFilename(), file.getSize(), userId, workspaceId);

        workspaceService.verifyOwnership(workspaceId, userId);

        String fileName = file.getOriginalFilename();
        String fileType = fileName != null && fileName.contains(".")
                ? fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase()
                : "unknown";

        KnowledgeFileResponse result = knowledgeService.uploadFile(
                fileName, fileType, fileToBytes(file), userId, workspaceId, category, description
        );

        log.info("文件上传成功: fileId={}, fileName={}", result.getFileId(), result.getFileName());
        return Result.success(result);
    }

    @DeleteMapping("/files/{fileId}")
    public Result<Void> deleteFile(
            @PathVariable Long fileId,
            @RequestParam("userId") @NotNull(message = "用户ID不能为空") Long userId
    ) {
        log.info("收到删除文件请求: fileId={}, userId={}", fileId, userId);
        knowledgeService.deleteFile(fileId, userId);
        return Result.successMessage("删除成功");
    }

    @GetMapping("/files")
    public Result<PageResponse<KnowledgeFileResponse>> listFiles(
            @RequestParam(value = "page", defaultValue = "1") Integer page,
            @RequestParam(value = "size", defaultValue = "10") Integer size,
            @RequestParam("userId") @NotNull(message = "用户ID不能为空") Long userId,
            @RequestParam(value = "workspaceId", required = false) Long workspaceId,
            @RequestParam(value = "status", required = false) Integer status,
            @RequestParam(value = "fileType", required = false) String fileType,
            @RequestParam(value = "keyword", required = false) String keyword
    ) {
        KnowledgeFilePageRequest request = KnowledgeFilePageRequest.builder()
                .userId(userId)
                .workspaceId(workspaceId)
                .page(page)
                .size(size)
                .status(status)
                .fileType(fileType)
                .keyword(keyword)
                .build();
        return Result.success(knowledgeService.listFiles(request));
    }

    @GetMapping("/files/{fileId}")
    public Result<KnowledgeFileResponse> getFileById(
            @PathVariable Long fileId,
            @RequestParam("userId") @NotNull(message = "用户ID不能为空") Long userId
    ) {
        return Result.success(knowledgeService.getFileById(fileId, userId));
    }

    @GetMapping("/files/{fileId}/preview")
    public Result<KnowledgeFilePreviewResponse> getFilePreview(
            @PathVariable Long fileId,
            @RequestParam("userId") @NotNull(message = "用户ID不能为空") Long userId,
            @RequestParam(value = "chunkLimit", defaultValue = "6") Integer chunkLimit
    ) {
        return Result.success(knowledgeService.getFilePreview(fileId, userId, chunkLimit));
    }

    @GetMapping("/files/{fileId}/chunks")
    public Result<List<KnowledgeChunkPreviewResponse>> getFileChunks(
            @PathVariable Long fileId,
            @RequestParam("userId") @NotNull(message = "用户ID不能为空") Long userId,
            @RequestParam(value = "limit", defaultValue = "20") Integer limit
    ) {
        return Result.success(knowledgeService.getFileChunks(fileId, userId, limit));
    }

    @PostMapping("/files/{fileId}/reprocess")
    public Result<Void> reprocessFile(
            @PathVariable Long fileId,
            @RequestBody Map<String, Long> body
    ) {
        Long userId = body.get("userId");
        log.info("收到重新处理请求: fileId={}, userId={}", fileId, userId);
        knowledgeService.reprocessFile(fileId, userId);
        return Result.successMessage("已开始重新处理");
    }

    @PostMapping("/search")
    public Result<KnowledgeSearchResponse> search(
            @RequestBody @Valid KnowledgeSearchRequest request
    ) {
        log.info("收到检索请求: query={}, topK={}, userId={}",
                request.getQuery(), request.getTopK(), request.getUserId());
        return Result.success(knowledgeService.search(request));
    }

    @PostMapping("/search/test")
    public Result<KnowledgeSearchTestResponse> testSearch(
            @RequestBody @Valid KnowledgeSearchTestRequest request
    ) {
        log.info("收到检索测试请求: query={}, topK={}, userId={}",
                request.getQuery(), request.getTopK(), request.getUserId());
        return Result.success(knowledgeService.testSearch(request));
    }

    @GetMapping("/search/context")
    public Result<String> buildContext(
            @RequestParam("query") @NotNull(message = "检索关键词不能为空") String query,
            @RequestParam(value = "maxTokens", defaultValue = "4000") Integer maxTokens,
            @RequestParam("userId") @NotNull(message = "用户ID不能为空") Long userId,
            @RequestParam(value = "workspaceId", required = false) Long workspaceId
    ) {
        KnowledgeSearchResponse result = knowledgeService.search(
                KnowledgeSearchRequest.builder()
                        .query(query)
                        .topK(5)
                        .userId(userId)
                        .workspaceId(workspaceId)
                        .build()
        );
        String context = contextBuilderService.buildKnowledgeContext(result, maxTokens);
        return Result.success(context);
    }

    @GetMapping("/health/ollama")
    public Result<Boolean> checkOllamaHealth() {
        boolean available = embeddingService.isAvailable();
        return Result.<Boolean>builder()
                .code(available ? 200 : 503)
                .message(available ? "Embedding服务正常，模型: " + embeddingService.getModelName() : "Embedding服务不可用")
                .data(available)
                .timestamp(System.currentTimeMillis())
                .build();
    }

    private byte[] fileToBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new RuntimeException("文件读取失败", e);
        }
    }
}
