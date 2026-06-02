package com.eduspark.eduspark.controller.client;

import com.eduspark.eduspark.dto.common.Result;
import com.eduspark.eduspark.dto.courseware.LessonPlanRequest;
import com.eduspark.eduspark.dto.courseware.LessonPlanResponse;
import com.eduspark.eduspark.dto.courseware.PptGenerateRequest;
import com.eduspark.eduspark.dto.courseware.PptGenerateResponse;
import com.eduspark.eduspark.service.ICoursewareService;
import com.eduspark.eduspark.service.IFileStorageService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Courseware endpoints for the client application.
 */
@Slf4j
@Validated
@RestController
@RequestMapping("/v1/courseware")
public class CoursewareController {

    private final ICoursewareService coursewareService;
    private final IFileStorageService fileStorageService;

    @Value("${courseware.storage.local-path:./data/courseware}")
    private String localStoragePath;

    public CoursewareController(ICoursewareService coursewareService,
                                IFileStorageService fileStorageService) {
        this.coursewareService = coursewareService;
        this.fileStorageService = fileStorageService;
    }

    @PostMapping("/lesson-plan")
    public Result<LessonPlanResponse> generateLessonPlan(@RequestBody @Validated LessonPlanRequest request) {
        log.info("Received lesson-plan generation request: subject={}, grade={}", request.getSubject(), request.getGrade());

        LessonPlanResponse response = coursewareService.generateLessonPlan(request);
        if (response.isSuccess()) {
            log.info("Lesson-plan generation succeeded: filePath={}", response.getFilePath());
            return Result.success(response);
        }

        log.error("Lesson-plan generation failed: error={}", response.getError());
        return Result.fail(response.getError());
    }

    // ===== 暂时关闭（PPT 链路收口）=====
    // PPT 统一改走「工作区」入口：聊天确认卡 → PptWorkspaceServiceImpl → renderFullReplacementPpt（全替换）。
    // 这个旧的直接生成 HTTP 端点（前端已不调用）暂时停用，避免出现第二条 PPT 生成链路。
    // 如需恢复，取消下方注释即可（coursewareService.generatePpt 实现仍保留）。
    // @PostMapping("/ppt")
    // public Result<PptGenerateResponse> generatePpt(@RequestBody @Validated PptGenerateRequest request) {
    //     log.info("Received ppt generation request: title={}, slideCount={}", request.getTitle(), request.getSlideCount());
    //
    //     PptGenerateResponse response = coursewareService.generatePpt(request);
    //     if (response.isSuccess()) {
    //         log.info("Ppt generation succeeded: filePath={}", response.getFilePath());
    //         return Result.success(response);
    //     }
    //
    //     log.error("Ppt generation failed: error={}", response.getError());
    //     return Result.fail(response.getError());
    // }
    // ===== 暂时关闭 结束 =====

    @GetMapping("/interactive")
    public Result<String> generateInteractiveContent(@RequestParam("topic") String topic,
                                                     @RequestParam(value = "count", defaultValue = "5") int count,
                                                     @RequestParam(value = "type", defaultValue = "choice") String type) {
        log.info("Received interactive-content generation request: topic={}, count={}, type={}", topic, count, type);
        return Result.success(coursewareService.generateInteractiveContent(topic, count, type));
    }

    @GetMapping("/download")
    public ResponseEntity<InputStreamResource> downloadFile(@RequestParam("path") String filePath,
                                                            HttpServletResponse response) throws IOException {
        log.info("Received file download request: path={}", filePath);
        if (isBlockedLocalPath(filePath)) {
            log.warn("Blocked unsafe local download path: path={}", filePath);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        InputStream inputStream;
        String fileName;

        Path localPath = resolveExistingLocalPath(filePath);
        if (localPath != null) {
            inputStream = Files.newInputStream(localPath);
            fileName = localPath.getFileName().toString();
        } else {
            try {
                inputStream = fileStorageService.getInputStream(filePath);
                fileName = extractFileName(filePath);
            } catch (Exception e) {
                log.warn("Storage lookup failed, retrying as local file: path={}", filePath);
                Path fallbackPath = resolveExistingLocalPath(filePath);
                if (fallbackPath == null) {
                    log.error("Download target was not found: path={}", filePath);
                    return ResponseEntity.notFound().build();
                }
                inputStream = Files.newInputStream(fallbackPath);
                fileName = fallbackPath.getFileName().toString();
            }
        }

        String contentType = getContentType(fileName);
        String contentDisposition = ContentDisposition.attachment()
                .filename(fileName, StandardCharsets.UTF_8)
                .build()
                .toString();

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition)
                .contentType(MediaType.parseMediaType(contentType))
                .body(new InputStreamResource(inputStream));
    }

    @GetMapping("/health")
    public Result<Boolean> healthCheck() {
        boolean healthy = coursewareService.isHealthy();
        return Result.<Boolean>builder()
                .code(healthy ? 200 : 503)
                .message(healthy ? "课件服务正常" : "课件服务不可用")
                .data(healthy)
                .timestamp(System.currentTimeMillis())
                .build();
    }

    private String getContentType(String fileName) {
        if (fileName == null) {
            return MediaType.APPLICATION_OCTET_STREAM_VALUE;
        }

        String lower = fileName.toLowerCase();
        if (lower.endsWith(".html") || lower.endsWith(".htm")) {
            return "text/html;charset=UTF-8";
        } else if (lower.endsWith(".docx")) {
            return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        } else if (lower.endsWith(".doc")) {
            return "application/msword";
        } else if (lower.endsWith(".pptx")) {
            return "application/vnd.openxmlformats-officedocument.presentationml.presentation";
        } else if (lower.endsWith(".ppt")) {
            return "application/vnd.ms-powerpoint";
        } else if (lower.endsWith(".pdf")) {
            return "application/pdf";
        } else if (lower.endsWith(".zip")) {
            return "application/zip";
        }
        return MediaType.APPLICATION_OCTET_STREAM_VALUE;
    }

    private Path resolveExistingLocalPath(String filePath) {
        if (filePath == null || filePath.isBlank()) {
            return null;
        }

        try {
            Path resolvedPath = resolveAgainstLocalStorage(filePath);
            if (resolvedPath != null && Files.exists(resolvedPath)) {
                return resolvedPath;
            }
        } catch (InvalidPathException e) {
            log.warn("Invalid file path syntax: path={}", filePath);
        }

        return null;
    }

    private boolean isBlockedLocalPath(String filePath) {
        if (filePath == null || filePath.isBlank()) {
            return false;
        }

        try {
            Path candidate = Paths.get(filePath);
            if (!candidate.isAbsolute() && !filePath.contains("..")) {
                return false;
            }
            return resolveAgainstLocalStorage(filePath) == null;
        } catch (InvalidPathException e) {
            return true;
        }
    }

    private Path resolveAgainstLocalStorage(String filePath) {
        Path storageRoot = Paths.get(localStoragePath).toAbsolutePath().normalize();
        Path candidate = Paths.get(filePath);
        Path resolvedPath = candidate.isAbsolute()
                ? candidate.toAbsolutePath().normalize()
                : storageRoot.resolve(candidate).normalize();
        return resolvedPath.startsWith(storageRoot) ? resolvedPath : null;
    }

    private String extractFileName(String filePath) {
        if (filePath == null || filePath.isBlank()) {
            return "download";
        }

        int slashIndex = Math.max(filePath.lastIndexOf('/'), filePath.lastIndexOf('\\'));
        return slashIndex >= 0 ? filePath.substring(slashIndex + 1) : filePath;
    }
}
