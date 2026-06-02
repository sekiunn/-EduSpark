package com.eduspark.eduspark.service.impl;

import com.eduspark.eduspark.dto.courseware.LessonPlanRequest;
import com.eduspark.eduspark.dto.courseware.LessonPlanResponse;
import com.eduspark.eduspark.dto.courseware.PptDeckPlan;
import com.eduspark.eduspark.dto.courseware.PptGenerateRequest;
import com.eduspark.eduspark.dto.courseware.PptGenerateResponse;
import com.eduspark.eduspark.dto.courseware.PptSlidePlan;
import com.eduspark.eduspark.dto.courseware.TemplateFillPlan;
import com.eduspark.eduspark.dto.courseware.TemplateSlideFill;
import com.eduspark.eduspark.dto.courseware.TextBlockInfo;
import com.eduspark.eduspark.dto.ppt.TemplateMarkerInfo;
import com.eduspark.eduspark.dto.ppt.TemplateSlideStructure;
import com.eduspark.eduspark.dto.ppt.TemplateStructure;
import com.eduspark.eduspark.pojo.entity.PptTemplate;
import com.eduspark.eduspark.service.ICoursewareService;
import com.eduspark.eduspark.service.ILLMService;
import com.eduspark.eduspark.service.ILessonPlanWriterService;
import com.eduspark.eduspark.service.IPptTemplateService;
import com.eduspark.eduspark.util.LessonPlanContentUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.sl.usermodel.ShapeType;
import org.apache.poi.sl.usermodel.TextParagraph;
import org.apache.poi.sl.usermodel.TextShape;
import org.apache.poi.sl.usermodel.VerticalAlignment;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFAutoShape;
import org.apache.poi.xslf.usermodel.XSLFGroupShape;
import org.apache.poi.xslf.usermodel.XSLFNotes;
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTable;
import org.apache.poi.xslf.usermodel.XSLFTableCell;
import org.apache.poi.xslf.usermodel.XSLFTableRow;
import org.apache.poi.xslf.usermodel.XSLFTextBox;
import org.apache.poi.xslf.usermodel.XSLFTextParagraph;
import org.apache.poi.xslf.usermodel.XSLFTextRun;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.io.ByteArrayOutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class CoursewareServiceImpl implements ICoursewareService {

    private static final String LESSON_PLAN_REWRITE_SYSTEM_PROMPT =
            "You revise lesson-plan passages written in Markdown. Return only the rewritten fragment in the same "
                    + "language as the original. Preserve useful Markdown structure and do not add explanations.";
    private static final String PPT_JSON_SYSTEM_PROMPT =
            "You are a senior Chinese teaching presentation planner. Return strict JSON only. "
                    + "Do not wrap in markdown fences. Design a slide-by-slide plan for a Chinese classroom PPT.";
    private static final String PPT_JSON_USER_PROMPT_TEMPLATE =
            "Design a Chinese teaching PPT deck and return a single JSON object.\n"
                    + "Schema: {\"title\":string,\"subtitle\":string,\"slideCount\":number,\"themeName\":string,\"style\":string,"
                    + "\"slides\":[{\"slideNo\":number,\"title\":string,"
                    + "\"layout\":\"cover|content|summary\","
                    + "\"slotLayout\":\"title_only|bullet_list|two_column|image_right|quote|code|comparison|chart\","
                    + "\"assetType\":\"icon|photo|chart|diagram|none\","
                    + "\"assetKeywords\":[string],"
                    + "\"bullets\":[string],\"speakerNotes\":string,\"visualFocus\":string}]}\n\n"
                    + "Rules:\n"
                    + "1. First slide must have layout=\"cover\" (slotLayout=\"title_only\").\n"
                    + "2. Last slide must have layout=\"summary\" (slotLayout=\"bullet_list\").\n"
                    + "3. Each content slide MUST choose a slotLayout from the enum above; AVOID using bullet_list for ALL slides — vary the layout (use two_column for compare/对比, image_right for concept+illustration, quote for 引言/语录, code for 代码示例, comparison for 优劣对比, chart for 数据/统计).\n"
                    + "4. Each content slide must have 2-5 concise Chinese bullets, EACH bullet ≤ 18 characters (Chinese punctuation counts). Do not write paragraphs in bullets.\n"
                    + "5. assetType MUST be one of icon|photo|chart|diagram|none. Prefer \"icon\" for concept/list slides.\n"
                    + "6. assetKeywords MUST contain 1-3 short Chinese keywords (≤4 chars each) describing the slide's visual focus, e.g. [\"目标\",\"算法\"]. These will be used to look up icons.\n"
                    + "7. For \"code\" slotLayout, put the code snippet in the FIRST bullet (use \\n for line breaks); subsequent bullets are commentary.\n"
                    + "8. For \"two_column\" / \"comparison\" slotLayout, prefix bullets with \"左：\"/\"右：\" or \"优：\"/\"劣：\" so renderer can split.\n"
                    + "9. speakerNotes ≤ 60 Chinese characters; visualFocus ≤ 30 chars.\n"
                    + "10. Total slides should be close to %d.\n\n"
                    + "%s";
    private static final String INTERACTIVE_SYSTEM_PROMPT =
            "You are a teaching assessment assistant. Return a JSON array only.";

    private static final String TEMPLATE_FILL_SYSTEM_PROMPT =
            "你是中国教学课件文案策划师。根据给定的 PPT 模板标记结构，为每个标记生成替换文案。"
                    + "只返回 JSON，不要用 markdown 代码块包裹。";

    private static final String TEMPLATE_FILL_USER_PROMPT_TEMPLATE =
            "请为教学 PPT 模板填充内容，返回单个 JSON 对象。\n"
                    + "Schema: {\"targetSlideCount\":number,\"extraContentPages\":number,"
                    + "\"slides\":[{\"slideIndex\":number,\"isDuplicate\":boolean,"
                    + "\"markerValues\":{\"标记名\":\"替换文本\",...}}]}\n\n"
                    + "规则：\n"
                    + "1. 为模板中每个 {{标记}} 生成对应内容，markerValues 的 key 必须与输入标记名完全一致。\n"
                    + "2. extraContentPages > 0 时系统会复制内容模板页，为这些额外页也生成 markerValues。\n"
                    + "3. 封面页填课程标题、副标题、教师姓名等。\n"
                    + "4. 内容页填教学要点，列表项用换行符 \\n 分隔。\n"
                    + "5. 总结页填复习要点、作业布置等。\n"
                    + "6. 每个标记值要简练，适合幻灯片展示。\n"
                    + "7. 根据主题内容量合理设置 targetSlideCount 和 extraContentPages。\n\n"
                    + "模板标记结构：\n%s\n\n"
                    + "教学需求：\n%s";

    private static final int MAX_PPT_SLIDES = 18;
    private static final int MAX_PPT_BULLETS = 5;
    /** 单条 bullet 文本最大字符数 —— 超过会触发 POI 自动缩字号导致排版变丑。 */
    private static final int MAX_BULLET_CHARS = 22;
    /** 代码块单行最大字符数（slotLayout=code 时使用）。 */
    private static final int MAX_CODE_LINE_CHARS = 60;

    private final ILLMService llmService;
    private final ILessonPlanWriterService lessonPlanWriterService;
    private final IPptTemplateService pptTemplateService;
    private final ObjectMapper objectMapper;

    @Value("${courseware.storage.local-path:./data/courseware}")
    private String localStoragePath;

    public CoursewareServiceImpl(ILLMService llmService,
                                 ILessonPlanWriterService lessonPlanWriterService,
                                 IPptTemplateService pptTemplateService,
                                 ObjectMapper objectMapper) {
        this.llmService = llmService;
        this.lessonPlanWriterService = lessonPlanWriterService;
        this.pptTemplateService = pptTemplateService;
        this.objectMapper = objectMapper;
    }

    @Override
    public LessonPlanResponse generateLessonPlan(LessonPlanRequest request) {
        try {
            String content = streamLessonPlanContent(request, chunk -> {
            });
            if (!hasText(content)) {
                return LessonPlanResponse.builder()
                        .success(false)
                        .error("教案生成失败：未返回正文内容")
                        .build();
            }
            return exportLessonPlanDocument(request, content);
        } catch (Exception e) {
            log.error("Generate lesson plan failed", e);
            return LessonPlanResponse.builder()
                    .success(false)
                    .error("教案生成异常: " + e.getMessage())
                    .build();
        }
    }

    @Override
    public LessonPlanResponse exportLessonPlanDocument(LessonPlanRequest request, String content) {
        try {
            if (!hasText(content)) {
                return LessonPlanResponse.builder()
                        .success(false)
                        .error("教案导出失败：正文为空")
                        .build();
            }

            String markdownContent = LessonPlanContentUtils.normalizeMarkdownContent(content);
            String plainText = LessonPlanContentUtils.toPlainText(markdownContent);
            byte[] docxBytes = buildDocx(request, markdownContent);

            String baseName = hasText(request.getTopic())
                    ? request.getTopic()
                    : valueOrDefault(request.getSubject(), "教案");
            String fileName = String.format(
                    "%s_教案_%s.docx",
                    sanitizeFileName(baseName),
                    LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)
            );
            String filePath = saveLocally(fileName, docxBytes);

            return LessonPlanResponse.builder()
                    .success(true)
                    .filePath(filePath)
                    .downloadUrl(buildDownloadUrl(filePath))
                    .fileName(fileName)
                    .fileSize((long) docxBytes.length)
                    .preview(buildPreview(plainText))
                    .content(markdownContent)
                    .build();
        } catch (Exception e) {
            log.error("Export lesson plan document failed", e);
            return LessonPlanResponse.builder()
                    .success(false)
                    .error("教案导出异常: " + e.getMessage())
                    .build();
        }
    }

    @Override
    public String streamLessonPlanContent(LessonPlanRequest request, Consumer<String> onChunk) {
        return lessonPlanWriterService.streamDraft(request, onChunk == null ? chunk -> {
        } : onChunk);
    }

    @Override
    public String rewriteLessonPlanFragment(String documentContent, String selectedText, String instruction) {
        List<ILLMService.ChatMessage> messages = List.of(
                new ILLMService.ChatMessage("system", LESSON_PLAN_REWRITE_SYSTEM_PROMPT),
                new ILLMService.ChatMessage("user", buildLessonPlanRewritePrompt(documentContent, selectedText, instruction))
        );
        return sanitizeRewriteResponse(llmService.chat(messages));
    }

    @Override
    public PptGenerateResponse generatePpt(PptGenerateRequest request) {
        try {
            String planningMarkdown = streamPptPlanning(request, chunk -> {
            });
            PptDeckPlan plan = buildPptPlan(request, planningMarkdown);
            PptGenerateResponse response = renderPpt(request, plan);
            response.setPlanningMarkdown(planningMarkdown);
            response.setPlan(plan);
            return response;
        } catch (Exception e) {
            log.error("Generate ppt failed", e);
            return PptGenerateResponse.builder()
                    .success(false)
                    .error("PPT 生成异常: " + e.getMessage())
                    .build();
        }
    }

    @Override
    public String streamPptPlanning(PptGenerateRequest request, Consumer<String> onChunk) {
        PptTemplate template = resolveTemplateSafely(request.getTemplateId());
        PptGenerateRequest effectiveRequest = enrichPptRequest(request, template);

        try {
            int slideCount = effectiveRequest.getSlideCount() != null ? effectiveRequest.getSlideCount() : 8;
            String requestBlock = buildPptRequestBlock(effectiveRequest, template);
            String userPrompt = String.format(Locale.ROOT, PPT_JSON_USER_PROMPT_TEMPLATE, slideCount, requestBlock);

            List<ILLMService.ChatMessage> messages = List.of(
                    new ILLMService.ChatMessage("system", PPT_JSON_SYSTEM_PROMPT),
                    new ILLMService.ChatMessage("user", userPrompt)
            );
            return llmService.chatStream(messages, onChunk == null ? chunk -> {} : onChunk);
        } catch (Exception e) {
            log.warn("Stream ppt planning failed, returning empty", e);
            return "";
        }
    }

    @Override
    public PptDeckPlan buildPptPlan(PptGenerateRequest request, String planningJson) {
        PptTemplate template = resolveTemplateSafely(request.getTemplateId());
        PptGenerateRequest effectiveRequest = enrichPptRequest(request, template);

        PptDeckPlan parsedPlan = parsePlanJson(planningJson);
        return normalizePlan(parsedPlan, effectiveRequest, template, null);
    }

    @Override
    public PptGenerateResponse renderPpt(PptGenerateRequest request, PptDeckPlan plan) {
        try {
            PptTemplate template = resolveTemplateSafely(request.getTemplateId());
            PptGenerateRequest effectiveRequest = enrichPptRequest(request, template);

            byte[] pptxBytes;
            PptDeckPlan normalizedPlan = null;
            if (hasParsedMarkers(template)) {
                TemplateFillPlan fillPlan = buildFillPlanFromDeckPlan(plan, template);
                pptxBytes = renderTemplatedPptx(template, fillPlan);
            } else {
                normalizedPlan = normalizePlan(plan, effectiveRequest, template, null);
                pptxBytes = buildRealPptx(normalizedPlan, effectiveRequest, template);
            }

            String baseName = hasText(effectiveRequest.getTitle()) ? effectiveRequest.getTitle() : "PPT";
            String fileName = String.format(
                    "%s_%s.pptx",
                    sanitizeFileName(baseName),
                    LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)
            );
            String filePath = saveLocally(fileName, pptxBytes);

            return PptGenerateResponse.builder()
                    .success(true)
                    .filePath(filePath)
                    .downloadUrl(buildDownloadUrl(filePath))
                    .fileName(fileName)
                    .fileSize((long) pptxBytes.length)
                    .outline(normalizedPlan != null ? extractOutline(normalizedPlan) : List.of())
                    .plan(normalizedPlan)
                    .build();
        } catch (Exception e) {
            log.error("Render ppt failed", e);
            return PptGenerateResponse.builder()
                    .success(false)
                    .error("PPT 渲染异常: " + e.getMessage())
                    .build();
        }
    }

    @Override
    public String streamTemplateFillPlanning(PptGenerateRequest request, PptTemplate template, Consumer<String> onChunk) {
        try {
            TemplateStructure structure = parseTemplateStructure(template);
            if (structure == null) {
                return "";
            }
            String structureJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(structure);
            String requestBlock = buildPptRequestBlock(enrichPptRequest(request, template), template);
            String userPrompt = String.format(Locale.ROOT, TEMPLATE_FILL_USER_PROMPT_TEMPLATE, structureJson, requestBlock);

            List<ILLMService.ChatMessage> messages = List.of(
                    new ILLMService.ChatMessage("system", TEMPLATE_FILL_SYSTEM_PROMPT),
                    new ILLMService.ChatMessage("user", userPrompt)
            );
            return llmService.chatStream(messages, onChunk == null ? chunk -> {} : onChunk);
        } catch (Exception e) {
            log.warn("Stream template fill planning failed", e);
            return "";
        }
    }

    @Override
    public TemplateFillPlan buildTemplateFillPlan(String planningJson) {
        if (!hasText(planningJson)) {
            return TemplateFillPlan.builder()
                    .targetSlideCount(0).extraContentPages(0).slides(List.of())
                    .build();
        }
        try {
            String json = planningJson.trim();
            if (json.startsWith("```")) {
                int start = json.indexOf('\n');
                int end = json.lastIndexOf("```");
                if (start > 0 && end > start) {
                    json = json.substring(start + 1, end).trim();
                }
            }
            return objectMapper.readValue(json, TemplateFillPlan.class);
        } catch (Exception e) {
            log.warn("Failed to parse template fill plan", e);
            return TemplateFillPlan.builder()
                    .targetSlideCount(0).extraContentPages(0).slides(List.of())
                    .build();
        }
    }

    @Override
    public PptGenerateResponse renderTemplatedPpt(PptGenerateRequest request, PptTemplate template, TemplateFillPlan fillPlan) {
        try {
            PptGenerateRequest effectiveRequest = enrichPptRequest(request, template);
            byte[] pptxBytes = renderTemplatedPptx(template, fillPlan);

            String baseName = hasText(effectiveRequest.getTitle()) ? effectiveRequest.getTitle() : "PPT";
            String fileName = String.format(
                    "%s_%s.pptx",
                    sanitizeFileName(baseName),
                    LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)
            );
            String filePath = saveLocally(fileName, pptxBytes);

            List<String> outline = List.of();
            if (fillPlan.getSlides() != null) {
                outline = fillPlan.getSlides().stream()
                        .map(s -> s.getMarkerValues() != null
                                ? s.getMarkerValues().entrySet().stream()
                                .filter(e -> e.getKey().contains("标题") || e.getKey().contains("title"))
                                .map(Map.Entry::getValue)
                                .findFirst().orElse("")
                                : "")
                        .filter(v -> !v.isEmpty())
                        .toList();
            }

            return PptGenerateResponse.builder()
                    .success(true)
                    .filePath(filePath)
                    .downloadUrl(buildDownloadUrl(filePath))
                    .fileName(fileName)
                    .fileSize((long) pptxBytes.length)
                    .outline(outline)
                    .build();
        } catch (Exception e) {
            log.error("Render templated ppt failed", e);
            return PptGenerateResponse.builder()
                    .success(false)
                    .error("PPT 模板渲染异常: " + e.getMessage())
                    .build();
        }
    }

    @Override
    public String generateInteractiveContent(String topic, int count, String type) {
        try {
            String prompt = String.format(
                    Locale.ROOT,
                    "围绕主题“%s”生成 %d 道%s教学题目。请只返回 JSON 数组，每个元素包含 question、options、answer、explanation 字段。",
                    valueOrDefault(topic, "课堂主题"),
                    Math.max(1, count),
                    "fill".equalsIgnoreCase(type) ? "填空" : "选择"
            );
            String content = llmService.chatWithContext(prompt, null, INTERACTIVE_SYSTEM_PROMPT);
            if (!hasText(content)) {
                return "[]";
            }
            int start = content.indexOf('[');
            int end = content.lastIndexOf(']');
            if (start >= 0 && end > start) {
                return content.substring(start, end + 1);
            }
        } catch (Exception e) {
            log.error("Generate interactive content failed", e);
        }
        return "[]";
    }

    @Override
    public String getDownloadUrl(String filePath, String format) {
        String url = "/api/v1/courseware/download?path=" + encodeDownloadParam(filePath);
        if (hasText(format)) {
            url += "&format=" + encodeDownloadParam(format);
        }
        return url;
    }

    @Override
    public boolean isHealthy() {
        return llmService.isAvailable();
    }

    private PptDeckPlan parsePlanJson(String rawJson) {
        String jsonBlock = extractJsonBlock(rawJson);
        if (!hasText(jsonBlock)) {
            return null;
        }
        try {
            return objectMapper.readValue(jsonBlock, PptDeckPlan.class);
        } catch (Exception e) {
            log.warn("Parse ppt plan json failed: {}", e.getMessage());
            return null;
        }
    }

    private String extractJsonBlock(String value) {
        if (!hasText(value)) {
            return "";
        }
        String trimmed = value.trim();
        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return "";
        }
        return trimmed.substring(start, end + 1);
    }

    private PptDeckPlan normalizePlan(PptDeckPlan plan,
                                      PptGenerateRequest request,
                                      PptTemplate template,
                                      String planningMarkdown) {
        PptDeckPlan basePlan = plan;
        if (basePlan == null || basePlan.getSlides() == null || basePlan.getSlides().isEmpty()) {
            basePlan = PptDeckPlan.builder()
                    .title(valueOrDefault(request.getTitle(), "PPT"))
                    .subtitle(buildDeckSubtitle(request))
                    .slideCount(4)
                    .themeName(valueOrDefault(request.getStyle(), "简洁课堂"))
                    .style(valueOrDefault(request.getStyle(), "简洁课堂"))
                    .slides(buildFallbackSlides(request))
                    .build();
        }

        List<PptSlidePlan> slides = new ArrayList<>();
        int index = 1;
        if (basePlan != null && basePlan.getSlides() != null) {
            for (PptSlidePlan slide : basePlan.getSlides()) {
                if (slide == null) {
                    continue;
                }

                String title = hasText(slide.getTitle()) ? slide.getTitle().trim() : "第 " + index + " 页";
                String layout = normalizeLayout(slide.getLayout(), index);
                String slotLayout = normalizeSlotLayout(slide.getSlotLayout(), layout);
                List<String> bullets = normalizeBullets(slide.getBullets(), slotLayout);

                if (bullets.isEmpty() && !"cover".equals(layout)) {
                    bullets = List.of("围绕本页主题进行讲解", "突出关键结论与课堂提示");
                }
                if ("cover".equals(layout)) {
                    bullets = List.of();
                }

                slides.add(PptSlidePlan.builder()
                        .slideNo(index)
                        .title(title)
                        .layout(layout)
                        .slotLayout(slotLayout)
                        .assetType(normalizeAssetType(slide.getAssetType()))
                        .assetKeywords(normalizeAssetKeywords(slide.getAssetKeywords(), slide.getVisualFocus(), title))
                        .bullets(bullets)
                        .speakerNotes(truncate(valueOrEmpty(slide.getSpeakerNotes()), 80))
                        .visualFocus(truncate(valueOrEmpty(slide.getVisualFocus()), 40))
                        .build());
                index++;
            }
        }

        if (slides.isEmpty()) {
            slides = buildFallbackSlides(request);
        }

        if (!"cover".equalsIgnoreCase(valueOrEmpty(slides.get(0).getLayout()))) {
            slides.add(0, buildCoverSlide(request));
            resequenceSlides(slides);
        }
        if (!"summary".equalsIgnoreCase(valueOrEmpty(slides.get(slides.size() - 1).getLayout()))) {
            slides.add(buildSummarySlide(request));
            resequenceSlides(slides);
        }
        if (slides.size() > MAX_PPT_SLIDES) {
            slides = new ArrayList<>(slides.subList(0, MAX_PPT_SLIDES));
            resequenceSlides(slides);
            if (!"summary".equalsIgnoreCase(valueOrEmpty(slides.get(slides.size() - 1).getLayout()))) {
                slides.set(slides.size() - 1, buildSummarySlide(request));
                slides.get(slides.size() - 1).setSlideNo(slides.size());
            }
        }

        String title = hasText(basePlan != null ? basePlan.getTitle() : null)
                ? basePlan.getTitle()
                : valueOrDefault(request.getTitle(), "PPT");
        String subtitle = hasText(basePlan != null ? basePlan.getSubtitle() : null)
                ? basePlan.getSubtitle()
                : buildDeckSubtitle(request);
        String themeName = hasText(basePlan != null ? basePlan.getThemeName() : null)
                ? basePlan.getThemeName()
                : (template != null && hasText(template.getName()) ? template.getName() : valueOrDefault(request.getStyle(), "简洁课堂"));
        String style = hasText(basePlan != null ? basePlan.getStyle() : null)
                ? basePlan.getStyle()
                : valueOrDefault(request.getStyle(), "简洁课堂");

        return PptDeckPlan.builder()
                .title(title)
                .subtitle(subtitle)
                .slideCount(slides.size())
                .themeName(themeName)
                .style(style)
                .slides(slides)
                .build();
    }

    private String normalizeLayout(String layout, int index) {
        String normalized = valueOrEmpty(layout).trim().toLowerCase(Locale.ROOT);
        if (List.of("cover", "content", "summary").contains(normalized)) {
            return normalized;
        }
        return index == 1 ? "cover" : "content";
    }

    private List<PptSlidePlan> buildFallbackSlides(PptGenerateRequest request) {
        List<PptSlidePlan> slides = new ArrayList<>();
        slides.add(buildCoverSlide(request));

        slides.add(PptSlidePlan.builder()
                .slideNo(2)
                .title("学习目标")
                .layout("content")
                .slotLayout("bullet_list")
                .assetType("icon")
                .assetKeywords(List.of("目标"))
                .bullets(List.of("明确本节学习目标", "说明核心知识要点", "建立整体认知框架"))
                .speakerNotes("先说明本节课要解决什么问题。")
                .visualFocus("目标列表")
                .build());

        List<String> knowledgePoints = normalizeBullets(request.getKnowledgePoints());
        slides.add(PptSlidePlan.builder()
                .slideNo(3)
                .title("核心内容")
                .layout("content")
                .slotLayout("image_right")
                .assetType("icon")
                .assetKeywords(List.of("知识"))
                .bullets(knowledgePoints.isEmpty()
                        ? List.of("提炼最关键的知识点", "结合示例解释结论", "突出课堂重点路径")
                        : knowledgePoints)
                .speakerNotes("围绕重点展开讲解，避免堆砌信息。")
                .visualFocus("主视觉 + 关键词高亮")
                .build());

        slides.add(buildSummarySlide(request));
        return slides;
    }

    private PptSlidePlan buildCoverSlide(PptGenerateRequest request) {
        return PptSlidePlan.builder()
                .slideNo(1)
                .title(valueOrDefault(request.getTitle(), "课程主题"))
                .layout("cover")
                .slotLayout("title_only")
                .assetType("none")
                .assetKeywords(List.of())
                .bullets(List.of())
                .speakerNotes(buildDeckSubtitle(request))
                .visualFocus("封面标题 + 教学主题")
                .build();
    }

    private PptSlidePlan buildSummarySlide(PptGenerateRequest request) {
        return PptSlidePlan.builder()
                .slideNo(999)
                .title("课堂总结")
                .layout("summary")
                .slotLayout("bullet_list")
                .assetType("icon")
                .assetKeywords(List.of("总结"))
                .bullets(List.of("回顾核心结论", "提炼方法与思路", "课后延伸思考"))
                .speakerNotes("用简洁方式收束课堂内容。")
                .visualFocus("总结卡片 + 课后任务")
                .build();
    }

    private void resequenceSlides(List<PptSlidePlan> slides) {
        for (int i = 0; i < slides.size(); i++) {
            slides.get(i).setSlideNo(i + 1);
        }
    }

    private List<String> normalizeBullets(List<String> bullets) {
        return normalizeBullets(bullets, "bullet_list");
    }

    /**
     * Normalize bullets. For most slot layouts, each bullet is hard-capped to {@link #MAX_BULLET_CHARS}
     * Chinese chars; for {@code code} slot the FIRST bullet preserves line breaks (it carries a snippet)
     * and is line-wise capped to {@link #MAX_CODE_LINE_CHARS}.
     */
    private List<String> normalizeBullets(List<String> bullets, String slotLayout) {
        if (bullets == null || bullets.isEmpty()) {
            return List.of();
        }
        boolean isCode = "code".equalsIgnoreCase(valueOrEmpty(slotLayout));

        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        boolean isFirst = true;
        for (String bullet : bullets) {
            String raw = valueOrEmpty(bullet);
            String value;
            if (isCode && isFirst && raw.contains("\n")) {
                value = cleanupCodeBlock(raw);
            } else {
                value = capLength(cleanupBullet(raw), MAX_BULLET_CHARS);
            }
            if (hasText(value)) {
                normalized.add(value);
            }
            isFirst = false;
            if (normalized.size() >= MAX_PPT_BULLETS) {
                break;
            }
        }
        return new ArrayList<>(normalized);
    }

    /**
     * 严格规整 slotLayout 字段；非法值按 layout 给出合理默认。
     */
    private String normalizeSlotLayout(String slotLayout, String layout) {
        String normalized = valueOrEmpty(slotLayout).trim().toLowerCase(Locale.ROOT);
        List<String> allowed = List.of("title_only", "bullet_list", "two_column",
                "image_right", "quote", "code", "comparison", "chart");
        if (allowed.contains(normalized)) {
            return normalized;
        }
        return "cover".equalsIgnoreCase(layout) ? "title_only" : "bullet_list";
    }

    private String normalizeAssetType(String assetType) {
        String normalized = valueOrEmpty(assetType).trim().toLowerCase(Locale.ROOT);
        return List.of("icon", "photo", "chart", "diagram", "none").contains(normalized)
                ? normalized
                : "icon";
    }

    private List<String> normalizeAssetKeywords(List<String> keywords, String visualFocus, String title) {
        List<String> result = new ArrayList<>();
        if (keywords != null) {
            for (String keyword : keywords) {
                String trimmed = valueOrEmpty(keyword).trim();
                if (hasText(trimmed) && trimmed.length() <= 8) {
                    result.add(trimmed);
                }
                if (result.size() >= 3) {
                    break;
                }
            }
        }
        if (result.isEmpty() && hasText(visualFocus)) {
            result.add(truncate(visualFocus, 8));
        }
        if (result.isEmpty() && hasText(title)) {
            result.add(truncate(title, 8));
        }
        return result;
    }

    /**
     * 按字符数截断；中英文等宽处理。
     */
    private String capLength(String value, int maxChars) {
        if (!hasText(value)) {
            return "";
        }
        if (value.length() <= maxChars) {
            return value;
        }
        return value.substring(0, maxChars - 1) + "…";
    }

    /**
     * 按行裁切代码片段，每行不超过 {@link #MAX_CODE_LINE_CHARS}，最多 8 行。
     */
    private String cleanupCodeBlock(String value) {
        if (!hasText(value)) {
            return "";
        }
        String[] lines = value.split("\\R");
        List<String> kept = new ArrayList<>();
        for (String line : lines) {
            String trimmed = line.replaceAll("\\s+$", "");
            if (trimmed.length() > MAX_CODE_LINE_CHARS) {
                trimmed = trimmed.substring(0, MAX_CODE_LINE_CHARS - 1) + "…";
            }
            kept.add(trimmed);
            if (kept.size() >= 8) {
                break;
            }
        }
        return String.join("\n", kept);
    }

    private byte[] buildRealPptx(PptDeckPlan plan, PptGenerateRequest request, PptTemplate template) throws Exception {
        // 优先：用管理员上传的 pptx 渲染
        XMLSlideShow templateShow = loadTemplatePresentation(template);
        if (templateShow != null) {
            log.info("Render path: user-uploaded template pptx ({})", template.getTemplateFilePath());
            return buildTemplatedPptx(templateShow, plan);
        }

        // 教师未选模板时的兜底：用 jar 内嵌 default.pptx
        try (var is = getClass().getResourceAsStream("/templates/ppt/default.pptx")) {
            if (is != null) {
                log.info("Render path: embedded default.pptx (no template selected)");
                XMLSlideShow defaultShow = new XMLSlideShow(is);
                return buildTemplatedPptx(defaultShow, plan);
            }
        } catch (Exception e) {
            log.warn("Failed to load default template, falling back to programmatic slides", e);
        }

        // 最终兜底：完全代码画。仅在 default.pptx 都加载失败时用到。
        log.info("Render path: programmatic fallback (default.pptx unavailable)");
        return buildFallbackPptx(plan, request, template);
    }

    private XMLSlideShow loadTemplatePresentation(PptTemplate template) {
        if (template == null || !hasText(template.getTemplateFilePath())) {
            return null;
        }
        try {
            Path path = resolveTemplatePath(template.getTemplateFilePath());
            if (Files.exists(path)) {
                return new XMLSlideShow(Files.newInputStream(path));
            }
        } catch (Exception e) {
            log.warn("Failed to load template file: {}", template.getTemplateFilePath(), e);
        }
        return null;
    }

    private Path resolveTemplatePath(String relativePath) {
        Path storageRoot = Paths.get(localStoragePath).toAbsolutePath().normalize().resolve("templates");
        return storageRoot.resolve(relativePath).normalize();
    }

    /**
     * Apply the template by directly creating new slides on the template show using
     * {@link XMLSlideShow#createSlide(org.apache.poi.xslf.usermodel.XSLFSlideLayout)}.
     * This preserves master/theme/font/color from the template (the previous shape-by-shape
     * copy approach lost all of that).
     *
     * <p>Layout matching strategy:
     * <ol>
     *   <li>Read all available {@code XSLFSlideLayout} from the template's slide masters.</li>
     *   <li>For each plan slide, pick the best layout: cover→TITLE/CENTER, summary→TITLE/SECT,
     *       content→TITLE_AND_CONTENT (preferred) or TWO_COLUMN_TEXT for two_column slot.</li>
     *   <li>Fill the layout's placeholders by type ({@code TITLE}, {@code BODY}, {@code CTR_TITLE},
     *       {@code SUBTITLE}). Naive {@code {{TITLE}}} placeholders are still replaced as a fallback.</li>
     *   <li>Drop the template's example slides (they were design samples, not real content).</li>
     * </ol>
     */
    private byte[] buildTemplatedPptx(XMLSlideShow templateShow, PptDeckPlan plan) throws Exception {
        // Drop existing example slides; we want only our generated slides bound to the template's masters.
        while (!templateShow.getSlides().isEmpty()) {
            templateShow.removeSlide(0);
        }

        List<org.apache.poi.xslf.usermodel.XSLFSlideMaster> masters = templateShow.getSlideMasters();
        Map<String, org.apache.poi.xslf.usermodel.XSLFSlideLayout> layoutByType = collectLayouts(masters);

        for (PptSlidePlan slidePlan : plan.getSlides()) {
            org.apache.poi.xslf.usermodel.XSLFSlideLayout layout = pickLayout(layoutByType, slidePlan);
            XSLFSlide newSlide = layout != null
                    ? templateShow.createSlide(layout)
                    : templateShow.createSlide();
            fillTemplatedSlide(newSlide, plan, slidePlan);
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        templateShow.write(baos);
        templateShow.close();
        return baos.toByteArray();
    }

    private Map<String, org.apache.poi.xslf.usermodel.XSLFSlideLayout> collectLayouts(
            List<org.apache.poi.xslf.usermodel.XSLFSlideMaster> masters) {
        Map<String, org.apache.poi.xslf.usermodel.XSLFSlideLayout> map = new LinkedHashMap<>();
        for (org.apache.poi.xslf.usermodel.XSLFSlideMaster master : masters) {
            for (org.apache.poi.xslf.usermodel.XSLFSlideLayout layout : master.getSlideLayouts()) {
                String key = layout.getType() != null ? layout.getType().name() : layout.getName();
                if (key != null && !map.containsKey(key)) {
                    map.put(key, layout);
                }
            }
        }
        return map;
    }

    private org.apache.poi.xslf.usermodel.XSLFSlideLayout pickLayout(
            Map<String, org.apache.poi.xslf.usermodel.XSLFSlideLayout> layoutByType,
            PptSlidePlan slidePlan) {
        if (layoutByType.isEmpty()) {
            return null;
        }
        String layout = valueOrEmpty(slidePlan.getLayout()).toLowerCase(Locale.ROOT);
        String slot = valueOrEmpty(slidePlan.getSlotLayout()).toLowerCase(Locale.ROOT);

        List<String> preferences = new ArrayList<>();
        if ("cover".equals(layout)) {
            preferences.add("TITLE");
            preferences.add("CENTERED_TITLE");
            preferences.add("TITLE_SLIDE");
        } else if ("summary".equals(layout)) {
            preferences.add("SECTION_HEADER");
            preferences.add("TITLE_AND_CONTENT");
            preferences.add("TITLE");
        } else if ("two_column".equals(slot) || "comparison".equals(slot)) {
            preferences.add("TWO_OBJ");
            preferences.add("TWO_TX_TWO_OBJ");
            preferences.add("TX_AND_OBJ");
            preferences.add("TWO_COL_TX");
            preferences.add("TITLE_AND_CONTENT");
        } else {
            preferences.add("TITLE_AND_CONTENT");
            preferences.add("OBJ");
            preferences.add("TX");
        }

        for (String pref : preferences) {
            if (layoutByType.containsKey(pref)) {
                return layoutByType.get(pref);
            }
        }
        return layoutByType.values().iterator().next();
    }

    private void fillTemplatedSlide(XSLFSlide slide, PptDeckPlan plan, PptSlidePlan slidePlan) {
        String title = valueOrDefault(slidePlan.getTitle(), valueOrDefault(plan.getTitle(), "课程主题"));
        String subtitle = valueOrDefault(plan.getSubtitle(), buildSubtitleFromSlide(slidePlan));
        String bulletsText = slidePlan.getBullets() != null
                ? String.join("\n", slidePlan.getBullets()) : "";

        boolean filledTitle = false;
        boolean filledBody = false;
        boolean filledSubtitle = false;

        for (XSLFShape shape : slide.getShapes()) {
            if (!(shape instanceof org.apache.poi.xslf.usermodel.XSLFTextShape textShape)) {
                continue;
            }
            org.apache.poi.sl.usermodel.Placeholder placeholder = textShape.getTextType();
            if (placeholder == null) {
                continue;
            }
            switch (placeholder) {
                case TITLE, CENTERED_TITLE -> {
                    setShapeText(textShape, title);
                    filledTitle = true;
                }
                case SUBTITLE -> {
                    setShapeText(textShape, subtitle);
                    filledSubtitle = true;
                }
                case BODY, CONTENT -> {
                    setShapeText(textShape, bulletsText);
                    filledBody = true;
                }
                default -> {
                    // leave header/footer/slide-number placeholders alone
                }
            }
        }

        // Fallback: legacy {{TITLE}}/{{BULLETS}}/{{SUBTITLE}} placeholder replacement
        replaceTextInSlide(slide, "{{TITLE}}", title);
        replaceTextInSlide(slide, "{{SUBTITLE}}", subtitle);
        replaceTextInSlide(slide, "{{BULLETS}}", bulletsText);
        String notesText = (hasText(slidePlan.getSpeakerNotes()) ? slidePlan.getSpeakerNotes() : "")
                + (hasText(slidePlan.getVisualFocus()) ? "  视觉提示：" + slidePlan.getVisualFocus() : "");
        replaceTextInSlide(slide, "{{NOTES}}", notesText);

        // If the layout has no recognizable title/body placeholder at all, append a text box
        // so content is never silently lost.
        if (!filledTitle && !filledBody && !filledSubtitle) {
            createTextBox(slide, new Rectangle(60, 60, 840, 60), title,
                    new Color(15, 23, 42), 26, true,
                    org.apache.poi.sl.usermodel.TextParagraph.TextAlign.LEFT, true);
            if (hasText(bulletsText)) {
                createBulletBox(slide, new Rectangle(60, 140, 840, 380),
                        slidePlan.getBullets(), new Color(51, 65, 85), 18);
            }
        }
    }

    private void setShapeText(org.apache.poi.xslf.usermodel.XSLFTextShape shape, String text) {
        if (shape == null) {
            return;
        }
        shape.clearText();
        if (!hasText(text)) {
            return;
        }
        for (String line : text.split("\\R")) {
            org.apache.poi.xslf.usermodel.XSLFTextParagraph paragraph = shape.addNewTextParagraph();
            org.apache.poi.xslf.usermodel.XSLFTextRun run = paragraph.addNewTextRun();
            run.setText(line);
        }
    }

    private String buildSubtitleFromSlide(PptSlidePlan slidePlan) {
        if (hasText(slidePlan.getSpeakerNotes())) {
            return slidePlan.getSpeakerNotes();
        }
        if (hasText(slidePlan.getVisualFocus())) {
            return slidePlan.getVisualFocus();
        }
        return "";
    }

    private void replaceTextInSlide(XSLFSlide slide, String placeholder, String replacement) {
        for (XSLFShape shape : slide.getShapes()) {
            if (shape instanceof XSLFTextBox textBox) {
                for (XSLFTextParagraph para : textBox.getTextParagraphs()) {
                    for (XSLFTextRun run : para.getTextRuns()) {
                        if (run.getRawText().contains(placeholder)) {
                            run.setText(run.getRawText().replace(placeholder, replacement));
                        }
                    }
                }
            } else if (shape instanceof XSLFAutoShape autoShape) {
                for (XSLFTextParagraph para : autoShape.getTextParagraphs()) {
                    for (XSLFTextRun run : para.getTextRuns()) {
                        if (run.getRawText().contains(placeholder)) {
                            run.setText(run.getRawText().replace(placeholder, replacement));
                        }
                    }
                }
            }
        }
    }

    private byte[] buildFallbackPptx(PptDeckPlan plan,
                                     PptGenerateRequest request,
                                     PptTemplate template) throws Exception {
        XMLSlideShow slideShow = new XMLSlideShow();
        slideShow.setPageSize(new Dimension(960, 540));
        ThemeSpec theme = resolveThemeSpec(request, template);

        for (PptSlidePlan slidePlan : plan.getSlides()) {
            XSLFSlide slide = slideShow.createSlide();
            if ("cover".equalsIgnoreCase(slidePlan.getLayout())) {
                renderCoverSlide(slide, plan, slidePlan, theme);
            } else {
                renderContentSlide(slide, slidePlan, theme, plan.getSlides().size());
            }
        }

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        slideShow.write(output);
        slideShow.close();
        return output.toByteArray();
    }

    private void renderCoverSlide(XSLFSlide slide, PptDeckPlan plan, PptSlidePlan slidePlan, ThemeSpec theme) {
        createBackground(slide, new Rectangle(0, 0, 960, 540), theme.coverBackground());
        createPill(slide, new Rectangle(60, 48, 158, 34), valueOrDefault(plan.getThemeName(), "PPT"),
                theme.coverPill(), theme.coverText(), 13, true);
        createTextBox(slide, new Rectangle(60, 128, 780, 138),
                valueOrDefault(slidePlan.getTitle(), valueOrDefault(plan.getTitle(), "课程主题")),
                theme.coverText(), 30, true, TextParagraph.TextAlign.LEFT, true);
        createTextBox(slide, new Rectangle(64, 286, 740, 72),
                valueOrDefault(plan.getSubtitle(), valueOrDefault(slidePlan.getSpeakerNotes(), "")),
                theme.coverTextMuted(), 16, false, TextParagraph.TextAlign.LEFT, true);

        createAccentBlock(slide, new Rectangle(60, 392, 250, 88), theme.coverCard(), theme.coverText(),
                valueOrDefault(slidePlan.getVisualFocus(), "课程封面"), "视觉提示");
        createAccentBlock(slide, new Rectangle(330, 392, 250, 88), theme.coverCard(), theme.coverText(),
                "共 " + plan.getSlideCount() + " 页", "页面规模");
        createAccentBlock(slide, new Rectangle(600, 392, 300, 88), theme.coverCard(), theme.coverText(),
                valueOrDefault(plan.getStyle(), "简洁课堂"), "呈现风格");
    }

    private void renderContentSlide(XSLFSlide slide, PptSlidePlan slidePlan, ThemeSpec theme, int totalSlides) {
        createBackground(slide, new Rectangle(0, 0, 960, 540), theme.surfaceBackground());
        createBackground(slide, new Rectangle(0, 0, 960, 12), theme.accent());

        createTextBox(slide, new Rectangle(54, 32, 740, 42),
                valueOrDefault(slidePlan.getTitle(), "页面内容"),
                theme.titleText(), 24, true, TextParagraph.TextAlign.LEFT, false);
        createTextBox(slide, new Rectangle(820, 30, 90, 30),
                slidePlan.getSlideNo() + " / " + totalSlides,
                theme.mutedText(), 12, false, TextParagraph.TextAlign.RIGHT, false);

        // Title underline accent
        createBackground(slide, new Rectangle(54, 76, 60, 4), theme.accent());

        // Dispatch by slotLayout —— 不同版式走不同布局，避免千篇一律。
        String slot = valueOrEmpty(slidePlan.getSlotLayout()).toLowerCase(Locale.ROOT);
        switch (slot) {
            case "two_column", "comparison" -> renderTwoColumnBody(slide, slidePlan, theme);
            case "image_right" -> renderImageRightBody(slide, slidePlan, theme);
            case "quote" -> renderQuoteBody(slide, slidePlan, theme);
            case "code" -> renderCodeBody(slide, slidePlan, theme);
            case "chart" -> renderChartBody(slide, slidePlan, theme);
            default -> renderBulletListBody(slide, slidePlan, theme);
        }

        if ("summary".equalsIgnoreCase(slidePlan.getLayout())) {
            createPill(slide, new Rectangle(50, 488, 128, 28), "课堂总结",
                    theme.accentSoft(), theme.accent(), 12, true);
        }
    }

    private void renderBulletListBody(XSLFSlide slide, PptSlidePlan slidePlan, ThemeSpec theme) {
        createRoundedPanel(slide, new Rectangle(50, 100, 590, 380), theme.contentPanel(), theme.panelBorder());
        createBulletBox(slide, new Rectangle(74, 124, 540, 340),
                slidePlan.getBullets(), theme.bodyText(), 18);
        renderSidePanel(slide, slidePlan, theme, true);
    }

    private void renderImageRightBody(XSLFSlide slide, PptSlidePlan slidePlan, ThemeSpec theme) {
        createRoundedPanel(slide, new Rectangle(50, 100, 540, 380), theme.contentPanel(), theme.panelBorder());
        createBulletBox(slide, new Rectangle(74, 124, 490, 340),
                slidePlan.getBullets(), theme.bodyText(), 18);
        // Right side: large icon card (image surrogate)
        createRoundedPanel(slide, new Rectangle(610, 100, 300, 380), theme.sidePanel(), theme.panelBorder());
        renderIconCard(slide, new Rectangle(610, 100, 300, 380), slidePlan, theme);
    }

    private void renderTwoColumnBody(XSLFSlide slide, PptSlidePlan slidePlan, ThemeSpec theme) {
        List<String> bullets = slidePlan.getBullets() == null ? List.of() : slidePlan.getBullets();
        List<String> leftBullets = new ArrayList<>();
        List<String> rightBullets = new ArrayList<>();
        String leftTitle = "左";
        String rightTitle = "右";
        boolean splitByPrefix = bullets.stream().anyMatch(b -> startsWithSplitPrefix(valueOrEmpty(b)));

        if (splitByPrefix) {
            for (String bullet : bullets) {
                String text = valueOrEmpty(bullet);
                if (text.startsWith("左：") || text.startsWith("左:") || text.startsWith("优：") || text.startsWith("优:")) {
                    leftTitle = text.startsWith("优") ? "优势" : "左";
                    leftBullets.add(text.substring(2).trim());
                } else if (text.startsWith("右：") || text.startsWith("右:") || text.startsWith("劣：") || text.startsWith("劣:")) {
                    rightTitle = text.startsWith("劣") ? "劣势" : "右";
                    rightBullets.add(text.substring(2).trim());
                } else {
                    (leftBullets.size() <= rightBullets.size() ? leftBullets : rightBullets).add(text);
                }
            }
        } else {
            int half = (bullets.size() + 1) / 2;
            leftBullets.addAll(bullets.subList(0, Math.min(half, bullets.size())));
            rightBullets.addAll(bullets.subList(Math.min(half, bullets.size()), bullets.size()));
        }

        // Left card
        createRoundedPanel(slide, new Rectangle(50, 100, 420, 380), theme.contentPanel(), theme.panelBorder());
        createTextBox(slide, new Rectangle(74, 116, 380, 28),
                leftTitle, theme.accent(), 14, true, TextParagraph.TextAlign.LEFT, false);
        createBulletBox(slide, new Rectangle(74, 150, 380, 314), leftBullets, theme.bodyText(), 16);

        // Right card
        createRoundedPanel(slide, new Rectangle(490, 100, 420, 380), theme.contentPanel(), theme.panelBorder());
        createTextBox(slide, new Rectangle(514, 116, 380, 28),
                rightTitle, theme.accent(), 14, true, TextParagraph.TextAlign.LEFT, false);
        createBulletBox(slide, new Rectangle(514, 150, 380, 314), rightBullets, theme.bodyText(), 16);
    }

    private boolean startsWithSplitPrefix(String text) {
        return text.startsWith("左：") || text.startsWith("左:")
                || text.startsWith("右：") || text.startsWith("右:")
                || text.startsWith("优：") || text.startsWith("优:")
                || text.startsWith("劣：") || text.startsWith("劣:");
    }

    private void renderQuoteBody(XSLFSlide slide, PptSlidePlan slidePlan, ThemeSpec theme) {
        createRoundedPanel(slide, new Rectangle(80, 130, 800, 320), theme.contentPanel(), theme.panelBorder());
        // Big quotation mark
        createTextBox(slide, new Rectangle(110, 140, 80, 80),
                "“", theme.accent(), 56, true, TextParagraph.TextAlign.LEFT, false);
        List<String> bullets = slidePlan.getBullets() == null ? List.of() : slidePlan.getBullets();
        String main = bullets.isEmpty() ? valueOrEmpty(slidePlan.getVisualFocus()) : bullets.get(0);
        createTextBox(slide, new Rectangle(180, 200, 660, 130),
                main, theme.titleText(), 22, false, TextParagraph.TextAlign.LEFT, true);
        if (bullets.size() > 1) {
            createTextBox(slide, new Rectangle(180, 350, 660, 60),
                    "—— " + bullets.get(1), theme.mutedText(), 14, false,
                    TextParagraph.TextAlign.LEFT, true);
        }
    }

    private void renderCodeBody(XSLFSlide slide, PptSlidePlan slidePlan, ThemeSpec theme) {
        List<String> bullets = slidePlan.getBullets() == null ? List.of() : slidePlan.getBullets();
        String code = bullets.isEmpty() ? "" : bullets.get(0);
        // Dark code panel
        Color codeBg = new Color(30, 41, 59);
        Color codeText = new Color(226, 232, 240);
        Color codeAccent = new Color(96, 165, 250);
        createRoundedPanel(slide, new Rectangle(50, 100, 600, 380), codeBg, codeBg);
        // Window dots
        createPill(slide, new Rectangle(70, 116, 14, 14), "", new Color(248, 113, 113), codeBg, 8, false);
        createPill(slide, new Rectangle(90, 116, 14, 14), "", new Color(250, 204, 21), codeBg, 8, false);
        createPill(slide, new Rectangle(110, 116, 14, 14), "", new Color(74, 222, 128), codeBg, 8, false);

        XSLFTextBox codeBox = slide.createTextBox();
        codeBox.setAnchor(new Rectangle(70, 144, 560, 320));
        codeBox.setTextAutofit(TextShape.TextAutofit.SHAPE);
        codeBox.setWordWrap(false);
        for (String line : code.split("\\R")) {
            XSLFTextParagraph paragraph = codeBox.addNewTextParagraph();
            paragraph.setSpaceAfter(2d);
            XSLFTextRun run = paragraph.addNewTextRun();
            run.setText(line);
            run.setFontColor(codeText);
            run.setFontSize(14d);
            run.setFontFamily("Consolas");
        }

        // Right panel: commentary
        createRoundedPanel(slide, new Rectangle(670, 100, 240, 380), theme.contentPanel(), theme.panelBorder());
        createTextBox(slide, new Rectangle(686, 116, 220, 28),
                "代码说明", codeAccent, 14, true, TextParagraph.TextAlign.LEFT, false);
        List<String> commentary = bullets.size() > 1 ? bullets.subList(1, bullets.size()) : List.of();
        createBulletBox(slide, new Rectangle(686, 150, 220, 320), commentary, theme.bodyText(), 14);
    }

    private void renderChartBody(XSLFSlide slide, PptSlidePlan slidePlan, ThemeSpec theme) {
        // Without external chart libs, draw a simple stylized "bar" cluster as a decorative chart placeholder
        createRoundedPanel(slide, new Rectangle(50, 100, 540, 380), theme.contentPanel(), theme.panelBorder());
        int chartTop = 200;
        int chartBottom = 440;
        int[] heights = {110, 170, 80, 200, 140};
        for (int i = 0; i < heights.length; i++) {
            int x = 90 + i * 90;
            int barTop = chartBottom - heights[i];
            createBackground(slide, new Rectangle(x, barTop, 56, heights[i]), theme.accent());
        }
        createBackground(slide, new Rectangle(80, chartBottom, 480, 2), theme.panelBorder());
        createTextBox(slide, new Rectangle(74, 124, 500, 28),
                "数据视图（示意）", theme.accent(), 14, true, TextParagraph.TextAlign.LEFT, false);

        // Right: bullets describing the data
        createRoundedPanel(slide, new Rectangle(610, 100, 300, 380), theme.sidePanel(), theme.panelBorder());
        createBulletBox(slide, new Rectangle(630, 130, 270, 330),
                slidePlan.getBullets(), theme.bodyText(), 14);
    }

    private void renderSidePanel(XSLFSlide slide, PptSlidePlan slidePlan, ThemeSpec theme, boolean withIcon) {
        createRoundedPanel(slide, new Rectangle(660, 100, 250, 380), theme.sidePanel(), theme.panelBorder());
        if (withIcon) {
            renderIconCard(slide, new Rectangle(660, 100, 250, 220), slidePlan, theme);
        }
        // Note section
        List<String> notes = new ArrayList<>();
        if (hasText(slidePlan.getSpeakerNotes())) {
            notes.add(slidePlan.getSpeakerNotes());
        }
        if (notes.isEmpty() && hasText(slidePlan.getVisualFocus())) {
            notes.add(slidePlan.getVisualFocus());
        }
        if (notes.isEmpty()) {
            notes.add("围绕本页主题进行重点讲解。");
        }
        createTextBox(slide, new Rectangle(680, withIcon ? 330 : 130, 220, 28),
                "讲解提示", theme.accent(), 13, true, TextParagraph.TextAlign.LEFT, false);
        XSLFTextBox box = slide.createTextBox();
        box.setAnchor(new Rectangle(680, withIcon ? 360 : 160, 220, withIcon ? 110 : 280));
        box.setTextAutofit(TextShape.TextAutofit.SHAPE);
        box.setWordWrap(true);
        for (String note : notes) {
            XSLFTextParagraph paragraph = box.addNewTextParagraph();
            paragraph.setSpaceAfter(8d);
            XSLFTextRun run = paragraph.addNewTextRun();
            run.setText(valueOrEmpty(note));
            run.setFontColor(theme.bodyText());
            run.setFontSize(12.5);
            run.setFontFamily("Microsoft YaHei");
        }
    }

    private void renderIconCard(XSLFSlide slide, Rectangle anchor, PptSlidePlan slidePlan, ThemeSpec theme) {
        // 图标查找：依次尝试 assetKeywords -> visualFocus -> title
        List<String> candidates = new ArrayList<>();
        if (slidePlan.getAssetKeywords() != null) {
            candidates.addAll(slidePlan.getAssetKeywords());
        }
        candidates.add(valueOrEmpty(slidePlan.getVisualFocus()));
        candidates.add(valueOrEmpty(slidePlan.getTitle()));

        String icon = PptIconCatalog.resolveIcon(candidates);

        // Big icon centered
        int iconSize = 90;
        int iconY = anchor.y + 40;
        XSLFTextBox iconBox = slide.createTextBox();
        iconBox.setAnchor(new Rectangle(anchor.x, iconY, anchor.width, iconSize));
        iconBox.setTextAutofit(TextShape.TextAutofit.SHAPE);
        iconBox.setWordWrap(false);
        XSLFTextParagraph iconPara = iconBox.addNewTextParagraph();
        iconPara.setTextAlign(TextParagraph.TextAlign.CENTER);
        XSLFTextRun iconRun = iconPara.addNewTextRun();
        iconRun.setText(icon);
        iconRun.setFontSize(72d);
        iconRun.setFontFamily("Segoe UI Emoji");

        // Keyword caption beneath the icon
        String caption = !candidates.isEmpty() && hasText(candidates.get(0))
                ? candidates.get(0)
                : valueOrEmpty(slidePlan.getTitle());
        if (hasText(caption)) {
            createTextBox(slide,
                    new Rectangle(anchor.x + 10, iconY + iconSize + 16, anchor.width - 20, 28),
                    caption, theme.titleText(), 14, true,
                    TextParagraph.TextAlign.CENTER, false);
        }
    }

    private void createBackground(XSLFSlide slide, Rectangle anchor, Color color) {
        XSLFAutoShape shape = slide.createAutoShape();
        shape.setShapeType(ShapeType.RECT);
        shape.setAnchor(anchor);
        shape.setLineColor(color);
        shape.setFillColor(color);
    }

    private void createRoundedPanel(XSLFSlide slide, Rectangle anchor, Color fill, Color border) {
        XSLFAutoShape shape = slide.createAutoShape();
        shape.setShapeType(ShapeType.ROUND_RECT);
        shape.setAnchor(anchor);
        shape.setFillColor(fill);
        shape.setLineColor(border);
        shape.setLineWidth(1.1);
    }

    private void createPill(XSLFSlide slide,
                            Rectangle anchor,
                            String text,
                            Color fill,
                            Color textColor,
                            double fontSize,
                            boolean bold) {
        XSLFAutoShape shape = slide.createAutoShape();
        shape.setShapeType(ShapeType.ROUND_RECT);
        shape.setAnchor(anchor);
        shape.setFillColor(fill);
        shape.setLineColor(fill);
        shape.setVerticalAlignment(VerticalAlignment.MIDDLE);

        XSLFTextParagraph paragraph = shape.addNewTextParagraph();
        paragraph.setTextAlign(TextParagraph.TextAlign.CENTER);
        XSLFTextRun run = paragraph.addNewTextRun();
        run.setText(valueOrEmpty(text));
        run.setFontColor(textColor);
        run.setFontSize(fontSize);
        run.setBold(bold);
        run.setFontFamily("Microsoft YaHei");
    }

    private void createTextBox(XSLFSlide slide,
                               Rectangle anchor,
                               String text,
                               Color color,
                               double fontSize,
                               boolean bold,
                               TextParagraph.TextAlign align,
                               boolean wrap) {
        XSLFTextBox box = slide.createTextBox();
        box.setAnchor(anchor);
        box.setTextAutofit(TextShape.TextAutofit.SHAPE);
        box.setWordWrap(wrap);
        box.setVerticalAlignment(VerticalAlignment.MIDDLE);

        XSLFTextParagraph paragraph = box.addNewTextParagraph();
        paragraph.setTextAlign(align);
        XSLFTextRun run = paragraph.addNewTextRun();
        run.setText(valueOrEmpty(text));
        run.setFontColor(color);
        run.setFontSize(fontSize);
        run.setBold(bold);
        run.setFontFamily("Microsoft YaHei");
    }

    private void createAccentBlock(XSLFSlide slide,
                                   Rectangle anchor,
                                   Color fill,
                                   Color textColor,
                                   String value,
                                   String label) {
        createRoundedPanel(slide, anchor, fill, fill);
        createTextBox(slide, new Rectangle(anchor.x + 16, anchor.y + 12, anchor.width - 32, 16),
                label, textColor, 11, false, TextParagraph.TextAlign.LEFT, false);
        createTextBox(slide, new Rectangle(anchor.x + 16, anchor.y + 30, anchor.width - 32, anchor.height - 36),
                value, textColor, 16, true, TextParagraph.TextAlign.LEFT, true);
    }

    private void createBulletBox(XSLFSlide slide,
                                 Rectangle anchor,
                                 List<String> bullets,
                                 Color textColor,
                                 double fontSize) {
        XSLFTextBox box = slide.createTextBox();
        box.setAnchor(anchor);
        box.setTextAutofit(TextShape.TextAutofit.SHAPE);
        box.setWordWrap(true);

        for (String bullet : normalizeBullets(bullets)) {
            XSLFTextParagraph paragraph = box.addNewTextParagraph();
            paragraph.setBullet(true);
            paragraph.setLeftMargin(24d);
            paragraph.setIndent(-16d);
            paragraph.setSpaceAfter(12d);

            XSLFTextRun run = paragraph.addNewTextRun();
            run.setText(valueOrEmpty(bullet));
            run.setFontColor(textColor);
            run.setFontSize(fontSize);
            run.setFontFamily("Microsoft YaHei");
        }
    }

    private String buildLessonPlanRewritePrompt(String documentContent, String selectedText, String instruction) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Rewrite the selected lesson-plan fragment according to the user's instruction.\n\n");
        prompt.append("Rules:\n");
        prompt.append("1. Keep the same language and teaching context as the source.\n");
        prompt.append("2. Return only the replacement fragment for the selected Markdown selection.\n");
        prompt.append("3. Do not explain your reasoning.\n");
        prompt.append("4. Preserve useful Markdown structure such as headings, lists, emphasis, numbering, and line breaks when appropriate.\n\n");
        prompt.append("User instruction:\n").append(valueOrEmpty(instruction).trim()).append("\n\n");
        prompt.append("Selected Markdown fragment:\n").append(valueOrEmpty(selectedText).trim()).append("\n\n");
        prompt.append("Full Markdown document for context:\n").append(valueOrEmpty(documentContent).trim());
        return prompt.toString();
    }

    private String buildPptRequestBlock(PptGenerateRequest request, PptTemplate template) {
        StringBuilder block = new StringBuilder();
        appendLine(block, "Title", request.getTitle());
        appendLine(block, "Subject", request.getSubject());
        appendLine(block, "Grade", request.getGrade());
        if (request.getKnowledgePoints() != null && !request.getKnowledgePoints().isEmpty()) {
            block.append("Knowledge Points: ").append(String.join(" / ", request.getKnowledgePoints())).append('\n');
        }
        appendLine(block, "Slide Count", request.getSlideCount() == null ? null : String.valueOf(request.getSlideCount()));
        appendLine(block, "Style", request.getStyle());
        appendLine(block, "User Description", request.getUserDescription());
        if (template != null) {
            appendLine(block, "Template Name", template.getName());
            appendLine(block, "Template Hint", template.getPromptHint());
            appendLine(block, "Template Description", template.getDescription());
        }
        appendLine(block, "Reference Text", truncate(request.getReferenceText(), 4_000));
        return block.toString().trim();
    }

    private ThemeSpec resolveThemeSpec(PptGenerateRequest request, PptTemplate template) {
        String style = (valueOrDefault(request.getStyle(), "") + " "
                + valueOrDefault(template != null ? template.getName() : null, ""))
                .toLowerCase(Locale.ROOT);

        ThemeSpec theme;
        if (style.contains("科技") || style.contains("未来") || style.contains("blue")) {
            theme = new ThemeSpec(
                    new Color(30, 64, 175),
                    new Color(37, 99, 235),
                    new Color(219, 234, 254),
                    Color.WHITE,
                    new Color(248, 250, 252),
                    new Color(15, 23, 42),
                    new Color(51, 65, 85),
                    new Color(226, 232, 240)
            );
        } else if (style.contains("清新") || style.contains("自然") || style.contains("green")) {
            theme = new ThemeSpec(
                    new Color(22, 101, 52),
                    new Color(34, 197, 94),
                    new Color(220, 252, 231),
                    Color.WHITE,
                    new Color(250, 253, 250),
                    new Color(20, 83, 45),
                    new Color(55, 65, 81),
                    new Color(220, 252, 231)
            );
        } else if (style.contains("活力") || style.contains("童趣") || style.contains("orange")) {
            theme = new ThemeSpec(
                    new Color(194, 65, 12),
                    new Color(249, 115, 22),
                    new Color(255, 237, 213),
                    Color.WHITE,
                    new Color(255, 251, 245),
                    new Color(124, 45, 18),
                    new Color(68, 64, 60),
                    new Color(255, 237, 213)
            );
        } else {
            theme = new ThemeSpec(
                    new Color(17, 24, 39),
                    new Color(37, 99, 235),
                    new Color(219, 234, 254),
                    Color.WHITE,
                    Color.WHITE,
                    new Color(17, 24, 39),
                    new Color(55, 65, 81),
                    new Color(229, 231, 235)
            );
        }

        Map<String, Object> renderConfig = parseJsonMap(template != null ? template.getRenderConfigJson() : null);
        return new ThemeSpec(
                parseColor(renderConfig.get("coverBackground"), theme.coverBackground()),
                parseColor(renderConfig.get("accentColor"), theme.accent()),
                parseColor(renderConfig.get("accentSoftColor"), theme.accentSoft()),
                parseColor(renderConfig.get("coverTextColor"), theme.coverText()),
                parseColor(renderConfig.get("surfaceBackground"), theme.surfaceBackground()),
                parseColor(renderConfig.get("titleColor"), theme.titleText()),
                parseColor(renderConfig.get("bodyColor"), theme.bodyText()),
                parseColor(renderConfig.get("panelBorder"), theme.panelBorder())
        );
    }

    private Map<String, Object> parseJsonMap(String json) {
        if (!hasText(json)) {
            return Map.of();
        }
        try {
            Map<?, ?> parsed = objectMapper.readValue(json, Map.class);
            Map<String, Object> result = new LinkedHashMap<>();
            if (parsed != null) {
                for (Map.Entry<?, ?> entry : parsed.entrySet()) {
                    result.put(String.valueOf(entry.getKey()), entry.getValue());
                }
            }
            return result;
        } catch (Exception e) {
            log.debug("Parse ppt render config failed: {}", e.getMessage());
            return Map.of();
        }
    }

    private Color parseColor(Object value, Color fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            String raw = String.valueOf(value).trim();
            if (!hasText(raw)) {
                return fallback;
            }
            if (raw.startsWith("#")) {
                raw = raw.substring(1);
            }
            if (raw.length() == 6) {
                return new Color(
                        Integer.parseInt(raw.substring(0, 2), 16),
                        Integer.parseInt(raw.substring(2, 4), 16),
                        Integer.parseInt(raw.substring(4, 6), 16)
                );
            }
        } catch (Exception ignored) {
            // use fallback
        }
        return fallback;
    }

    private byte[] buildDocx(LessonPlanRequest request, String markdownContent) throws Exception {
        XWPFDocument document = new XWPFDocument();

        XWPFParagraph titleParagraph = document.createParagraph();
        titleParagraph.setAlignment(ParagraphAlignment.CENTER);
        XWPFRun titleRun = titleParagraph.createRun();
        titleRun.setBold(true);
        titleRun.setFontSize(22);
        titleRun.setText((hasText(request.getTopic()) ? request.getTopic() : request.getSubject()) + " 教案");

        addInfoParagraph(document, "学科：" + safeText(request.getSubject()));
        addInfoParagraph(document, "年级：" + safeText(request.getGrade()));
        addInfoParagraph(document, "课题：" + safeText(request.getTopic()));
        if (request.getDuration() != null) {
            addInfoParagraph(document, "课时长：" + request.getDuration() + " 分钟");
        }
        if (hasText(request.getTeacherName())) {
            addInfoParagraph(document, "教师：" + request.getTeacherName());
        }
        addInfoParagraph(document, "日期：" + LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE));
        document.createParagraph();

        for (String rawLine : markdownContent.split("\\R")) {
            String line = rawLine.trim();
            if (line.isEmpty()) {
                document.createParagraph();
                continue;
            }

            XWPFParagraph paragraph = document.createParagraph();
            XWPFRun run = paragraph.createRun();
            int headingLevel = resolveHeadingLevel(line);
            String text = stripMarkdownSyntax(line);
            if (!hasText(text)) {
                continue;
            }

            if (headingLevel > 0) {
                run.setBold(true);
                run.setFontSize(Math.max(14, 22 - headingLevel * 2));
                run.setText(text);
                continue;
            }

            if (line.matches("^\\s*[-*+]\\s+.*")) {
                paragraph.setIndentationLeft(360);
                run.setFontSize(12);
                run.setText("• " + stripMarkdownSyntax(line.replaceFirst("^\\s*[-*+]\\s+", "")));
                continue;
            }

            if (line.matches("^\\s*\\d+\\.\\s+.*")) {
                paragraph.setIndentationLeft(360);
                run.setFontSize(12);
                run.setText(stripMarkdownSyntax(line));
                continue;
            }

            run.setFontSize(12);
            run.setText(text);
        }

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        document.write(output);
        document.close();
        return output.toByteArray();
    }

    private void addInfoParagraph(XWPFDocument document, String text) {
        if (!hasText(text) || text.endsWith("：")) {
            return;
        }
        XWPFParagraph paragraph = document.createParagraph();
        XWPFRun run = paragraph.createRun();
        run.setFontSize(11);
        run.setText(text);
    }

    private int resolveHeadingLevel(String text) {
        if (text.matches("^#{1,6}\\s+.*")) {
            int level = 0;
            while (level < text.length() && text.charAt(level) == '#') {
                level++;
            }
            return Math.min(level, 6);
        }
        if (text.contains("目标") || text.contains("重点") || text.contains("难点")
                || text.contains("过程") || text.contains("作业")) {
            return 2;
        }
        return 0;
    }

    private String stripMarkdownSyntax(String value) {
        if (!hasText(value)) {
            return "";
        }
        return value
                .replaceFirst("^#{1,6}\\s+", "")
                .replaceFirst("^>\\s?", "")
                .replaceAll("\\*\\*(.*?)\\*\\*", "$1")
                .replaceAll("__(.*?)__", "$1")
                .replaceAll("\\*(.*?)\\*", "$1")
                .replaceAll("_(.*?)_", "$1")
                .replaceAll("`([^`]*)`", "$1")
                .replaceAll("!\\[(.*?)]\\((.*?)\\)", "$1")
                .replaceAll("\\[(.*?)]\\((.*?)\\)", "$1")
                .trim();
    }

    private String buildPreview(String plainText) {
        if (!hasText(plainText)) {
            return "";
        }
        return plainText.substring(0, Math.min(240, plainText.length()));
    }

    private String sanitizeRewriteResponse(String content) {
        if (!hasText(content)) {
            return "";
        }
        String sanitized = content.trim();
        if (sanitized.startsWith("```")) {
            sanitized = sanitized.replaceFirst("^```[a-zA-Z0-9_-]*\\s*", "");
            sanitized = sanitized.replaceFirst("\\s*```$", "");
        }
        return sanitized.trim();
    }

    private PptGenerateRequest enrichPptRequest(PptGenerateRequest request, PptTemplate template) {
        if (template == null) {
            return request;
        }

        String templateNotes = mergeTemplateNotes(template);
        return PptGenerateRequest.builder()
                .title(request.getTitle())
                .subject(request.getSubject())
                .grade(request.getGrade())
                .knowledgePoints(request.getKnowledgePoints())
                .slideCount(request.getSlideCount())
                .style(request.getStyle())
                .referenceText(mergeText(request.getReferenceText(), templateNotes))
                .userDescription(mergeText(request.getUserDescription(), "请优先贴合模板约束与视觉提示。"))
                .templateId(request.getTemplateId())
                .build();
    }

    private PptTemplate resolveTemplateSafely(String templateId) {
        if (!hasText(templateId)) {
            return null;
        }
        try {
            return pptTemplateService.resolveTemplateReference(templateId);
        } catch (Exception e) {
            log.warn("Resolve ppt template failed, fallback to default generation: {}", templateId, e);
            return null;
        }
    }

    private String mergeTemplateNotes(PptTemplate template) {
        StringBuilder builder = new StringBuilder();
        appendPlainLine(builder, "模板名称", template.getName());
        appendPlainLine(builder, "模板编码", template.getTemplateCode());
        appendPlainLine(builder, "模板引擎键", template.getEngineTemplateKey());
        appendPlainLine(builder, "模板描述", template.getDescription());
        appendPlainLine(builder, "模板提示", template.getPromptHint());
        return builder.toString().trim();
    }

    private String mergeText(String base, String extra) {
        if (!hasText(base)) {
            return hasText(extra) ? extra.trim() : null;
        }
        if (!hasText(extra)) {
            return base;
        }
        return base.trim() + "\n\n" + extra.trim();
    }

    private void appendPlainLine(StringBuilder builder, String label, String value) {
        if (!hasText(value)) {
            return;
        }
        if (builder.length() > 0) {
            builder.append('\n');
        }
        builder.append(label).append(": ").append(value.trim());
    }

    private void appendLine(StringBuilder builder, String label, String value) {
        if (hasText(value)) {
            builder.append(label).append(": ").append(value.trim()).append('\n');
        }
    }

    private String buildDeckSubtitle(PptGenerateRequest request) {
        List<String> parts = new ArrayList<>();
        if (hasText(request.getSubject())) {
            parts.add(request.getSubject().trim());
        }
        if (hasText(request.getGrade())) {
            parts.add(request.getGrade().trim());
        }
        if (hasText(request.getStyle())) {
            parts.add(request.getStyle().trim());
        }
        return parts.isEmpty() ? "" : String.join(" / ", parts);
    }

    private List<String> extractOutline(PptDeckPlan plan) {
        if (plan == null || plan.getSlides() == null) {
            return List.of();
        }
        return plan.getSlides().stream()
                .map(PptSlidePlan::getTitle)
                .filter(this::hasText)
                .toList();
    }

    private String saveLocally(String fileName, byte[] bytes) throws Exception {
        Path storageRoot = Paths.get(localStoragePath).toAbsolutePath().normalize();
        Path dateDir = storageRoot.resolve(LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE));
        Files.createDirectories(dateDir);
        Path filePath = dateDir.resolve(fileName).normalize();
        Files.write(filePath, bytes);
        return storageRoot.relativize(filePath).toString().replace('\\', '/');
    }

    private String buildDownloadUrl(String filePath) {
        return "/api/v1/courseware/download?path=" + encodeDownloadParam(filePath);
    }

    private String encodeDownloadParam(String value) {
        return URLEncoder.encode(valueOrEmpty(value), StandardCharsets.UTF_8);
    }

    private String sanitizeFileName(String value) {
        String sanitized = valueOrDefault(value, "document")
                .replaceAll("[\\\\/:*?\"<>|]", "_")
                .replaceAll("\\s+", "_")
                .trim();
        return hasText(sanitized) ? sanitized : "document";
    }

    private String cleanupBullet(String value) {
        return valueOrEmpty(value)
                .replaceAll("^[-*+•]\\s*", "")
                .replaceAll("^\\d+\\.\\s*", "")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String valueOrDefault(String value, String fallback) {
        return hasText(value) ? value : fallback;
    }

    private String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }

    private String safeText(String value) {
        return hasText(value) ? value.trim() : "";
    }

    private String truncate(String value, int maxLength) {
        if (!hasText(value) || value.length() <= maxLength) {
            return valueOrEmpty(value);
        }
        return value.substring(0, maxLength);
    }

    private String joinValues(Collection<String> values) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        return values.stream()
                .filter(this::hasText)
                .map(String::trim)
                .distinct()
                .reduce((left, right) -> left + "、" + right)
                .orElse("");
    }

    private record ThemeSpec(
            Color coverBackground,
            Color accent,
            Color accentSoft,
            Color coverText,
            Color surfaceBackground,
            Color titleText,
            Color bodyText,
            Color panelBorder
    ) {
        Color coverPill() {
            return accentSoft;
        }

        Color coverCard() {
            return accentSoft;
        }

        Color coverTextMuted() {
            return new Color(
                    Math.min(255, coverText.getRed() + 40),
                    Math.min(255, coverText.getGreen() + 40),
                    Math.min(255, coverText.getBlue() + 40)
            );
        }

        Color contentPanel() {
            return Color.WHITE;
        }

        Color sidePanel() {
            return accentSoft;
        }

        Color mutedText() {
            return bodyText;
        }
    }

    // ── Marker-based template rendering ──────────────────────────────────

    private void copySlideContent(XSLFSlide source, XSLFSlide target) {
        for (XSLFShape shape : source.getShapes()) {
            if (shape instanceof org.apache.poi.xslf.usermodel.XSLFTextShape textShape) {
                org.apache.poi.xslf.usermodel.XSLFTextBox textBox = target.createTextBox();
                textBox.setAnchor(textShape.getAnchor());
                for (XSLFTextParagraph srcPara : textShape.getTextParagraphs()) {
                    XSLFTextParagraph dstPara = textBox.addNewTextParagraph();
                    dstPara.setTextAlign(srcPara.getTextAlign());
                    for (XSLFTextRun srcRun : srcPara.getTextRuns()) {
                        XSLFTextRun dstRun = dstPara.addNewTextRun();
                        dstRun.setText(srcRun.getRawText());
                        if (srcRun.getFontColor() != null) {
                            dstRun.setFontColor(srcRun.getFontColor());
                        }
                        if (srcRun.getFontSize() != null) {
                            dstRun.setFontSize(srcRun.getFontSize());
                        }
                        dstRun.setFontFamily(srcRun.getFontFamily());
                        dstRun.setBold(srcRun.isBold());
                        dstRun.setItalic(srcRun.isItalic());
                    }
                }
            }
        }
    }

    private boolean hasParsedMarkers(PptTemplate template) {
        if (template == null || !hasText(template.getRenderConfigJson())) {
            return false;
        }
        try {
            TemplateStructure structure = objectMapper.readValue(
                    template.getRenderConfigJson(), TemplateStructure.class);
            if (structure.getSlides() == null) {
                return false;
            }
            return structure.getSlides().stream()
                    .anyMatch(s -> s.getMarkers() != null && !s.getMarkers().isEmpty());
        } catch (Exception e) {
            return false;
        }
    }

    private TemplateStructure parseTemplateStructure(PptTemplate template) {
        if (template == null || !hasText(template.getRenderConfigJson())) {
            return null;
        }
        try {
            return objectMapper.readValue(template.getRenderConfigJson(), TemplateStructure.class);
        } catch (Exception e) {
            log.warn("Failed to parse template structure", e);
            return null;
        }
    }

    private TemplateFillPlan buildFillPlanFromDeckPlan(PptDeckPlan plan, PptTemplate template) {
        TemplateStructure structure = parseTemplateStructure(template);
        if (structure == null || structure.getSlides() == null || plan == null || plan.getSlides() == null) {
            return TemplateFillPlan.builder()
                    .targetSlideCount(0).extraContentPages(0).slides(List.of())
                    .build();
        }

        List<TemplateSlideFill> fills = new ArrayList<>();
        int planSlideCount = plan.getSlides().size();
        int templateSlideCount = structure.getSlides().size();

        for (int i = 0; i < Math.min(planSlideCount, templateSlideCount); i++) {
            PptSlidePlan slidePlan = plan.getSlides().get(i);
            TemplateSlideStructure slideStruct = structure.getSlides().get(i);

            Map<String, String> markerValues = new HashMap<>();
            List<TemplateMarkerInfo> markers = slideStruct.getMarkers();
            if (markers != null && !markers.isEmpty()) {
                TemplateMarkerInfo titleMarker = markers.stream()
                        .filter(m -> m.getName().contains("标题") || m.getName().contains("title"))
                        .findFirst().orElse(null);
                TemplateMarkerInfo contentMarker = markers.stream()
                        .filter(m -> !m.equals(titleMarker))
                        .findFirst().orElse(null);

                if (titleMarker != null && slidePlan.getTitle() != null) {
                    markerValues.put(titleMarker.getName(), slidePlan.getTitle());
                }
                if (contentMarker != null && slidePlan.getBullets() != null) {
                    markerValues.put(contentMarker.getName(), String.join("\n", slidePlan.getBullets()));
                }
                for (TemplateMarkerInfo marker : markers) {
                    if (!markerValues.containsKey(marker.getName())) {
                        markerValues.put(marker.getName(), "");
                    }
                }
            }

            fills.add(TemplateSlideFill.builder()
                    .slideIndex(i)
                    .isDuplicate(false)
                    .markerValues(markerValues)
                    .build());
        }

        int extraPages = Math.max(0, planSlideCount - templateSlideCount);
        return TemplateFillPlan.builder()
                .targetSlideCount(planSlideCount)
                .extraContentPages(extraPages)
                .slides(fills)
                .build();
    }

    private byte[] renderTemplatedPptx(PptTemplate template, TemplateFillPlan fillPlan) throws Exception {
        Path templatePath = resolveTemplatePath(template.getTemplateFilePath());
        XMLSlideShow pptx = new XMLSlideShow(Files.newInputStream(templatePath));
        TemplateStructure structure = parseTemplateStructure(template);

        try {
            // Duplicate repeatable slide for extra content pages
            if (fillPlan.getExtraContentPages() > 0 && structure != null
                    && structure.getRepeatableSlideIndex() >= 0) {
                int repeatIdx = structure.getRepeatableSlideIndex();
                if (repeatIdx < pptx.getSlides().size()) {
                    XMLSlideShow source = new XMLSlideShow(Files.newInputStream(templatePath));
                    try {
                        XSLFSlide sourceSlide = source.getSlides().get(repeatIdx);
                        org.apache.poi.xslf.usermodel.XSLFSlideLayout layout = sourceSlide.getSlideLayout();
                        for (int i = 0; i < fillPlan.getExtraContentPages(); i++) {
                            XSLFSlide newSlide = layout != null
                                    ? pptx.createSlide(layout)
                                    : pptx.createSlide();
                            copySlideContent(sourceSlide, newSlide);
                        }
                    } finally {
                        source.close();
                    }
                }
            }

            // Replace markers in each slide
            for (TemplateSlideFill slideFill : fillPlan.getSlides()) {
                int idx = slideFill.getSlideIndex();
                if (idx < 0 || idx >= pptx.getSlides().size()) {
                    continue;
                }
                XSLFSlide slide = pptx.getSlides().get(idx);
                replaceMarkersInSlide(slide, slideFill.getMarkerValues());
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            pptx.write(baos);
            return baos.toByteArray();
        } finally {
            pptx.close();
        }
    }

    private static final Pattern MARKER_PATTERN = Pattern.compile("\\{\\{(.+?)}}");

    private void replaceMarkersInSlide(XSLFSlide slide, Map<String, String> markerValues) {
        if (markerValues == null || markerValues.isEmpty()) {
            return;
        }
        replaceMarkersInShapes(slide.getShapes(), markerValues);

        // 备注页（speaker notes）：扫描时已经识别到，渲染时也要同步替换。
        XSLFNotes notes = slide.getNotes();
        if (notes != null) {
            replaceMarkersInShapes(notes.getShapes(), markerValues);
        }
    }

    /**
     * 递归遍历 shapes：表格 → cells；组合形状 → 内部 shapes；文本形状 → run 级替换。
     * 与 PptTemplateServiceImpl.collectMarkersFromShapes 对称，保证识别什么就替换什么。
     */
    private void replaceMarkersInShapes(Collection<? extends XSLFShape> shapes, Map<String, String> markerValues) {
        if (shapes == null) {
            return;
        }
        for (XSLFShape shape : shapes) {
            if (shape instanceof XSLFTable table) {
                for (XSLFTableRow row : table.getRows()) {
                    for (XSLFTableCell cell : row.getCells()) {
                        replaceMarkersInTextShape(cell, markerValues);
                    }
                }
            } else if (shape instanceof XSLFGroupShape group) {
                replaceMarkersInShapes(group.getShapes(), markerValues);
            } else if (shape instanceof XSLFTextShape textShape) {
                replaceMarkersInTextShape(textShape, markerValues);
            }
        }
    }

    private void replaceMarkersInTextShape(XSLFTextShape textShape, Map<String, String> markerValues) {
        for (XSLFTextParagraph paragraph : textShape.getTextParagraphs()) {
            replaceMarkersInParagraph(paragraph, markerValues);
        }
    }

    /**
     * 在保留局部样式的前提下替换段落内的所有 {{标记}}。
     * <p>算法：
     * <ol>
     *   <li>把段落内所有 run 拼成 fullText，同时记录每个 run 在 fullText 中的字符区间 [start, end)</li>
     *   <li>用 MARKER_PATTERN 找出所有 {{xxx}} 的位置；marker key 走 trim，兼容 {{ 空格 标 题 }} 这种 PPT 自动加空格的情况</li>
     *   <li>对每个 match，选与之字符重叠最多的 run 作为 anchor（替换值写在这个 run 上，保留它的字号/颜色/加粗等）；
     *       被 match 覆盖到的其它 run 把被覆盖的字符删掉</li>
     *   <li>matches 从后往前应用，避免索引漂移</li>
     * </ol>
     */
    private void replaceMarkersInParagraph(XSLFTextParagraph paragraph, Map<String, String> markerValues) {
        List<XSLFTextRun> runs = paragraph.getTextRuns();
        if (runs.isEmpty()) {
            return;
        }

        // 1. 拼出 fullText + 每个 run 在其中的字符区间
        StringBuilder fullText = new StringBuilder();
        int[] runStart = new int[runs.size()];
        int[] runEnd = new int[runs.size()];
        for (int i = 0; i < runs.size(); i++) {
            String raw = runs.get(i).getRawText();
            runStart[i] = fullText.length();
            fullText.append(raw == null ? "" : raw);
            runEnd[i] = fullText.length();
        }
        String text = fullText.toString();

        // 2. 找出所有 marker 匹配
        Matcher matcher = MARKER_PATTERN.matcher(text);
        List<int[]> matchRanges = new ArrayList<>();  // [matchStart, matchEnd]
        List<String> matchKeys = new ArrayList<>();
        while (matcher.find()) {
            String key = matcher.group(1).trim();
            // 即便 markerValues 里没这个 key 我们也要处理 —— 默认替换为空，避免最终 PPT 上残留 {{xxx}} 字面量。
            matchRanges.add(new int[]{matcher.start(), matcher.end()});
            matchKeys.add(key);
        }
        if (matchRanges.isEmpty()) {
            return;
        }

        // 3. 倒序应用每个 match：在 anchor run 上写替换值，被覆盖的其他 run 清空对应字符
        StringBuilder[] newTexts = new StringBuilder[runs.size()];
        for (int i = 0; i < runs.size(); i++) {
            newTexts[i] = new StringBuilder(runs.get(i).getRawText() == null ? "" : runs.get(i).getRawText());
        }

        for (int mi = matchRanges.size() - 1; mi >= 0; mi--) {
            int matchStart = matchRanges.get(mi)[0];
            int matchEnd = matchRanges.get(mi)[1];
            String key = matchKeys.get(mi);
            String value = markerValues.get(key);
            if (value == null) {
                // 未提供值就清空标记字面量，避免渲染出 {{xxx}}。
                value = "";
            }

            int anchorIdx = findAnchorRunIndex(runStart, runEnd, matchStart, matchEnd);

            for (int ri = 0; ri < runs.size(); ri++) {
                int ovS = Math.max(runStart[ri], matchStart);
                int ovE = Math.min(runEnd[ri], matchEnd);
                if (ovS >= ovE) {
                    continue;
                }
                int localStart = ovS - runStart[ri];
                int localEnd = ovE - runStart[ri];
                StringBuilder sb = newTexts[ri];
                // 边界保护：之前已经处理过的 match 可能让 run 文本变短
                localEnd = Math.min(localEnd, sb.length());
                localStart = Math.min(localStart, localEnd);
                if (ri == anchorIdx) {
                    sb.replace(localStart, localEnd, value);
                } else {
                    sb.delete(localStart, localEnd);
                }
            }
        }

        // 4. 写回每个 run
        for (int i = 0; i < runs.size(); i++) {
            String newText = newTexts[i].toString();
            String oldText = runs.get(i).getRawText() == null ? "" : runs.get(i).getRawText();
            if (!newText.equals(oldText)) {
                runs.get(i).setText(newText);
            }
        }
    }

    private int findAnchorRunIndex(int[] runStart, int[] runEnd, int matchStart, int matchEnd) {
        int bestIdx = 0;
        int bestOverlap = -1;
        for (int i = 0; i < runStart.length; i++) {
            int ov = Math.min(runEnd[i], matchEnd) - Math.max(runStart[i], matchStart);
            if (ov > bestOverlap) {
                bestOverlap = ov;
                bestIdx = i;
            }
        }
        return bestIdx;
    }

    // ============================================================
    // FullReplacement 流：上传即模板（不需要管理员写 {{xxx}} 占位符）
    // ============================================================

    /** 替换率低于该阈值视为生成失败（防止把"完全是原模板"的 PPT 当成成功交付）。 */
    private static final double FULL_REPLACEMENT_MIN_APPLY_RATE = 0.3;

    /**
     * 全替换一次性返回所有文本块改写的 JSON，输出很长，必须远大于全局默认 max-tokens(1024)，
     * 否则 JSON 会被截断、解析失败、0 替换。qwen3.6-plus 支持 8192（与 interactive.writer 一致）。
     */
    private static final int FULL_REPLACEMENT_MAX_TOKENS = 8192;

    /** 全替换并发分批：每批最多多少个文本块（小批 → 单次调用快、不易截断）。 */
    private static final int FULL_REPLACEMENT_BATCH_SIZE = 50;

    /** 全替换并发分批的最大并发数（限流，避免线程过多或触发平台 QPS 限制）。 */
    private static final int FULL_REPLACEMENT_MAX_CONCURRENCY = 4;

    private static final String FULL_REPLACEMENT_SYSTEM_PROMPT =
            "你是中国教学 PPT 模板改写助手。给定一份 pptx 模板的全部文本块和一个新教学主题，"
                    + "请把每个文本块改写成对应主题的内容。严格只返回 JSON 对象，不要用 markdown 代码块包裹，"
                    + "不要在 JSON 前后输出任何说明文字。\n\n"
                    + "输出 schema：{\"replacements\":[{\"blockId\":\"...\",\"replacement\":\"...\"}]}\n\n"
                    + "规则（违反任何一条会导致该块替换被丢弃，最终 PPT 显示原文）：\n"
                    + "1. 必须为输入的每一个 blockId 输出 replacement，不能漏、不能加新 blockId。\n"
                    + "2. 字符数应与原文接近，**绝对不能超过原文 1.3 倍**——超出会让幻灯片版面溢出。\n"
                    + "3. 含换行符 \\n 的 block（列表）的换行数**必须严格等于**原文换行数。少一行或多一行都会被丢弃。\n"
                    + "4. 装饰性内容请**原样保留**：纯数字（如 \"01\"、\"02\"）、单字符、看起来是页码 / 版权声明 / 固定品牌名 / 学校名 / 日期占位。\n"
                    + "5. role=cover_title/content_title/summary 的 block 改写成新主题的对应标题；role=content 改写成新主题的对应内容；role=table_cell 按表格语义改写。\n"
                    + "6. 列表 block 必须保持原 list 项数，每行写新主题下对应的要点。\n"
                    + "7. JSON 必须 valid，不要尾随逗号，不要单引号。";

    @Override
    public PptGenerateResponse renderFullReplacementPpt(PptGenerateRequest request, PptTemplate template) {
        if (template == null || !hasText(template.getTemplateFilePath())) {
            return PptGenerateResponse.builder()
                    .success(false)
                    .error("FullReplacement 需要模板已上传 pptx 文件")
                    .build();
        }

        try {
            Path templatePath = resolveTemplatePath(template.getTemplateFilePath());
            if (!Files.exists(templatePath)) {
                throw new IllegalStateException("模板 pptx 文件不存在: " + template.getTemplateFilePath());
            }

            // 1. 提取所有文本块
            List<TextBlockInfo> blocks = extractTextBlocks(templatePath);
            if (blocks.isEmpty()) {
                throw new IllegalStateException("模板里没有发现可改写的文本块");
            }

            // 2. 调 LLM 生成 blockId → replacement
            Map<String, String> replacements = invokeLLMForReplacements(request, blocks);

            // 2.1 替换率把关：低于阈值视为生成失败，避免把"完全是原模板"的 PPT 当作成功交给用户
            int targetCount = (int) blocks.stream().filter(b -> !b.isFromNotes()).count();
            int appliedCount = replacements.size();
            double appliedRate = targetCount > 0 ? (double) appliedCount / targetCount : 0;
            if (targetCount > 0) {
                if (appliedCount == 0) {
                    throw new IllegalStateException(
                            "AI 未能生成任何模板替换（多半是 LLM 调用超时或返回格式错误），请稍后重试或更换模板。");
                }
                if (appliedRate < FULL_REPLACEMENT_MIN_APPLY_RATE) {
                    throw new IllegalStateException(String.format(
                            "AI 仅成功改写 %d/%d 个文本块（成功率 %.0f%%），生成内容不完整，请稍后重试。",
                            appliedCount, targetCount, appliedRate * 100));
                }
                log.info("FullReplacement applied={}/{} (rate={}%)",
                        appliedCount, targetCount, String.format("%.0f", appliedRate * 100));
            }

            // 3. POI in-place 替换
            byte[] pptxBytes = replaceTextBlocksInPptx(templatePath, replacements);

            // 4. 落盘
            String baseName = hasText(request.getTitle()) ? request.getTitle() : "PPT";
            String fileName = String.format("%s_%s.pptx",
                    sanitizeFileName(baseName),
                    LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE));
            String filePath = saveLocally(fileName, pptxBytes);

            // 5. 把改写后的真实内容按页聚合成结构化 SlidePlan，喂给前端"逐页揭幕"用——
            //    否则前端只能拿到模板封面 + 占位标题，看不到 AI 真正改了什么。
            List<PptSlidePlan> aggregatedSlides = aggregateSlidesFromReplacements(blocks, replacements);
            PptDeckPlan aggregatedPlan = PptDeckPlan.builder()
                    .title(valueOrDefault(request.getTitle(), ""))
                    .slideCount(aggregatedSlides.size())
                    .slides(aggregatedSlides)
                    .build();

            return PptGenerateResponse.builder()
                    .success(true)
                    .filePath(filePath)
                    .downloadUrl(buildDownloadUrl(filePath))
                    .fileName(fileName)
                    .fileSize((long) pptxBytes.length)
                    .plan(aggregatedPlan)
                    .outline(aggregatedSlides.stream()
                            .map(PptSlidePlan::getTitle)
                            .filter(t -> t != null && !t.isBlank())
                            .limit(20)
                            .toList())
                    .build();
        } catch (Exception e) {
            log.error("FullReplacement render failed", e);
            return PptGenerateResponse.builder()
                    .success(false)
                    .error("PPT 生成异常: " + e.getMessage())
                    .build();
        }
    }

    /**
     * 按 slideIndex 把已改写的文本块聚合成"每页一个 SlidePlan"。
     * 角色映射：
     *   cover_title / content_title / summary → 该页 title（按优先级取首个）
     *   content / cover_subtitle / cover_other → 该页 bullets（按出现顺序，换行拆条）
     *   table_cell / notes → 忽略（表格在 pptx 已渲染；备注不在工作区展示）
     */
    private List<PptSlidePlan> aggregateSlidesFromReplacements(List<TextBlockInfo> blocks,
                                                                Map<String, String> replacements) {
        // extractTextBlocks 按 slideIndex 升序返回，这里用 LinkedHashMap 保持插入顺序即可
        Map<Integer, List<TextBlockInfo>> bySlide = new LinkedHashMap<>();
        for (TextBlockInfo b : blocks) {
            if (b == null || b.isFromNotes()) continue;
            String role = b.getRole() == null ? "" : b.getRole().toLowerCase(Locale.ROOT);
            if ("table_cell".equals(role) || "notes".equals(role)) continue;
            bySlide.computeIfAbsent(b.getSlideIndex(), k -> new ArrayList<>()).add(b);
        }

        List<PptSlidePlan> slides = new ArrayList<>(bySlide.size());
        for (Map.Entry<Integer, List<TextBlockInfo>> entry : bySlide.entrySet()) {
            int slideIdx = entry.getKey();
            int slideNo = slideIdx + 1;
            List<TextBlockInfo> slideBlocks = entry.getValue();

            String title = null;
            for (TextBlockInfo b : slideBlocks) {
                String role = b.getRole() == null ? "" : b.getRole().toLowerCase(Locale.ROOT);
                if ("cover_title".equals(role) || "content_title".equals(role) || "summary".equals(role)) {
                    String text = resolveBlockText(b, replacements);
                    if (text != null && !text.isBlank()) {
                        title = text.split("\n", 2)[0].trim();
                        break;
                    }
                }
            }

            List<String> bullets = new ArrayList<>();
            for (TextBlockInfo b : slideBlocks) {
                String role = b.getRole() == null ? "" : b.getRole().toLowerCase(Locale.ROOT);
                if ("cover_title".equals(role) || "content_title".equals(role) || "summary".equals(role)) {
                    continue;
                }
                String text = resolveBlockText(b, replacements);
                if (text == null || text.isBlank()) continue;
                for (String line : text.split("\n")) {
                    String trimmed = line.trim();
                    if (!trimmed.isEmpty()) {
                        bullets.add(trimmed);
                    }
                }
            }

            slides.add(PptSlidePlan.builder()
                    .slideNo(slideNo)
                    .title(title != null ? title : "")
                    .layout(slideIdx == 0 ? "cover" : "content")
                    .bullets(bullets)
                    .build());
        }
        return slides;
    }

    private String resolveBlockText(TextBlockInfo b, Map<String, String> replacements) {
        String rep = replacements.get(b.getBlockId());
        if (rep != null && !rep.isBlank()) {
            return rep;
        }
        return b.getOriginal() != null ? b.getOriginal() : "";
    }

    /**
     * 给 LLM 喂一份"待改写文本块"清单，返回 blockId → 新文本 的映射。
     * MVP：非流式（一次 chat 调用），简单可靠。后续如果 token 太长可以切片。
     * <p>备注页（fromNotes=true）不送 LLM，保留原文。
     */
    private Map<String, String> invokeLLMForReplacements(PptGenerateRequest request,
                                                          List<TextBlockInfo> blocks) {
        List<TextBlockInfo> targetBlocks = blocks.stream()
                .filter(b -> !b.isFromNotes())
                .toList();
        if (targetBlocks.isEmpty()) {
            return Map.of();
        }

        // 并发分批：把所有文本块切成若干小批，并发调 LLM，最后合并。
        // 好处：每批 prompt/输出都小 → 单次调用快、不易截断；多批并发 → 总墙钟时间大幅下降。
        // 安全性：每批互相独立、无共享可变状态；真正改 pptx 的 POI 部分在合并之后单线程进行，
        // 不在并发范围内，因此避开了 POI 的线程安全问题。
        List<List<TextBlockInfo>> batches = partitionBlocks(targetBlocks, FULL_REPLACEMENT_BATCH_SIZE);
        if (batches.size() == 1) {
            // 单批：无需开线程池，直接调用（失败按原逻辑抛出，由上层转成失败响应）。
            return invokeLLMForBatch(request, batches.get(0));
        }

        int concurrency = Math.min(FULL_REPLACEMENT_MAX_CONCURRENCY, batches.size());
        log.info("FullReplacement 并发分批: totalBlocks={}, batches={}, concurrency={}",
                targetBlocks.size(), batches.size(), concurrency);

        ExecutorService pool = Executors.newFixedThreadPool(concurrency, r -> {
            Thread t = new Thread(r, "ppt-fullreplace-batch");
            t.setDaemon(true);
            return t;
        });
        try {
            List<CompletableFuture<Map<String, String>>> futures = new ArrayList<>(batches.size());
            for (List<TextBlockInfo> batch : batches) {
                futures.add(CompletableFuture.supplyAsync(() -> {
                    try {
                        return invokeLLMForBatch(request, batch);
                    } catch (Exception e) {
                        // 单批失败不拖垮整体：这批的块保留原文，其余批照常；
                        // 最终由 renderFullReplacementPpt 的 30% 替换率护栏决定整体成败。
                        log.warn("FullReplacement 某批改写失败，跳过该批（保留原文）: batchSize={}", batch.size(), e);
                        return Map.<String, String>of();
                    }
                }, pool));
            }

            Map<String, String> merged = new LinkedHashMap<>();
            for (CompletableFuture<Map<String, String>> future : futures) {
                merged.putAll(future.join());
            }
            log.info("FullReplacement 分批合并完成: 采纳替换 {} 条 / 共 {} 块", merged.size(), targetBlocks.size());
            return merged;
        } finally {
            pool.shutdownNow();
        }
    }

    /** 把文本块按固定大小切成多批（每个 block 是独立单元，按数量切不会拆散某个 list 块）。 */
    private List<List<TextBlockInfo>> partitionBlocks(List<TextBlockInfo> blocks, int batchSize) {
        List<List<TextBlockInfo>> batches = new ArrayList<>();
        for (int i = 0; i < blocks.size(); i += batchSize) {
            batches.add(blocks.subList(i, Math.min(i + batchSize, blocks.size())));
        }
        return batches;
    }

    /** 对一批文本块调用一次 LLM，返回 blockId → 改写文本 的映射。无共享状态，可并发调用。 */
    private Map<String, String> invokeLLMForBatch(PptGenerateRequest request,
                                                  List<TextBlockInfo> batchBlocks) {
        try {
            String blocksJson = objectMapper.writeValueAsString(batchBlocks.stream()
                    .map(b -> Map.of(
                            "blockId", b.getBlockId(),
                            "slideIndex", b.getSlideIndex(),
                            "role", valueOrDefault(b.getRole(), "other"),
                            "original", valueOrDefault(b.getOriginal(), "")))
                    .toList());

            log.info("FullReplacement 批调用: blocks={}, promptChars={}", batchBlocks.size(), blocksJson.length());

            String userPrompt = "新教学主题：" + valueOrDefault(request.getTitle(), "未指定")
                    + "\n学科：" + valueOrDefault(request.getSubject(), "未指定")
                    + "\n年级：" + valueOrDefault(request.getGrade(), "未指定")
                    + "\n知识点：" + (request.getKnowledgePoints() == null || request.getKnowledgePoints().isEmpty()
                        ? "无" : String.join("、", request.getKnowledgePoints()))
                    + "\n补充说明：" + valueOrDefault(request.getUserDescription(), "无")
                    + "\n\n模板文本块（顺序固定，请按相同顺序返回 replacements）：\n"
                    + blocksJson
                    + "\n\n请输出 JSON：{\"replacements\":[{\"blockId\":\"...\",\"replacement\":\"...\"}]}";

            // 流式调用：避免 read-timeout 的"单次总读取"打断（与其他正常路径一致）。
            String response = llmService.chatStream(List.of(
                    new ILLMService.ChatMessage("system", FULL_REPLACEMENT_SYSTEM_PROMPT),
                    new ILLMService.ChatMessage("user", userPrompt)
            ), chunk -> { }, FULL_REPLACEMENT_MAX_TOKENS);
            return parseReplacementsJson(response);
        } catch (Exception e) {
            log.warn("FullReplacement LLM call failed", e);
            throw new RuntimeException("LLM 调用失败：" + e.getMessage(), e);
        }
    }

    private Map<String, String> parseReplacementsJson(String response) {
        if (response == null || response.isBlank()) {
            return Map.of();
        }
        String json = response.trim();
        // 剥 markdown 围栏
        if (json.startsWith("```")) {
            int firstLn = json.indexOf('\n');
            int lastFence = json.lastIndexOf("```");
            if (firstLn > 0 && lastFence > firstLn) {
                json = json.substring(firstLn + 1, lastFence).trim();
            }
        }
        Map<String, String> strict = tryParseReplacements(json);
        if (!strict.isEmpty()) {
            return strict;
        }
        // 兜底：LLM 输出被 max_tokens 截断时整体 JSON 不合法、严格解析失败。
        // 截到最后一个完整对象的 '}' 再补 "]}"，抢救已生成的完整条目（配合 30% 护栏多半仍能成）。
        String repaired = repairTruncatedReplacementsJson(json);
        if (repaired != null) {
            Map<String, String> salvaged = tryParseReplacements(repaired);
            if (!salvaged.isEmpty()) {
                log.warn("FullReplacement JSON 疑似被截断，已抢救 {} 个完整替换条目", salvaged.size());
                return salvaged;
            }
        }
        log.warn("Parse FullReplacement JSON failed and salvage produced nothing");
        return Map.of();
    }

    private Map<String, String> tryParseReplacements(String json) {
        try {
            Map<String, Object> parsed = objectMapper.readValue(json, Map.class);
            Object arr = parsed.get("replacements");
            if (!(arr instanceof List<?> list)) {
                return Map.of();
            }
            Map<String, String> out = new LinkedHashMap<>();
            for (Object item : list) {
                if (!(item instanceof Map<?, ?> entry)) {
                    continue;
                }
                Object id = entry.get("blockId");
                Object rep = entry.get("replacement");
                if (id != null && rep != null) {
                    out.put(String.valueOf(id), String.valueOf(rep));
                }
            }
            return out;
        } catch (Exception e) {
            return Map.of();
        }
    }

    /**
     * 抢救被截断的 replacements JSON：截到最后一个完整对象的 '}'，补 "]}" 拼成合法 JSON。
     * best-effort——拼不出合法 JSON（如截断点恰在含 '}' 的字符串中）时返回 null，由调用方按空结果处理。
     */
    private String repairTruncatedReplacementsJson(String json) {
        if (json == null) {
            return null;
        }
        int arrStart = json.indexOf('[');
        int lastObjEnd = json.lastIndexOf('}');
        if (arrStart < 0 || lastObjEnd <= arrStart) {
            return null;
        }
        return json.substring(0, lastObjEnd + 1) + "]}";
    }


    /**
     * 提取 pptx 里每个非空 XSLFTextShape 作为一个 TextBlockInfo。
     * 按 shape 粒度切（不是 paragraph）——一个 list 是一个 block，AI 看到完整的列表
     * 才能保持项数对齐。提取顺序必须跟 {@link #replaceTextBlocksInPptx} 走的顺序一致。
     */
    List<TextBlockInfo> extractTextBlocks(Path pptxPath) {
        List<TextBlockInfo> blocks = new ArrayList<>();
        try (XMLSlideShow pptx = new XMLSlideShow(Files.newInputStream(pptxPath))) {
            List<XSLFSlide> slides = pptx.getSlides();
            int lastIndex = slides.size() - 1;
            for (int si = 0; si < slides.size(); si++) {
                XSLFSlide slide = slides.get(si);
                walkShapesForExtract(slide.getShapes(), si, lastIndex, false, blocks);
                XSLFNotes notes = slide.getNotes();
                if (notes != null) {
                    walkShapesForExtract(notes.getShapes(), si, lastIndex, true, blocks);
                }
            }
        } catch (Exception e) {
            log.error("Extract text blocks from pptx failed: {}", pptxPath, e);
            throw new RuntimeException("提取文本块失败: " + e.getMessage(), e);
        }
        return blocks;
    }

    private void walkShapesForExtract(java.util.Collection<? extends XSLFShape> shapes,
                                      int slideIndex, int lastSlideIndex, boolean isNotes,
                                      List<TextBlockInfo> out) {
        if (shapes == null) {
            return;
        }
        for (XSLFShape shape : shapes) {
            if (shape instanceof XSLFTable table) {
                for (XSLFTableRow row : table.getRows()) {
                    for (XSLFTableCell cell : row.getCells()) {
                        addShapeBlock(cell, slideIndex, lastSlideIndex, isNotes, "table_cell", out);
                    }
                }
            } else if (shape instanceof XSLFGroupShape group) {
                walkShapesForExtract(group.getShapes(), slideIndex, lastSlideIndex, isNotes, out);
            } else if (shape instanceof XSLFTextShape ts) {
                String role = isNotes ? "notes" : inferShapeRole(slideIndex, lastSlideIndex, out, slideIndex);
                addShapeBlock(ts, slideIndex, lastSlideIndex, isNotes, role, out);
            }
        }
    }

    private void addShapeBlock(XSLFTextShape ts, int slideIndex, int lastSlideIndex,
                               boolean isNotes, String role, List<TextBlockInfo> out) {
        List<XSLFTextParagraph> paragraphs = ts.getTextParagraphs();
        if (paragraphs.isEmpty()) {
            return;
        }
        StringBuilder joined = new StringBuilder();
        for (int pi = 0; pi < paragraphs.size(); pi++) {
            if (pi > 0) {
                joined.append('\n');
            }
            joined.append(getParagraphRawText(paragraphs.get(pi)));
        }
        String text = joined.toString();
        if (text.trim().isEmpty()) {
            return;
        }
        out.add(TextBlockInfo.builder()
                .blockId(slideIndex + "-shape" + ts.getShapeId() + (isNotes ? "-notes" : ""))
                .slideIndex(slideIndex)
                .original(text)
                .role(role)
                .fromNotes(isNotes)
                .paragraphCount(paragraphs.size())
                .build());
    }

    private String getParagraphRawText(XSLFTextParagraph paragraph) {
        StringBuilder sb = new StringBuilder();
        for (XSLFTextRun run : paragraph.getTextRuns()) {
            String raw = run.getRawText();
            if (raw != null) {
                sb.append(raw);
            }
        }
        return sb.toString();
    }

    private String inferShapeRole(int slideIndex, int lastSlideIndex,
                                  List<TextBlockInfo> existing, int currentSlide) {
        if (slideIndex == 0) {
            // 第一页第一个文本块是封面主标题，第二个是副标题
            long countOnThisSlide = existing.stream()
                    .filter(b -> b.getSlideIndex() == currentSlide && !b.isFromNotes())
                    .count();
            if (countOnThisSlide == 0) {
                return "cover_title";
            }
            if (countOnThisSlide == 1) {
                return "cover_subtitle";
            }
            return "cover_other";
        }
        if (slideIndex == lastSlideIndex && lastSlideIndex > 0) {
            return "summary";
        }
        return "content";
    }

    /**
     * 用 LLM 返回的 blockId→替换文本 映射，在 pptx 上做 in-place 替换。
     * <p>校验规则（任何一项不过 → log 警告并保留原文，不破坏版面）：
     * <ul>
     *   <li>换行数必须一致：{@code replacement.split("\n").length == original.split("\n").length}</li>
     *   <li>长度不超过原文 1.3 倍：超出则按行截断保留</li>
     *   <li>blockId 不存在于 LLM 返回里 → 保留原文</li>
     * </ul>
     * <p>遍历顺序必须跟 {@link #extractTextBlocks} 完全一致。
     *
     * @return 渲染后的 pptx 字节
     */
    byte[] replaceTextBlocksInPptx(Path pptxPath, Map<String, String> replacements) {
        try (XMLSlideShow pptx = new XMLSlideShow(Files.newInputStream(pptxPath))) {
            List<XSLFSlide> slides = pptx.getSlides();
            int lastIndex = slides.size() - 1;
            // 用 list 作为 walk 上下文，跟 extract 同样的顺序
            List<TextBlockInfo> dummyOut = new ArrayList<>();
            int[] applied = {0};
            int[] skipped = {0};
            for (int si = 0; si < slides.size(); si++) {
                XSLFSlide slide = slides.get(si);
                walkShapesForReplace(slide.getShapes(), si, lastIndex, false, replacements, dummyOut, applied, skipped);
                XSLFNotes notes = slide.getNotes();
                if (notes != null) {
                    // MVP：备注页保留原样不替换。如未来要改，把 isNotesAllow 改 true 即可。
                    walkShapesForReplace(notes.getShapes(), si, lastIndex, true, replacements, dummyOut, applied, skipped);
                }
            }
            log.info("FullReplacement applied={}, skipped={}", applied[0], skipped[0]);

            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            pptx.write(baos);
            return baos.toByteArray();
        } catch (Exception e) {
            log.error("Replace text blocks in pptx failed: {}", pptxPath, e);
            throw new RuntimeException("PPT 文本替换失败: " + e.getMessage(), e);
        }
    }

    private void walkShapesForReplace(java.util.Collection<? extends XSLFShape> shapes,
                                      int slideIndex, int lastSlideIndex, boolean isNotes,
                                      Map<String, String> replacements,
                                      List<TextBlockInfo> ctx,
                                      int[] appliedCount, int[] skippedCount) {
        if (shapes == null) {
            return;
        }
        for (XSLFShape shape : shapes) {
            if (shape instanceof XSLFTable table) {
                for (XSLFTableRow row : table.getRows()) {
                    for (XSLFTableCell cell : row.getCells()) {
                        applyReplacementOnShape(cell, slideIndex, lastSlideIndex, isNotes,
                                "table_cell", replacements, ctx, appliedCount, skippedCount);
                    }
                }
            } else if (shape instanceof XSLFGroupShape group) {
                walkShapesForReplace(group.getShapes(), slideIndex, lastSlideIndex, isNotes,
                        replacements, ctx, appliedCount, skippedCount);
            } else if (shape instanceof XSLFTextShape ts) {
                String role = isNotes ? "notes" : inferShapeRole(slideIndex, lastSlideIndex, ctx, slideIndex);
                applyReplacementOnShape(ts, slideIndex, lastSlideIndex, isNotes,
                        role, replacements, ctx, appliedCount, skippedCount);
            }
        }
    }

    private void applyReplacementOnShape(XSLFTextShape ts, int slideIndex, int lastSlideIndex,
                                         boolean isNotes, String role,
                                         Map<String, String> replacements,
                                         List<TextBlockInfo> ctx,
                                         int[] appliedCount, int[] skippedCount) {
        List<XSLFTextParagraph> paragraphs = ts.getTextParagraphs();
        if (paragraphs.isEmpty()) {
            return;
        }
        // 重新拼出原文（必须跟 extract 时一致）
        StringBuilder joined = new StringBuilder();
        for (int pi = 0; pi < paragraphs.size(); pi++) {
            if (pi > 0) {
                joined.append('\n');
            }
            joined.append(getParagraphRawText(paragraphs.get(pi)));
        }
        String original = joined.toString();
        if (original.trim().isEmpty()) {
            return;
        }
        // 跟 extract 走同样的"加入 ctx"以保持后续 inferRole 计数一致
        ctx.add(TextBlockInfo.builder()
                .blockId(slideIndex + "-shape" + ts.getShapeId() + (isNotes ? "-notes" : ""))
                .slideIndex(slideIndex)
                .original(original)
                .role(role)
                .fromNotes(isNotes)
                .paragraphCount(paragraphs.size())
                .build());

        String blockId = slideIndex + "-shape" + ts.getShapeId() + (isNotes ? "-notes" : "");
        String replacement = replacements.get(blockId);

        // 1. 没有替换值 → 保留原文（备注页 / LLM 漏返）
        if (replacement == null || isNotes) {
            skippedCount[0]++;
            return;
        }

        // 2. 校验换行数对齐
        int originalLines = original.split("\n", -1).length;
        int replacementLines = replacement.split("\n", -1).length;
        if (originalLines != replacementLines) {
            log.warn("FullReplacement skip: blockId={} lineCount mismatch (original={}, replacement={})",
                    blockId, originalLines, replacementLines);
            skippedCount[0]++;
            return;
        }

        // 3. 校验长度（容许 1.3 倍）
        if (replacement.length() > original.length() * 1.3 + 4) {
            log.warn("FullReplacement skip: blockId={} length blowup (original={}, replacement={})",
                    blockId, original.length(), replacement.length());
            skippedCount[0]++;
            return;
        }

        // 4. 替换：按行对应 paragraph，每个 paragraph 内部用"字符最多的 run"作样式锚点
        String[] newLines = replacement.split("\n", -1);
        for (int pi = 0; pi < paragraphs.size(); pi++) {
            replaceParagraphFully(paragraphs.get(pi), pi < newLines.length ? newLines[pi] : "");
        }
        appliedCount[0]++;
    }

    /**
     * 把整个 paragraph 内容替换成 newText，保留"字符数最多的 run"的样式作为基准。
     * 全段替换跟 marker 替换不同——这里不在乎"哪个 run 包含标记"，只关心"主体文字的视觉"。
     */
    private void replaceParagraphFully(XSLFTextParagraph paragraph, String newText) {
        List<XSLFTextRun> runs = paragraph.getTextRuns();
        if (runs.isEmpty()) {
            return;
        }
        int anchorIdx = 0;
        int maxLen = -1;
        for (int i = 0; i < runs.size(); i++) {
            String raw = runs.get(i).getRawText();
            int len = raw == null ? 0 : raw.length();
            if (len > maxLen) {
                maxLen = len;
                anchorIdx = i;
            }
        }
        for (int i = 0; i < runs.size(); i++) {
            if (i == anchorIdx) {
                runs.get(i).setText(newText);
            } else {
                runs.get(i).setText("");
            }
        }
    }
}
