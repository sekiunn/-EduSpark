package com.eduspark.eduspark.controller.client;

import com.eduspark.eduspark.dto.common.Result;
import com.eduspark.eduspark.dto.ppt.PptTemplateClientResponse;
import com.eduspark.eduspark.dto.ppt.PptTemplateSceneResponse;
import com.eduspark.eduspark.dto.ppt.PptTemplateStyleResponse;
import com.eduspark.eduspark.service.IPptTemplateService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@Validated
@RestController
@RequestMapping("/v1/ppt")
public class PptTemplateController {

    private final IPptTemplateService pptTemplateService;

    public PptTemplateController(IPptTemplateService pptTemplateService) {
        this.pptTemplateService = pptTemplateService;
    }

    @GetMapping("/scenes")
    public Result<List<PptTemplateSceneResponse>> listScenes() {
        return Result.success(pptTemplateService.listClientScenes());
    }

    @GetMapping("/styles")
    public Result<List<PptTemplateStyleResponse>> listStyles(
            @RequestParam(value = "sceneId", required = false) Long sceneId
    ) {
        return Result.success(pptTemplateService.listClientStyles(sceneId));
    }

    @GetMapping("/templates")
    public Result<List<PptTemplateClientResponse>> listTemplates(
            @RequestParam(value = "sceneId", required = false) Long sceneId,
            @RequestParam(value = "styleId", required = false) Long styleId,
            @RequestParam(value = "keyword", required = false) String keyword
    ) {
        return Result.success(pptTemplateService.listClientTemplates(sceneId, styleId, keyword));
    }

    @GetMapping("/templates/{id}")
    public Result<PptTemplateClientResponse> getTemplate(@PathVariable Long id) {
        return Result.success(pptTemplateService.getClientTemplate(id));
    }
}
