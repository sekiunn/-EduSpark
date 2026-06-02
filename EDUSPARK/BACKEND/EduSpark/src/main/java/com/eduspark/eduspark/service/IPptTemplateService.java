package com.eduspark.eduspark.service;

import com.eduspark.eduspark.dto.common.PageResponse;
import com.eduspark.eduspark.dto.ppt.PptTemplateAdminResponse;
import com.eduspark.eduspark.dto.ppt.PptTemplateClientResponse;
import com.eduspark.eduspark.dto.ppt.PptTemplatePreParseResponse;
import com.eduspark.eduspark.dto.ppt.PptTemplateSceneRequest;
import com.eduspark.eduspark.dto.ppt.PptTemplateSceneResponse;
import com.eduspark.eduspark.dto.ppt.PptTemplateStyleRequest;
import com.eduspark.eduspark.dto.ppt.PptTemplateStyleResponse;
import com.eduspark.eduspark.dto.ppt.PptTemplateUpsertRequest;
import com.eduspark.eduspark.pojo.entity.PptTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface IPptTemplateService {

    List<PptTemplateSceneResponse> listAdminScenes(Boolean enabled);

    PptTemplateSceneResponse createScene(PptTemplateSceneRequest request);

    PptTemplateSceneResponse updateScene(Long id, PptTemplateSceneRequest request);

    void deleteScene(Long id);

    List<PptTemplateStyleResponse> listAdminStyles(Long sceneId, Boolean enabled);

    PptTemplateStyleResponse createStyle(PptTemplateStyleRequest request);

    PptTemplateStyleResponse updateStyle(Long id, PptTemplateStyleRequest request);

    void deleteStyle(Long id);

    PageResponse<PptTemplateAdminResponse> listAdminTemplates(
            Long sceneId,
            Long styleId,
            Boolean enabled,
            String keyword,
            Integer page,
            Integer size
    );

    PptTemplateAdminResponse getAdminTemplate(Long id);

    PptTemplateAdminResponse createTemplate(PptTemplateUpsertRequest request);

    PptTemplateAdminResponse updateTemplate(Long id, PptTemplateUpsertRequest request);

    PptTemplateAdminResponse toggleTemplate(Long id);

    void deleteTemplate(Long id);

    String uploadTemplateAsset(MultipartFile file, Long userId);

    PptTemplateAdminResponse uploadTemplateFile(Long templateId, MultipartFile file);

    /**
     * 把 pptx 临时存入 _pending/ 并解析标记。返回结果包含 pendingFileKey，
     * 前端在 createTemplate / updateTemplate 提交时一并传入即可把临时文件
     * 移到正式位置并落入模板。
     */
    PptTemplatePreParseResponse preParseTemplate(MultipartFile file);

    List<PptTemplateSceneResponse> listClientScenes();

    List<PptTemplateStyleResponse> listClientStyles(Long sceneId);

    List<PptTemplateClientResponse> listClientTemplates(Long sceneId, Long styleId, String keyword);

    PptTemplateClientResponse getClientTemplate(Long id);

    PptTemplate resolveTemplateReference(String templateRef);
}
