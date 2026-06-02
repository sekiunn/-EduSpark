package com.eduspark.eduspark.controller.admin;

import com.eduspark.eduspark.dto.common.PageResponse;
import com.eduspark.eduspark.dto.common.Result;
import com.eduspark.eduspark.dto.ppt.PptTemplateAdminResponse;
import com.eduspark.eduspark.dto.ppt.PptTemplatePreParseResponse;
import com.eduspark.eduspark.dto.ppt.PptTemplateSceneRequest;
import com.eduspark.eduspark.dto.ppt.PptTemplateSceneResponse;
import com.eduspark.eduspark.dto.ppt.PptTemplateStyleRequest;
import com.eduspark.eduspark.dto.ppt.PptTemplateStyleResponse;
import com.eduspark.eduspark.dto.ppt.PptTemplateUpsertRequest;
import com.eduspark.eduspark.service.IPptTemplateService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Slf4j
@Validated
@RestController
@RequestMapping("/admin/ppt")
public class PptTemplateAdminController {

    private final IPptTemplateService pptTemplateService;

    public PptTemplateAdminController(IPptTemplateService pptTemplateService) {
        this.pptTemplateService = pptTemplateService;
    }

    @GetMapping("/scenes")
    public Result<List<PptTemplateSceneResponse>> listScenes(
            @RequestParam(value = "enabled", required = false) Boolean enabled
    ) {
        return Result.success(pptTemplateService.listAdminScenes(enabled));
    }

    @PostMapping("/scenes")
    public Result<PptTemplateSceneResponse> createScene(@RequestBody @Valid PptTemplateSceneRequest request) {
        return Result.success(pptTemplateService.createScene(request));
    }

    @PutMapping("/scenes/{id}")
    public Result<PptTemplateSceneResponse> updateScene(@PathVariable Long id,
                                                        @RequestBody @Valid PptTemplateSceneRequest request) {
        return Result.success(pptTemplateService.updateScene(id, request));
    }

    @DeleteMapping("/scenes/{id}")
    public Result<Void> deleteScene(@PathVariable Long id) {
        pptTemplateService.deleteScene(id);
        return Result.success();
    }

    @GetMapping("/styles")
    public Result<List<PptTemplateStyleResponse>> listStyles(
            @RequestParam(value = "sceneId", required = false) Long sceneId,
            @RequestParam(value = "enabled", required = false) Boolean enabled
    ) {
        return Result.success(pptTemplateService.listAdminStyles(sceneId, enabled));
    }

    @PostMapping("/styles")
    public Result<PptTemplateStyleResponse> createStyle(@RequestBody @Valid PptTemplateStyleRequest request) {
        return Result.success(pptTemplateService.createStyle(request));
    }

    @PutMapping("/styles/{id}")
    public Result<PptTemplateStyleResponse> updateStyle(@PathVariable Long id,
                                                        @RequestBody @Valid PptTemplateStyleRequest request) {
        return Result.success(pptTemplateService.updateStyle(id, request));
    }

    @DeleteMapping("/styles/{id}")
    public Result<Void> deleteStyle(@PathVariable Long id) {
        pptTemplateService.deleteStyle(id);
        return Result.success();
    }

    @GetMapping("/templates")
    public Result<PageResponse<PptTemplateAdminResponse>> listTemplates(
            @RequestParam(value = "sceneId", required = false) Long sceneId,
            @RequestParam(value = "styleId", required = false) Long styleId,
            @RequestParam(value = "enabled", required = false) Boolean enabled,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "page", required = false, defaultValue = "1") Integer page,
            @RequestParam(value = "size", required = false, defaultValue = "10") Integer size
    ) {
        return Result.success(pptTemplateService.listAdminTemplates(sceneId, styleId, enabled, keyword, page, size));
    }

    @GetMapping("/templates/{id}")
    public Result<PptTemplateAdminResponse> getTemplate(@PathVariable Long id) {
        return Result.success(pptTemplateService.getAdminTemplate(id));
    }

    @PostMapping("/templates")
    public Result<PptTemplateAdminResponse> createTemplate(@RequestBody @Valid PptTemplateUpsertRequest request) {
        return Result.success(pptTemplateService.createTemplate(request));
    }

    @PutMapping("/templates/{id}")
    public Result<PptTemplateAdminResponse> updateTemplate(@PathVariable Long id,
                                                           @RequestBody @Valid PptTemplateUpsertRequest request) {
        return Result.success(pptTemplateService.updateTemplate(id, request));
    }

    @PatchMapping("/templates/{id}/toggle")
    public Result<PptTemplateAdminResponse> toggleTemplate(@PathVariable Long id) {
        return Result.success(pptTemplateService.toggleTemplate(id));
    }

    @DeleteMapping("/templates/{id}")
    public Result<Void> deleteTemplate(@PathVariable Long id) {
        pptTemplateService.deleteTemplate(id);
        return Result.success();
    }

    @PostMapping("/templates/assets")
    public Result<String> uploadTemplateAsset(@RequestPart("file") MultipartFile file,
                                              HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        log.info("Upload ppt template asset: fileName={}, size={}, userId={}",
                file.getOriginalFilename(), file.getSize(), userId);
        return Result.success(pptTemplateService.uploadTemplateAsset(file, userId));
    }

    @PostMapping("/templates/{id}/file")
    public Result<PptTemplateAdminResponse> uploadTemplateFile(@PathVariable Long id,
                                                                @RequestPart("file") MultipartFile file) {
        log.info("Upload ppt template file: templateId={}, fileName={}, size={}",
                id, file.getOriginalFilename(), file.getSize());
        return Result.success(pptTemplateService.uploadTemplateFile(id, file));
    }

    /**
     * 新增模板对话框：先把 pptx 上传到 _pending/ 并解析标记结构。
     * 返回 pendingFileKey + 解析摘要，前端在最终 createTemplate / updateTemplate
     * 时再回传 pendingFileKey，让"创建模板 + 上传 pptx"在同一次对话里完成。
     */
    @PostMapping("/templates/pre-parse")
    public Result<PptTemplatePreParseResponse> preParseTemplate(@RequestPart("file") MultipartFile file) {
        log.info("Pre-parse ppt template file: fileName={}, size={}",
                file.getOriginalFilename(), file.getSize());
        return Result.success(pptTemplateService.preParseTemplate(file));
    }
}
