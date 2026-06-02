package com.eduspark.eduspark.service.impl;

import com.eduspark.eduspark.dto.interactive.InteractiveContext;
import com.eduspark.eduspark.dto.interactive.InteractiveTemplatePlan;
import com.eduspark.eduspark.service.ILLMService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@Slf4j
@Service
public class InteractiveTemplateGenerationService {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };
    private static final Pattern IDENTIFIER_PATTERN = Pattern.compile("\\b[A-Za-z_][A-Za-z0-9_]*\\b");

    private final ILLMService llmService;
    private final ObjectMapper objectMapper;
    private final InteractiveTemplateRendererService rendererService;

    @Value("${interactive.template.enabled:true}")
    private boolean templateEnabled;

    @Value("${interactive.template.planner-model:${lesson-plan.writer.external.model:qwen3.6-plus}}")
    private String plannerModel;

    public InteractiveTemplateGenerationService(ILLMService llmService,
                                                ObjectMapper objectMapper,
                                                InteractiveTemplateRendererService rendererService) {
        this.llmService = llmService;
        this.objectMapper = objectMapper;
        this.rendererService = rendererService;
    }

    public String tryGenerateInitialHtml(InteractiveContext context, java.util.function.Consumer<String> onChunk) {
        if (!templateEnabled) {
            return null;
        }
        InteractiveTemplatePlan plan = buildTemplatePlan(context, null, false);
        if (plan == null) {
            return null;
        }
        applyPlan(context, plan);
        String html = rendererService.render(plan, context);
        emitInChunks(html, onChunk);
        return html;
    }

    public String tryGenerateRefinedHtml(InteractiveContext context,
                                         String currentHtml,
                                         String instruction,
                                         java.util.function.Consumer<String> onChunk) {
        if (!templateEnabled) {
            return null;
        }
        InteractiveTemplatePlan plan = buildTemplatePlan(context, instruction, true);
        if (plan == null) {
            return null;
        }
        applyPlan(context, plan);
        String html = rendererService.render(plan, context);
        emitInChunks(html, onChunk);
        return html;
    }

    private InteractiveTemplatePlan buildTemplatePlan(InteractiveContext context, String instruction, boolean refinement) {
        String templateId = resolveTemplateId(context, instruction, refinement);
        if (!InteractiveTemplateCatalog.SUPPORTED_TEMPLATE_IDS.contains(templateId)) {
            return null;
        }
        Map<String, Object> existingSpec = context == null ? Map.of() : context.safeTemplateSpec();
        Map<String, Object> spec = normalizeSpec(
                templateId,
                extractSpec(templateId, context, instruction, existingSpec),
                context
        );
        return InteractiveTemplatePlan.builder()
                .renderMode(InteractiveTemplateCatalog.RENDER_MODE_TEMPLATE)
                .interactiveType(resolveInteractiveType(templateId))
                .templateId(templateId)
                .templateVersion(InteractiveTemplateCatalog.TEMPLATE_VERSION_V1)
                .title(resolveTitle(spec, context))
                .spec(spec)
                .build();
    }

    private void applyPlan(InteractiveContext context, InteractiveTemplatePlan plan) {
        if (context == null || plan == null) {
            return;
        }
        context.setRenderMode(plan.getRenderMode());
        context.setInteractiveType(plan.getInteractiveType());
        context.setTemplateId(plan.getTemplateId());
        context.setTemplateVersion(plan.getTemplateVersion());
        context.setTemplateSpec(plan.getSpec());
    }

    private void emitInChunks(String html, java.util.function.Consumer<String> onChunk) {
        if (html == null || onChunk == null) {
            return;
        }
        int chunkSize = 360;
        for (int index = 0; index < html.length(); index += chunkSize) {
            onChunk.accept(html.substring(index, Math.min(html.length(), index + chunkSize)));
        }
    }

    private String resolveTemplateId(InteractiveContext context, String instruction, boolean preferExistingTemplate) {
        if (preferExistingTemplate && context != null && hasText(context.getTemplateId())
                && InteractiveTemplateCatalog.SUPPORTED_TEMPLATE_IDS.contains(context.getTemplateId())) {
            return context.getTemplateId();
        }
        String text = buildDetectionText(context, instruction).toLowerCase(Locale.ROOT);
        if (!hasText(text)) {
            return null;
        }
        if (containsAny(text, "动量守恒", "碰撞", "弹性碰撞", "非弹性碰撞", "collision", "momentum")) {
            return InteractiveTemplateCatalog.TEMPLATE_PHYSICS_COLLISION_V1;
        }
        if (containsAny(text, "拖拽", "配对", "连线", "排序", "drag", "match")) {
            return InteractiveTemplateCatalog.TEMPLATE_DRAG_MATCH_V1;
        }
        if (containsAny(text, "热点", "点击探索", "点击查看", "结构图", "器官", "地图", "hotspot", "explore")) {
            return InteractiveTemplateCatalog.TEMPLATE_HOTSPOT_EXPLORE_V1;
        }
        if (containsAny(text, "练习题", "选择题", "判断题", "填空题", "测验", "答题", "题目", "quiz", "practice")) {
            return InteractiveTemplateCatalog.TEMPLATE_QUIZ_PRACTICE_V1;
        }
        if (containsAny(text, "参数", "滑杆", "函数", "曲线", "图像", "坐标", "公式", "parameter", "slider")) {
            return InteractiveTemplateCatalog.TEMPLATE_PARAMETER_EXPLORER_V1;
        }
        if (looksLikeFlowDemo(text)) {
            return InteractiveTemplateCatalog.TEMPLATE_FLOW_DEMO_V1;
        }
        if (looksLikePresentationDemo(text)) {
            return InteractiveTemplateCatalog.TEMPLATE_PPT_DEMO_V1;
        }
        if (containsAny(text, "动画", "演示", "过程", "步骤", "模拟", "animation", "demo", "step")) {
            return InteractiveTemplateCatalog.TEMPLATE_ANIMATION_STEPPER_V1;
        }
        return null;
    }

    private boolean looksLikeFlowDemo(String text) {
        return containsAny(text,
                "流程", "流程图", "循环", "阶段", "步骤", "时序", "机制", "过程", "演变", "变化过程", "生命周期",
                "cycle", "flow", "timeline", "sequence");
    }

    private boolean looksLikePresentationDemo(String text) {
        if (looksLikeFlowDemo(text)) {
            return false;
        }
        return containsAny(text,
                "讲解", "概念", "介绍", "认识", "原理", "对比", "结构", "概览", "专题", "总结", "课件", "幻灯", "ppt",
                "presentation", "slides");
    }

    private String resolveInteractiveType(String templateId) {
        return switch (templateId) {
            case InteractiveTemplateCatalog.TEMPLATE_PHYSICS_COLLISION_V1,
                    InteractiveTemplateCatalog.TEMPLATE_PPT_DEMO_V1,
                    InteractiveTemplateCatalog.TEMPLATE_FLOW_DEMO_V1,
                    InteractiveTemplateCatalog.TEMPLATE_ANIMATION_STEPPER_V1 -> InteractiveTemplateCatalog.TYPE_ANIMATION_DEMO;
            case InteractiveTemplateCatalog.TEMPLATE_PARAMETER_EXPLORER_V1 -> InteractiveTemplateCatalog.TYPE_PARAMETER_SIMULATION;
            case InteractiveTemplateCatalog.TEMPLATE_QUIZ_PRACTICE_V1 -> InteractiveTemplateCatalog.TYPE_QUIZ_PRACTICE;
            case InteractiveTemplateCatalog.TEMPLATE_DRAG_MATCH_V1 -> InteractiveTemplateCatalog.TYPE_DRAG_MATCH;
            case InteractiveTemplateCatalog.TEMPLATE_HOTSPOT_EXPLORE_V1 -> InteractiveTemplateCatalog.TYPE_HOTSPOT_EXPLORE;
            default -> null;
        };
    }

    private String resolveTitle(Map<String, Object> spec, InteractiveContext context) {
        String title = normalizeText(spec.get("title"));
        if (title != null) {
            return title;
        }
        if (context != null && hasText(context.getTopic())) {
            return context.getTopic();
        }
        return "互动页面";
    }

    private Map<String, Object> extractSpec(String templateId,
                                            InteractiveContext context,
                                            String instruction,
                                            Map<String, Object> existingSpec) {
        Map<String, Object> merged = new LinkedHashMap<>(existingSpec == null ? Map.of() : existingSpec);
        try {
            String prompt = buildSpecPrompt(templateId, context, instruction, existingSpec);
            String raw = llmService.chatWithModel(List.of(new ILLMService.ChatMessage("user", prompt)), plannerModel);
            merged.putAll(parseJsonMap(raw));
        } catch (Exception e) {
            log.warn("Extract interactive template spec failed for {}", templateId, e);
        }
        return merged;
    }

    private String buildSpecPrompt(String templateId,
                                   InteractiveContext context,
                                   String instruction,
                                   Map<String, Object> existingSpec) {
        String currentContext = toJson(context == null ? Map.of() : context.toMap());
        String currentSpec = toJson(existingSpec == null ? Map.of() : existingSpec);
        String requestBlock = hasText(instruction)
                ? "User refinement request:\n" + instruction.trim() + "\n"
                : "This is an initial generation request.\n";

        return switch (templateId) {
            case InteractiveTemplateCatalog.TEMPLATE_PHYSICS_COLLISION_V1 -> buildPlannerPrompt(
                    templateId, currentContext, currentSpec, requestBlock,
                    """
                    {
                      "title": null,
                      "subtitle": null,
                      "massA": 2,
                      "massB": 1,
                      "velocityA": 4,
                      "velocityB": 0,
                      "defaultCollisionType": "elastic",
                      "showKineticEnergy": true,
                      "showFormula": true
                    }
                    """,
                    """
                    1. Keep the topic focused on one-dimensional momentum conservation.
                    2. defaultCollisionType must be elastic or inelastic.
                    3. Prefer concise Chinese title and subtitle text.
                    """);
            case InteractiveTemplateCatalog.TEMPLATE_PPT_DEMO_V1 -> buildPlannerPrompt(
                    templateId, currentContext, currentSpec, requestBlock,
                    """
                    {
                      "title": null,
                      "subtitle": null,
                      "intro": null,
                      "slides": [{
                        "tag": null,
                        "title": null,
                        "summary": null,
                        "bullets": ["要点 1", "要点 2"],
                        "highlight": null
                      }]
                    }
                    """,
                    """
                    1. Return 3 to 5 slides.
                    2. Each slide should contain 2 to 4 concise bullets in Chinese.
                    3. Use this for concept explanation, overview, comparison, or lecture-style demo pages.
                    """);
            case InteractiveTemplateCatalog.TEMPLATE_FLOW_DEMO_V1 -> buildPlannerPrompt(
                    templateId, currentContext, currentSpec, requestBlock,
                    """
                    {
                      "title": null,
                      "subtitle": null,
                      "overview": null,
                      "steps": [{
                        "title": null,
                        "description": null,
                        "keyword": null
                      }]
                    }
                    """,
                    """
                    1. Return 4 to 6 steps.
                    2. Use this for cycles, processes, mechanisms, stages, or sequence explanations.
                    3. keyword should be very short and suitable for node labels.
                    """);
            case InteractiveTemplateCatalog.TEMPLATE_ANIMATION_STEPPER_V1 -> buildPlannerPrompt(
                    templateId, currentContext, currentSpec, requestBlock,
                    """
                    {
                      "title": null,
                      "subtitle": null,
                      "intro": null,
                      "steps": [{ "title": null, "text": null }]
                    }
                    """,
                    """
                    1. Return 3 to 6 steps.
                    2. Every step should be clear, concise, and written in Chinese.
                    3. Use this for process explanations or guided walkthroughs.
                    """);
            case InteractiveTemplateCatalog.TEMPLATE_PARAMETER_EXPLORER_V1 -> buildPlannerPrompt(
                    templateId, currentContext, currentSpec, requestBlock,
                    """
                    {
                      "title": null,
                      "subtitle": null,
                      "formulaText": null,
                      "parameters": [{ "key": "a", "label": "参数 a", "min": -5, "max": 5, "step": 0.5, "defaultValue": 1, "unit": "" }],
                      "metrics": [{ "label": "结果", "expression": "a * x", "unit": "", "precision": 2 }],
                      "chart": { "xKey": "x", "yExpression": "a * x", "yLabel": "结果值" }
                    }
                    """,
                    """
                    1. Return 2 to 4 parameters.
                    2. Expressions must be simple JavaScript math expressions using parameter keys and Math.xxx only.
                    3. Prefer Chinese labels.
                    """);
            case InteractiveTemplateCatalog.TEMPLATE_QUIZ_PRACTICE_V1 -> buildPlannerPrompt(
                    templateId, currentContext, currentSpec, requestBlock,
                    """
                    {
                      "title": null,
                      "subtitle": null,
                      "questions": [{ "type": "single_choice", "prompt": null, "options": ["A", "B", "C", "D"], "answer": null, "explanation": null }]
                    }
                    """,
                    """
                    1. Return 3 to 5 questions.
                    2. type must be single_choice, true_false, or short_text.
                    3. Every question must include an explanation.
                    """);
            case InteractiveTemplateCatalog.TEMPLATE_DRAG_MATCH_V1 -> buildPlannerPrompt(
                    templateId, currentContext, currentSpec, requestBlock,
                    """
                    {
                      "title": null,
                      "subtitle": null,
                      "leftItems": [{ "id": "l1", "label": null }],
                      "rightItems": [{ "id": "r1", "label": null }],
                      "pairs": [{ "leftId": "l1", "rightId": "r1" }]
                    }
                    """,
                    """
                    1. Keep 3 to 6 pairs.
                    2. Labels should be short and suitable for drag-and-drop cards.
                    3. pairs must only reference IDs from leftItems and rightItems.
                    """);
            case InteractiveTemplateCatalog.TEMPLATE_HOTSPOT_EXPLORE_V1 -> buildPlannerPrompt(
                    templateId, currentContext, currentSpec, requestBlock,
                    """
                    {
                      "title": null,
                      "subtitle": null,
                      "boardTitle": null,
                      "hotspots": [{ "id": "h1", "label": null, "x": 20, "y": 30, "content": null }]
                    }
                    """,
                    """
                    1. Return 3 to 6 hotspots.
                    2. x and y should be integers between 10 and 90.
                    3. Content should be concise Chinese text.
                    """);
            default -> "{}";
        };
    }

    private String buildPlannerPrompt(String templateId,
                                      String currentContext,
                                      String currentSpec,
                                      String requestBlock,
                                      String schema,
                                      String rules) {
        return """
                You generate a JSON spec for the interactive template %s.
                Return JSON only. Do not include markdown or explanations.
                Prefer Chinese display text.

                Current context JSON:
                %s

                Current spec JSON:
                %s

                %s
                Target JSON shape:
                %s

                Rules:
                %s
                """.formatted(templateId, currentContext, currentSpec, requestBlock, schema, rules);
    }

    private Map<String, Object> normalizeSpec(String templateId, Map<String, Object> raw, InteractiveContext context) {
        Map<String, Object> spec = raw == null ? new LinkedHashMap<>() : new LinkedHashMap<>(raw);
        spec.putIfAbsent("title", context != null && hasText(context.getTopic()) ? context.getTopic() : "互动页面");
        return switch (templateId) {
            case InteractiveTemplateCatalog.TEMPLATE_PHYSICS_COLLISION_V1 -> normalizePhysicsCollisionSpec(spec);
            case InteractiveTemplateCatalog.TEMPLATE_PPT_DEMO_V1 -> normalizePptDemoSpec(spec);
            case InteractiveTemplateCatalog.TEMPLATE_FLOW_DEMO_V1 -> normalizeFlowDemoSpec(spec);
            case InteractiveTemplateCatalog.TEMPLATE_ANIMATION_STEPPER_V1 -> normalizeAnimationStepperSpec(spec);
            case InteractiveTemplateCatalog.TEMPLATE_PARAMETER_EXPLORER_V1 -> normalizeParameterExplorerSpec(spec);
            case InteractiveTemplateCatalog.TEMPLATE_QUIZ_PRACTICE_V1 -> normalizeQuizPracticeSpec(spec);
            case InteractiveTemplateCatalog.TEMPLATE_DRAG_MATCH_V1 -> normalizeDragMatchSpec(spec);
            case InteractiveTemplateCatalog.TEMPLATE_HOTSPOT_EXPLORE_V1 -> normalizeHotspotExploreSpec(spec);
            default -> spec;
        };
    }

    private Map<String, Object> normalizePhysicsCollisionSpec(Map<String, Object> spec) {
        spec.put("subtitle", firstText(spec.get("subtitle"), "调节质量与速度，观察碰撞前后系统总动量的变化。"));
        spec.put("massA", clampNumber(spec.get("massA"), 2, 1, 5, 0.5));
        spec.put("massB", clampNumber(spec.get("massB"), 1, 1, 5, 0.5));
        spec.put("velocityA", clampNumber(spec.get("velocityA"), 4, -6, 6, 0.5));
        spec.put("velocityB", clampNumber(spec.get("velocityB"), 0, -6, 6, 0.5));
        spec.put("defaultCollisionType", "inelastic".equalsIgnoreCase(firstText(spec.get("defaultCollisionType"), "elastic")) ? "inelastic" : "elastic");
        spec.put("showKineticEnergy", normalizeBoolean(spec.get("showKineticEnergy"), true));
        spec.put("showFormula", normalizeBoolean(spec.get("showFormula"), true));
        return spec;
    }

    private Map<String, Object> normalizePptDemoSpec(Map<String, Object> spec) {
        List<Map<String, Object>> slides = normalizeMapList(spec.get("slides"));
        if (slides.isEmpty()) {
            slides = List.of(
                    mapOf(
                            "tag", "引入",
                            "title", "先建立整体认识",
                            "summary", "用一句话把主题先讲清楚。",
                            "bullets", List.of("说明这节内容在讲什么", "告诉学生最值得关注的变化", "建立整体框架"),
                            "highlight", "先整体，后细节"
                    ),
                    mapOf(
                            "tag", "核心",
                            "title", "抓住关键关系",
                            "summary", "把最核心的概念、条件或规律拆开说明。",
                            "bullets", List.of("突出关键概念", "解释变量之间的关系", "避免一次堆太多信息"),
                            "highlight", "围绕一个核心点展开"
                    ),
                    mapOf(
                            "tag", "总结",
                            "title", "回到结论",
                            "summary", "用收束页帮助学生回顾重点。",
                            "bullets", List.of("重申核心规律", "提醒常见误区", "给出应用场景"),
                            "highlight", "从现象回到结论"
                    )
            );
        }
        List<Map<String, Object>> normalizedSlides = new ArrayList<>();
        int index = 0;
        for (Map<String, Object> slide : slides.stream().limit(5).toList()) {
            index++;
            List<String> bullets = normalizeList(slide.get("bullets"));
            if (bullets.isEmpty()) {
                bullets = List.of("补充这个部分的第 1 个要点。", "补充这个部分的第 2 个要点。");
            }
            normalizedSlides.add(mapOf(
                    "tag", firstText(slide.get("tag"), "模块 " + index),
                    "title", firstText(slide.get("title"), "内容页 " + index),
                    "summary", firstText(slide.get("summary"), "这里补充这一部分的讲解摘要。"),
                    "bullets", bullets.stream().limit(4).map(item -> firstText(item, "补充要点。")).toList(),
                    "highlight", firstText(slide.get("highlight"), firstText(slide.get("tag"), "关键提示"))
            ));
        }
        spec.put("subtitle", firstText(spec.get("subtitle"), "按顺序浏览讲解卡片，用一个页面完成概念引入、展开与总结。"));
        spec.put("intro", firstText(spec.get("intro"), "这个页面适合做课堂讲解、概念导入、结构梳理或对比展示。"));
        spec.put("slides", normalizedSlides);
        return spec;
    }

    private Map<String, Object> normalizeFlowDemoSpec(Map<String, Object> spec) {
        List<Map<String, Object>> steps = normalizeMapList(spec.get("steps"));
        if (steps.isEmpty()) {
            steps = List.of(
                    mapOf("title", "阶段 1", "description", "先说明过程开始时的条件。", "keyword", "起点"),
                    mapOf("title", "阶段 2", "description", "展示系统中最关键的变化。", "keyword", "变化"),
                    mapOf("title", "阶段 3", "description", "补充中间的转化或传递关系。", "keyword", "转化"),
                    mapOf("title", "阶段 4", "description", "给出最终结果并回扣整体规律。", "keyword", "结果")
            );
        }
        List<Map<String, Object>> normalizedSteps = new ArrayList<>();
        int index = 0;
        for (Map<String, Object> step : steps.stream().limit(6).toList()) {
            index++;
            normalizedSteps.add(mapOf(
                    "title", firstText(step.get("title"), "阶段 " + index),
                    "description", firstText(step.get("description"), "这里补充这个阶段的说明。"),
                    "keyword", firstText(step.get("keyword"), "节点 " + index)
            ));
        }
        spec.put("subtitle", firstText(spec.get("subtitle"), "点击流程节点，逐步查看关键阶段和变化关系。"));
        spec.put("overview", firstText(spec.get("overview"), "这个页面适合讲解循环、流程、机制、时序或生命周期。"));
        spec.put("steps", normalizedSteps);
        return spec;
    }

    private Map<String, Object> normalizeAnimationStepperSpec(Map<String, Object> spec) {
        List<Map<String, Object>> steps = normalizeMapList(spec.get("steps"));
        if (steps.isEmpty()) {
            steps = List.of(
                    mapOf("title", "步骤 1", "text", "先观察现象发生前的状态。"),
                    mapOf("title", "步骤 2", "text", "再关注关键变量如何变化。"),
                    mapOf("title", "步骤 3", "text", "最后总结这个过程对应的知识规律。")
            );
        }
        spec.put("subtitle", firstText(spec.get("subtitle"), "按照顺序点击步骤，逐步理解知识点的变化过程。"));
        spec.put("intro", firstText(spec.get("intro"), "这个页面适合用来做课堂讲解、过程演示或思路梳理。"));
        spec.put("steps", steps.stream().limit(6).map(step -> mapOf(
                "title", firstText(step.get("title"), "步骤"),
                "text", firstText(step.get("text"), "请补充这一步的说明。")
        )).toList());
        return spec;
    }

    private Map<String, Object> normalizeParameterExplorerSpec(Map<String, Object> spec) {
        List<Map<String, Object>> parameters = normalizeMapList(spec.get("parameters"));
        if (parameters.isEmpty()) {
            parameters = List.of(
                    mapOf("key", "x", "label", "x", "min", -10, "max", 10, "step", 1, "defaultValue", 0, "unit", ""),
                    mapOf("key", "a", "label", "a", "min", -5, "max", 5, "step", 0.5, "defaultValue", 1, "unit", "")
            );
        }
        List<Map<String, Object>> normalizedParameters = new ArrayList<>();
        for (Map<String, Object> parameter : parameters.stream().limit(4).toList()) {
            String key = normalizeKey(parameter.get("key"));
            if (key == null) {
                continue;
            }
            double min = clampNumber(parameter.get("min"), -5, -100, 100, null);
            double max = clampNumber(parameter.get("max"), 5, -100, 100, null);
            if (min >= max) {
                max = min + 1;
            }
            normalizedParameters.add(mapOf(
                    "key", key,
                    "label", firstText(parameter.get("label"), key),
                    "min", min,
                    "max", max,
                    "step", clampNumber(parameter.get("step"), 0.5, 0.1, 20, null),
                    "defaultValue", clampNumber(parameter.get("defaultValue"), 0, min, max, null),
                    "unit", firstText(parameter.get("unit"), "")
            ));
        }
        if (normalizedParameters.isEmpty()) {
            normalizedParameters = List.of(
                    mapOf("key", "x", "label", "x", "min", -10, "max", 10, "step", 1, "defaultValue", 0, "unit", ""),
                    mapOf("key", "a", "label", "a", "min", -5, "max", 5, "step", 0.5, "defaultValue", 1, "unit", "")
            );
        }
        String preferredAxisKey = firstText(normalizeMap(spec.get("chart")).get("xKey"), "x");
        List<Map<String, Object>> metrics = normalizeMetrics(spec.get("metrics"), normalizedParameters);
        spec.put("subtitle", firstText(spec.get("subtitle"), "拖动参数，观察结果数值和图像如何随之变化。"));
        spec.put("formulaText", firstText(spec.get("formulaText"), "通过参数变化理解公式中各个量之间的关系。"));
        spec.put("parameters", normalizedParameters);
        spec.put("metrics", metrics);
        spec.put("chart", normalizeChart(spec.get("chart"), normalizedParameters, metrics, preferredAxisKey));
        return spec;
    }

    private Map<String, Object> normalizeQuizPracticeSpec(Map<String, Object> spec) {
        List<Map<String, Object>> questions = normalizeMapList(spec.get("questions"));
        if (questions.isEmpty()) {
            questions = List.of(mapOf(
                    "type", "single_choice",
                    "prompt", "请补充题目内容。",
                    "options", List.of("选项 A", "选项 B", "选项 C", "选项 D"),
                    "answer", "选项 A",
                    "explanation", "这里补充解析。"
            ));
        }
        List<Map<String, Object>> normalized = new ArrayList<>();
        for (Map<String, Object> question : questions.stream().limit(5).toList()) {
            String type = firstText(question.get("type"), "single_choice").toLowerCase(Locale.ROOT);
            if (!List.of("single_choice", "true_false", "short_text").contains(type)) {
                type = "single_choice";
            }
            List<String> options = "single_choice".equals(type)
                    ? normalizeList(question.get("options"))
                    : List.of("正确", "错误");
            if ("single_choice".equals(type) && options.size() < 2) {
                options = List.of("选项 A", "选项 B", "选项 C", "选项 D");
            }
            normalized.add(mapOf(
                    "type", type,
                    "prompt", firstText(question.get("prompt"), "请补充题目内容。"),
                    "options", options,
                    "answer", question.get("answer") != null ? question.get("answer") : ("true_false".equals(type) ? "正确" : options.get(0)),
                    "explanation", firstText(question.get("explanation"), "这里补充解析。")
            ));
        }
        spec.put("subtitle", firstText(spec.get("subtitle"), "完成题目后立即查看得分、答案与解析。"));
        spec.put("questions", normalized);
        return spec;
    }

    private Map<String, Object> normalizeDragMatchSpec(Map<String, Object> spec) {
        List<Map<String, Object>> leftItems = normalizeMapList(spec.get("leftItems"));
        List<Map<String, Object>> rightItems = normalizeMapList(spec.get("rightItems"));
        List<Map<String, Object>> pairs = normalizeMapList(spec.get("pairs"));
        if (leftItems.isEmpty() || rightItems.isEmpty()) {
            leftItems = List.of(mapOf("id", "l1", "label", "概念 A"), mapOf("id", "l2", "label", "概念 B"));
            rightItems = List.of(mapOf("id", "r1", "label", "解释 1"), mapOf("id", "r2", "label", "解释 2"));
            pairs = List.of(mapOf("leftId", "l1", "rightId", "r1"), mapOf("leftId", "l2", "rightId", "r2"));
        }
        List<Map<String, Object>> normalizedLeft = leftItems.stream().limit(6)
                .map(item -> mapOf("id", firstText(item.get("id"), "l1"), "label", firstText(item.get("label"), "项目")))
                .toList();
        List<Map<String, Object>> normalizedRight = rightItems.stream().limit(6)
                .map(item -> mapOf("id", firstText(item.get("id"), "r1"), "label", firstText(item.get("label"), "目标")))
                .toList();
        if (pairs.isEmpty()) {
            List<Map<String, Object>> generatedPairs = new ArrayList<>();
            for (int i = 0; i < Math.min(normalizedLeft.size(), normalizedRight.size()); i++) {
                generatedPairs.add(mapOf("leftId", normalizedLeft.get(i).get("id"), "rightId", normalizedRight.get(i).get("id")));
            }
            pairs = generatedPairs;
        }
        spec.put("subtitle", firstText(spec.get("subtitle"), "拖动左侧项目到右侧目标区，完成对应关系配对。"));
        spec.put("leftItems", normalizedLeft);
        spec.put("rightItems", normalizedRight);
        spec.put("pairs", pairs.stream().limit(Math.min(normalizedLeft.size(), normalizedRight.size()))
                .map(pair -> mapOf("leftId", firstText(pair.get("leftId"), "l1"), "rightId", firstText(pair.get("rightId"), "r1")))
                .toList());
        return spec;
    }

    private Map<String, Object> normalizeHotspotExploreSpec(Map<String, Object> spec) {
        List<Map<String, Object>> hotspots = normalizeMapList(spec.get("hotspots"));
        if (hotspots.isEmpty()) {
            hotspots = List.of(
                    mapOf("id", "h1", "label", "热点 1", "x", 25, "y", 30, "content", "这里补充第一个热点的说明。"),
                    mapOf("id", "h2", "label", "热点 2", "x", 70, "y", 55, "content", "这里补充第二个热点的说明。")
            );
        }
        List<Map<String, Object>> normalized = new ArrayList<>();
        int index = 0;
        for (Map<String, Object> hotspot : hotspots.stream().limit(6).toList()) {
            index++;
            normalized.add(mapOf(
                    "id", firstText(hotspot.get("id"), "h" + index),
                    "label", firstText(hotspot.get("label"), "热点 " + index),
                    "x", clampInt(hotspot.get("x"), 15 + index * 10, 10, 90),
                    "y", clampInt(hotspot.get("y"), 20 + index * 8, 10, 90),
                    "content", firstText(hotspot.get("content"), "这里补充热点说明。")
            ));
        }
        spec.put("subtitle", firstText(spec.get("subtitle"), "点击画面中的热点，查看对应知识说明。"));
        spec.put("boardTitle", firstText(spec.get("boardTitle"), "点击热点探索"));
        spec.put("hotspots", normalized);
        return spec;
    }

    private List<Map<String, Object>> normalizeMetrics(Object raw, List<Map<String, Object>> parameters) {
        List<Map<String, Object>> metrics = normalizeMapList(raw);
        List<Map<String, Object>> fallbackMetrics = buildParameterMetricsFallback(parameters);
        Set<String> parameterKeys = extractParameterKeys(parameters);
        if (metrics.isEmpty()) {
            return fallbackMetrics;
        }
        if (metrics.isEmpty()) {
            metrics = List.of(mapOf("label", "结果", "expression", "a * x", "unit", "", "precision", 2));
        }
        List<Map<String, Object>> normalizedMetrics = new ArrayList<>();
        int index = 0;
        boolean hasCompatibleMetric = false;
        for (Map<String, Object> metric : metrics.stream().limit(4).toList()) {
            String expression = firstText(metric.get("expression"), null);
            if (!isExpressionCompatible(expression, parameterKeys)) {
                normalizedMetrics.add(new LinkedHashMap<>(fallbackMetrics.get(Math.min(index, fallbackMetrics.size() - 1))));
                index++;
                continue;
            }
            hasCompatibleMetric = true;
            normalizedMetrics.add(mapOf(
                    "label", metric.get("label"),
                    "expression", expression,
                    "unit", metric.get("unit"),
                    "precision", metric.get("precision")
            ));
            index++;
        }
        metrics = !hasCompatibleMetric || normalizedMetrics.isEmpty() ? fallbackMetrics : normalizedMetrics;
        return metrics.stream().limit(4).map(metric -> mapOf(
                "label", firstText(metric.get("label"), "结果"),
                "expression", firstText(metric.get("expression"), "0"),
                "unit", firstText(metric.get("unit"), ""),
                "precision", clampInt(metric.get("precision"), 2, 0, 6)
        )).toList();
    }

    private Map<String, Object> normalizeChart(Object raw,
                                               List<Map<String, Object>> parameters,
                                               List<Map<String, Object>> metrics,
                                               String preferredAxisKey) {
        Map<String, Object> chart = normalizeMap(raw);
        String defaultXKey = normalizeAxisKey(preferredAxisKey);
        if (defaultXKey == null) {
            defaultXKey = String.valueOf(parameters.get(0).get("key"));
        }
        Set<String> allowedKeys = extractParameterKeys(parameters);
        allowedKeys.add(defaultXKey);
        String defaultExpression = buildChartFallbackExpression(metrics, parameters, defaultXKey, allowedKeys);
        String yExpression = firstText(chart.get("yExpression"), defaultExpression);
        if (!isExpressionCompatible(yExpression, allowedKeys)) {
            yExpression = defaultExpression;
        }
        Map<String, Object> boundParameter = findParameter(parameters, defaultXKey);
        double axisMin = boundParameter != null
                ? clampNumber(boundParameter.get("min"), -10, -100, 100, null)
                : clampNumber(chart.get("xMin"), -10, -100, 100, null);
        double axisMax = boundParameter != null
                ? clampNumber(boundParameter.get("max"), 10, -100, 100, null)
                : clampNumber(chart.get("xMax"), 10, -100, 100, null);
        if (axisMin >= axisMax) {
            axisMax = axisMin + 1;
        }
        double defaultXValue = boundParameter != null
                ? clampNumber(boundParameter.get("defaultValue"), axisMin, axisMin, axisMax, null)
                : clampNumber(chart.get("defaultXValue"), 0, axisMin, axisMax, null);
        return mapOf(
                "xKey", defaultXKey,
                "xMin", axisMin,
                "xMax", axisMax,
                "defaultXValue", defaultXValue,
                "yExpression", yExpression,
                "yLabel", firstText(chart.get("yLabel"), "结果值")
        );
    }

    private List<Map<String, Object>> buildParameterMetricsFallback(List<Map<String, Object>> parameters) {
        List<Map<String, Object>> fallback = new ArrayList<>();
        for (Map<String, Object> parameter : parameters.stream().limit(3).toList()) {
            String key = String.valueOf(parameter.get("key"));
            fallback.add(mapOf(
                    "label", firstText(parameter.get("label"), key),
                    "expression", key,
                    "unit", firstText(parameter.get("unit"), ""),
                    "precision", 2
            ));
        }
        if (fallback.isEmpty()) {
            fallback.add(mapOf("label", "Result", "expression", "0", "unit", "", "precision", 2));
        }
        return fallback;
    }

    private String buildChartFallbackExpression(List<Map<String, Object>> metrics,
                                                List<Map<String, Object>> parameters,
                                                String axisKey,
                                                Set<String> allowedKeys) {
        for (Map<String, Object> metric : metrics) {
            String expression = firstText(metric.get("expression"), null);
            if (isExpressionCompatible(expression, allowedKeys)) {
                return expression;
            }
        }

        Set<String> parameterKeys = extractParameterKeys(parameters);
        if (!parameterKeys.contains(axisKey)) {
            if (parameterKeys.contains("a") && parameterKeys.contains("b")) {
                return "a * " + axisKey + " + b";
            }
            if (parameterKeys.contains("a")) {
                return "a * " + axisKey;
            }
        }

        return parameters.isEmpty() ? "0" : String.valueOf(parameters.get(0).get("key"));
    }

    private Set<String> extractParameterKeys(List<Map<String, Object>> parameters) {
        Set<String> keys = new LinkedHashSet<>();
        for (Map<String, Object> parameter : parameters) {
            String key = normalizeAxisKey(parameter.get("key"));
            if (key != null) {
                keys.add(key);
            }
        }
        return keys;
    }

    private Map<String, Object> findParameter(List<Map<String, Object>> parameters, String key) {
        for (Map<String, Object> parameter : parameters) {
            if (Objects.equals(normalizeAxisKey(parameter.get("key")), key)) {
                return parameter;
            }
        }
        return null;
    }

    private String normalizeAxisKey(Object raw) {
        return normalizeKey(raw);
    }

    private boolean isExpressionCompatible(String expression, Collection<String> allowedKeys) {
        if (!hasText(expression)) {
            return false;
        }
        String sanitized = expression.replaceAll("Math\\s*\\.\\s*[A-Za-z_][A-Za-z0-9_]*", " ");
        Matcher matcher = IDENTIFIER_PATTERN.matcher(sanitized);
        while (matcher.find()) {
            String token = matcher.group();
            if (List.of("Math", "true", "false", "null", "NaN", "Infinity").contains(token)) {
                continue;
            }
            if (allowedKeys == null || !allowedKeys.contains(token)) {
                return false;
            }
        }
        return true;
    }

    private Map<String, Object> parseJsonMap(String raw) throws Exception {
        if (!hasText(raw)) {
            return Map.of();
        }
        String trimmed = raw.trim();
        if (trimmed.startsWith("```")) {
            trimmed = trimmed.replaceFirst("^```[a-zA-Z]*\\s*", "");
            trimmed = trimmed.replaceFirst("\\s*```$", "");
        }
        try {
            return objectMapper.readValue(trimmed, MAP_TYPE);
        } catch (Exception ignored) {
            Matcher matcher = Pattern.compile("\\{[\\s\\S]*}", Pattern.MULTILINE).matcher(trimmed);
            if (matcher.find()) {
                return objectMapper.readValue(matcher.group(), MAP_TYPE);
            }
            throw ignored;
        }
    }

    private String buildDetectionText(InteractiveContext context, String instruction) {
        List<String> parts = new ArrayList<>();
        if (context != null) {
            addIfText(parts, context.getTopic());
            addIfText(parts, context.getSubject());
            addIfText(parts, context.getGrade());
            addIfText(parts, context.getInteractionType());
            addIfText(parts, context.getPageGoal());
            addIfText(parts, context.getStudentAction());
            addIfText(parts, context.getScene());
            addIfText(parts, context.getTeachingFocus());
            addIfText(parts, context.getStyleHint());
            addIfText(parts, context.getNotes());
            addAllIfText(parts, context.safeMustHaveInteractions());
            addAllIfText(parts, context.safeConstraints());
        }
        addIfText(parts, instruction);
        return String.join(" ", parts);
    }

    private void addIfText(List<String> parts, String value) {
        if (hasText(value)) {
            parts.add(value.trim());
        }
    }

    private void addAllIfText(List<String> parts, List<String> values) {
        if (values != null) {
            values.stream().filter(this::hasText).map(String::trim).forEach(parts::add);
        }
    }

    private boolean containsAny(String text, String... candidates) {
        return Stream.of(candidates).filter(this::hasText).anyMatch(text::contains);
    }

    private String firstText(Object primary, String fallback) {
        String text = normalizeText(primary);
        return text != null ? text : fallback;
    }

    private String normalizeKey(Object raw) {
        String text = normalizeText(raw);
        if (text == null) {
            return null;
        }
        String key = text.replaceAll("[^A-Za-z0-9_]", "");
        return key.isBlank() ? null : key;
    }

    private List<String> normalizeList(Object raw) {
        if (raw instanceof Collection<?> collection) {
            return collection.stream().map(this::normalizeText).filter(Objects::nonNull).toList();
        }
        String text = normalizeText(raw);
        if (text == null) {
            return List.of();
        }
        return Stream.of(text.split("[,，；;\\n]")).map(String::trim).filter(part -> !part.isBlank()).toList();
    }

    private Map<String, Object> normalizeMap(Object raw) {
        if (raw instanceof Map<?, ?> map) {
            Map<String, Object> normalized = new LinkedHashMap<>();
            map.forEach((key, value) -> {
                String normalizedKey = normalizeText(key);
                if (normalizedKey != null) {
                    normalized.put(normalizedKey, value);
                }
            });
            return normalized;
        }
        if (raw instanceof String text) {
            String normalized = normalizeText(text);
            if (normalized != null && normalized.startsWith("{") && normalized.endsWith("}")) {
                try {
                    return objectMapper.readValue(normalized, MAP_TYPE);
                } catch (Exception ignored) {
                    return Map.of();
                }
            }
        }
        return Map.of();
    }

    private List<Map<String, Object>> normalizeMapList(Object raw) {
        if (raw instanceof Collection<?> collection) {
            return collection.stream().map(this::normalizeMap).filter(map -> !map.isEmpty()).toList();
        }
        if (raw instanceof String text) {
            String normalized = normalizeText(text);
            if (normalized != null && normalized.startsWith("[") && normalized.endsWith("]")) {
                try {
                    List<?> parsed = objectMapper.readValue(normalized, List.class);
                    return parsed.stream().map(this::normalizeMap).filter(map -> !map.isEmpty()).toList();
                } catch (Exception ignored) {
                    return List.of();
                }
            }
        }
        return List.of();
    }

    private double clampNumber(Object raw, double fallback, double min, double max, Double step) {
        double value = fallback;
        if (raw instanceof Number number) {
            value = number.doubleValue();
        } else {
            String text = normalizeText(raw);
            if (text != null) {
                try {
                    value = Double.parseDouble(text);
                } catch (NumberFormatException ignored) {
                    value = fallback;
                }
            }
        }
        value = Math.max(min, Math.min(max, value));
        if (step != null && step > 0) {
            value = Math.round(value / step) * step;
        }
        return value;
    }

    private int clampInt(Object raw, int fallback, int min, int max) {
        int value = fallback;
        if (raw instanceof Number number) {
            value = number.intValue();
        } else {
            String text = normalizeText(raw);
            if (text != null) {
                try {
                    value = Integer.parseInt(text);
                } catch (NumberFormatException ignored) {
                    value = fallback;
                }
            }
        }
        return Math.max(min, Math.min(max, value));
    }

    private boolean normalizeBoolean(Object raw, boolean fallback) {
        if (raw instanceof Boolean bool) {
            return bool;
        }
        String text = normalizeText(raw);
        if (text == null) {
            return fallback;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        if (List.of("true", "1", "yes", "y").contains(lower)) {
            return true;
        }
        if (List.of("false", "0", "no", "n").contains(lower)) {
            return false;
        }
        return fallback;
    }

    private Map<String, Object> mapOf(Object... values) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < values.length; i += 2) {
            map.put(String.valueOf(values[i]), values[i + 1]);
        }
        return map;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return "{}";
        }
    }

    private String normalizeText(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() || "null".equalsIgnoreCase(text) ? null : text;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
