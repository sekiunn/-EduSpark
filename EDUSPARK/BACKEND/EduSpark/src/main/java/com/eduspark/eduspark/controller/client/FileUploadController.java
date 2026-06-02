package com.eduspark.eduspark.controller.client;

import com.eduspark.eduspark.dto.common.Result;
import com.eduspark.eduspark.service.IFileStorageService;
import com.eduspark.eduspark.service.IKnowledgeService;
import com.eduspark.eduspark.dto.knowledge.KnowledgeFileResponse;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文件上传控制器（客户端）
 */
@Slf4j
@Validated
@RestController
@RequestMapping("/v1/upload")
public class FileUploadController {

    private final IKnowledgeService knowledgeService;
    private final IFileStorageService fileStorageService;

    public FileUploadController(
            IKnowledgeService knowledgeService,
            IFileStorageService fileStorageService
    ) {
        this.knowledgeService = knowledgeService;
        this.fileStorageService = fileStorageService;
    }

    /**
     * 上传文件（作为知识库）
     */
    @PostMapping("/file")
    public Result<KnowledgeFileResponse> uploadFile(
            @RequestParam("file") @NotNull(message = "文件不能为空") MultipartFile file,
            @RequestParam("userId") @NotNull(message = "用户ID不能为空") Long userId,
            @RequestParam(value = "workspaceId", required = false) Long workspaceId,
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "description", required = false) String description
    ) {
        log.info("收到文件上传请求: fileName={}, size={}, userId={}, workspaceId={}",
                file.getOriginalFilename(), file.getSize(), userId, workspaceId);

        String fileName = file.getOriginalFilename();
        String fileType = fileName != null && fileName.contains(".")
                ? fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase()
                : "unknown";

        try {
            byte[] bytes = file.getBytes();
            KnowledgeFileResponse result = knowledgeService.uploadFile(
                    fileName, fileType, bytes,
                    userId, workspaceId, category, description
            );

            log.info("文件上传成功: fileId={}, fileName={}", result.getFileId(), result.getFileName());
            return Result.success(result);

        } catch (Exception e) {
            log.error("文件上传失败: fileName={}, error={}", fileName, e.getMessage());
            return Result.fail("文件上传失败: " + e.getMessage());
        }
    }

    /**
     * 通用文件上传（不加入知识库，仅存储）
     */
    @PostMapping("/common")
    public Result<String> uploadCommonFile(
            @RequestParam("file") @NotNull(message = "文件不能为空") MultipartFile file,
            @RequestParam("userId") @NotNull(message = "用户ID不能为空") Long userId
    ) {
        log.info("收到通用文件上传请求: fileName={}, size={}, userId={}",
                file.getOriginalFilename(), file.getSize(), userId);

        try {
            byte[] bytes = file.getBytes();
            String filePath = fileStorageService.upload(
                    file.getOriginalFilename(), bytes, userId
            );
            String fileUrl = fileStorageService.getUrl(filePath);

            log.info("通用文件上传成功: url={}", fileUrl);
            return Result.success(fileUrl);

        } catch (Exception e) {
            log.error("文件上传失败: fileName={}, error={}", file.getOriginalFilename(), e.getMessage());
            return Result.fail("文件上传失败: " + e.getMessage());
        }
    }

    /**
     * 获取文件访问URL
     */
    @GetMapping("/url")
    public Result<String> getFileUrl(@RequestParam("fileId") Long fileId) {
        try {
            String url = knowledgeService.getFileUrl(fileId);
            return Result.success(url);
        } catch (Exception e) {
            log.error("获取文件URL失败: fileId={}", fileId, e);
            return Result.fail("获取文件URL失败");
        }
    }

    /**
     * 文件存储服务健康检查
     */
    @GetMapping("/health")
    public Result<Boolean> checkStorageHealth() {
        boolean available = fileStorageService.isAvailable();
        return Result.<Boolean>builder()
                .code(available ? 200 : 503)
                .message(available ? "文件存储服务正常" : "文件存储服务不可用")
                .data(available)
                .build();
    }
}
