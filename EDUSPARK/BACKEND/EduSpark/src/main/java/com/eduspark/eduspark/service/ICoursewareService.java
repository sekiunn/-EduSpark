package com.eduspark.eduspark.service;

import com.eduspark.eduspark.dto.courseware.LessonPlanRequest;
import com.eduspark.eduspark.dto.courseware.LessonPlanResponse;
import com.eduspark.eduspark.dto.courseware.PptDeckPlan;
import com.eduspark.eduspark.dto.courseware.PptGenerateRequest;
import com.eduspark.eduspark.dto.courseware.PptGenerateResponse;
import com.eduspark.eduspark.dto.courseware.TemplateFillPlan;
import com.eduspark.eduspark.pojo.entity.PptTemplate;

import java.util.function.Consumer;

/**
 * Courseware generation service.
 */
public interface ICoursewareService {

    LessonPlanResponse generateLessonPlan(LessonPlanRequest request);

    LessonPlanResponse exportLessonPlanDocument(LessonPlanRequest request, String content);

    String streamLessonPlanContent(LessonPlanRequest request, Consumer<String> onChunk);

    String rewriteLessonPlanFragment(String documentContent, String selectedText, String instruction);

    PptGenerateResponse generatePpt(PptGenerateRequest request);

    String streamPptPlanning(PptGenerateRequest request, Consumer<String> onChunk);

    PptDeckPlan buildPptPlan(PptGenerateRequest request, String planningMarkdown);

    PptGenerateResponse renderPpt(PptGenerateRequest request, PptDeckPlan plan);

    String streamTemplateFillPlanning(PptGenerateRequest request, PptTemplate template, Consumer<String> onChunk);

    TemplateFillPlan buildTemplateFillPlan(String planningJson);

    PptGenerateResponse renderTemplatedPpt(PptGenerateRequest request, PptTemplate template, TemplateFillPlan fillPlan);

    /**
     * 新方案"上传即模板"——不需要管理员写 {{xxx}} 占位符。
     * 流程：提取 pptx 全部文本块 → LLM 按主题改写所有块 → POI 保样式 in-place 替换。
     * 保留原 pptx 的全部图片、版式、配色。
     * <p>预期由 PptWorkspaceServiceImpl 在模板有 pptx 但无 marker 时调用。
     */
    PptGenerateResponse renderFullReplacementPpt(PptGenerateRequest request, PptTemplate template);

    String generateInteractiveContent(String topic, int count, String type);

    String getDownloadUrl(String filePath, String format);

    boolean isHealthy();
}
