package com.eduspark.eduspark.service.impl;

import com.eduspark.eduspark.dto.chat.TeachingBlueprint;
import com.eduspark.eduspark.dto.chat.TeachingCore;
import com.eduspark.eduspark.dto.courseware.PptDeckPlan;
import com.eduspark.eduspark.dto.courseware.PptGenerateRequest;
import com.eduspark.eduspark.dto.courseware.PptGenerateResponse;
import com.eduspark.eduspark.dto.courseware.PptSlidePlan;
import com.eduspark.eduspark.dto.courseware.TemplateFillPlan;
import com.eduspark.eduspark.dto.pptworkspace.PptDocumentResponse;
import com.eduspark.eduspark.dto.pptworkspace.PptKnowledgeSourceItem;
import com.eduspark.eduspark.dto.pptworkspace.PptSlideProgressEvent;
import com.eduspark.eduspark.dto.pptworkspace.PptStreamDeltaResponse;
import com.eduspark.eduspark.dto.pptworkspace.PptWorkspaceCardData;
import com.eduspark.eduspark.exception.BusinessException;
import com.eduspark.eduspark.exception.ErrorCode;
import com.eduspark.eduspark.mapper.PptDocumentMapper;
import com.eduspark.eduspark.pojo.entity.ChatSession;
import com.eduspark.eduspark.pojo.entity.PptDocument;
import com.eduspark.eduspark.pojo.entity.PptTemplate;
import com.eduspark.eduspark.service.IChatSessionService;
import com.eduspark.eduspark.service.ICoursewareService;
import com.eduspark.eduspark.service.IPptBlueprintEnrichmentService;
import com.eduspark.eduspark.service.IPptTemplateService;
import com.eduspark.eduspark.service.IPptThumbnailService;
import com.eduspark.eduspark.service.IPptWorkspaceService;
import com.eduspark.eduspark.service.IPptWorkspaceStreamService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@Slf4j
@Service
public class PptWorkspaceServiceImpl implements IPptWorkspaceService {

    private static final int STREAM_PERSIST_STEP = 180;
    private static final long STREAM_PERSIST_INTERVAL_MS = 1_000L;
    /** RENDERING 阶段心跳间隔，给前端定时反馈"AI 还在思考"，避免误以为卡死。 */
    private static final long RENDER_HEARTBEAT_INTERVAL_SEC = 6L;
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<List<PptSlideProgressEvent>> SLIDE_PROGRESS_LIST_TYPE = new TypeReference<>() {
    };
    /**
     * PPT 标题层级的风格元词过滤（比 RAG 那边保守：保留"主题/教学"避免误伤教学主题词）。
     * 用户的 topic 字段经常被"手绘教师说课PPT"这类模板风格污染，直接当 title 显示会很难看。
     */
    private static final Pattern TITLE_STYLE_NOISE = Pattern.compile(
            "(?i)(手绘|说课|课件|模板|风格|配色|样式|教师|PPT|ppt)"
    );

    private final PptDocumentMapper pptDocumentMapper;
    private final IPptBlueprintEnrichmentService blueprintEnrichmentService;
    private final ICoursewareService coursewareService;
    private final IChatSessionService chatSessionService;
    private final IPptWorkspaceStreamService workspaceStreamService;
    private final IPptTemplateService pptTemplateService;
    private final IPptThumbnailService thumbnailService;
    private final Executor pptThumbnailExecutor;
    private final ObjectMapper objectMapper;

    @Value("${ppt.reveal.skeleton-delay-ms:250}")
    private long revealSkeletonDelayMs;
    @Value("${ppt.reveal.background-delay-ms:350}")
    private long revealBackgroundDelayMs;
    @Value("${ppt.reveal.title-delay-ms:200}")
    private long revealTitleDelayMs;
    @Value("${ppt.reveal.bullet-delay-ms:120}")
    private long revealBulletDelayMs;
    @Value("${ppt.reveal.completion-delay-ms:150}")
    private long revealCompletionDelayMs;
    @Value("${courseware.storage.local-path:./data/courseware}")
    private String localStoragePath;

    public PptWorkspaceServiceImpl(PptDocumentMapper pptDocumentMapper,
                                   IPptBlueprintEnrichmentService blueprintEnrichmentService,
                                   ICoursewareService coursewareService,
                                   IChatSessionService chatSessionService,
                                   IPptWorkspaceStreamService workspaceStreamService,
                                   IPptTemplateService pptTemplateService,
                                   IPptThumbnailService thumbnailService,
                                   @Qualifier("pptThumbnailExecutor") Executor pptThumbnailExecutor,
                                   ObjectMapper objectMapper) {
        this.pptDocumentMapper = pptDocumentMapper;
        this.blueprintEnrichmentService = blueprintEnrichmentService;
        this.coursewareService = coursewareService;
        this.chatSessionService = chatSessionService;
        this.workspaceStreamService = workspaceStreamService;
        this.pptTemplateService = pptTemplateService;
        this.thumbnailService = thumbnailService;
        this.pptThumbnailExecutor = pptThumbnailExecutor;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public PptWorkspaceCardData createWorkspace(ChatSession session, TeachingBlueprint blueprint) {
        TemplateInfo templateInfo = resolveTemplateInfo(extractTemplateId(blueprint));
        PptDocument document = PptDocument.builder()
                .sessionId(session.getId())
                .userId(session.getUserId())
                .title(resolveTitle(session, blueprint))
                .status(PptDocument.Status.PREPARING)
                .summary("系统会先检索知识库、补全蓝图，再流式生成页面方案和最终 PPT。")
                .templateId(templateInfo.templateId())
                .templateName(templateInfo.templateName())
                .sourceBlueprintJson(writeBlueprint(blueprint))
                .planningMarkdown("")
                .build();
        pptDocumentMapper.insert(document);
        return toCardData(document);
    }

    @Override
    public void generateInitialDocument(Long sessionId, TeachingBlueprint blueprint) {
        PptDocument document = pptDocumentMapper.selectLatestBySessionId(sessionId);
        if (document == null) {
            log.warn("PPT workspace missing for session {}", sessionId);
            return;
        }

        try {
            updateDocumentStatus(document, PptDocument.Status.RETRIEVING, "正在检索知识库并整理相关参考资料...", true);
            updateDocumentStatus(document, PptDocument.Status.ENRICHING, "正在补全 PPT 蓝图并融合检索结果...", true);

            TeachingBlueprint enrichedBlueprint = blueprintEnrichmentService.enrichBlueprint(document.getUserId(), blueprint);
            document.setEnrichedBlueprintJson(writeBlueprint(enrichedBlueprint));
            applyTemplateInfo(document, resolveTemplateInfo(extractTemplateId(enrichedBlueprint)));
            pptDocumentMapper.updateById(document);
            workspaceStreamService.publishSnapshot(document.getId(), toResponse(document));

            PptGenerateRequest request = buildPptRequest(enrichedBlueprint);
            PptTemplate resolvedTemplate = resolveTemplateForRequest(request.getTemplateId());

            // 决策（PPT 链路收口，可行性优先）：工作区统一走「全替换」一条链路。
            // 必须先选一个带 pptx 文件的模板，否则要求用户先选模板；无模板的 legacy 兜底、
            // 以及 markers 精确占位符替换两条路径已暂时收起（generateWithLegacyLayout /
            // generateWithTemplateMarkers / hasParsedMarkers 方法保留在下方，便于回退）。
            if (!hasText(request.getTemplateId())) {
                throw new IllegalStateException("请先选择一个 PPT 模板再生成。系统会以该模板为底版，按主题改写文字并保留原有版式、配色与图片。");
            }
            if (resolvedTemplate == null) {
                throw new IllegalStateException("您选择的 PPT 模板已下架或不存在，请重新选择一个模板");
            }
            if (!hasText(resolvedTemplate.getTemplateFilePath())) {
                throw new IllegalStateException("您选择的 PPT 模板尚未上传 pptx 文件，无法生成。请联系管理员或更换模板");
            }

            // 模式 B：上传即模板——AI 读取 pptx 全部文本块，按教学主题改写，原图片/版式/配色完整保留。
            // 所有选中的模板（无论是否含 {{标记}}）一律走这条，工作区只此一条生成链路。
            generateWithFullReplacement(document, request, resolvedTemplate, sessionId);

        } catch (Exception e) {
            log.error("Generate ppt workspace failed: sessionId={}, documentId={}", sessionId, document.getId(), e);
            document.setErrorMessage(e.getMessage());
            updateDocumentStatus(document, PptDocument.Status.FAILED, "PPT 生成失败，请稍后重试。", false);
            chatSessionService.updateGenerationStatus(
                    sessionId,
                    ChatSession.GenerationStatus.FAILED,
                    "ppt_workspace_failed"
            );
            saveAssistantMessage(sessionId, "PPT 生成失败，请稍后重试。");
            workspaceStreamService.publishFailed(document.getId(), toResponse(document));
        }
    }

    private void generateWithTemplateMarkers(PptDocument document, PptGenerateRequest request,
                                             PptTemplate template, Long sessionId) {
        updateDocumentStatus(document, PptDocument.Status.PLANNING, "正在根据模板结构生成文案...", true);

        StringBuilder streamedContent = new StringBuilder();
        long[] lastPersistAt = {System.currentTimeMillis()};
        int[] lastPersistLength = {0};
        String generatedFill = coursewareService.streamTemplateFillPlanning(request, template, chunk -> {
            if (!hasText(chunk)) {
                return;
            }
            streamedContent.append(chunk);
            workspaceStreamService.publishContentDelta(
                    document.getId(),
                    PptStreamDeltaResponse.builder()
                            .documentId(document.getId())
                            .delta(chunk)
                            .build()
            );
            maybePersistPlanningProgress(document, streamedContent.toString(), lastPersistLength, lastPersistAt);
        });

        String planningMarkdown = hasText(generatedFill) ? generatedFill : streamedContent.toString();
        persistPlanningMarkdown(document, planningMarkdown);

        TemplateFillPlan fillPlan = coursewareService.buildTemplateFillPlan(planningMarkdown);
        try {
            document.setPlanJson(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(fillPlan));
        } catch (Exception e) {
            log.warn("Failed to serialize fill plan", e);
        }
        pptDocumentMapper.updateById(document);
        workspaceStreamService.publishSnapshot(document.getId(), toResponse(document));

        updateDocumentStatus(document, PptDocument.Status.RENDERING, "正在替换模板标记并生成 PPT...", true);
        PptGenerateResponse response = coursewareService.renderTemplatedPpt(request, template, fillPlan);
        if (!response.isSuccess()) {
            throw new IllegalStateException(valueOrDefault(response.getError(), "PPT 渲染失败"));
        }

        revealSlidesProgressively(document, buildSlidesForRevealFromFillPlan(fillPlan), template, true);
        completeGeneration(document, response, sessionId, "PPT 已根据模板生成完成，可在右侧查看并下载文件。");
    }

    private void generateWithLegacyLayout(PptDocument document, PptGenerateRequest request, Long sessionId) {
        updateDocumentStatus(document, PptDocument.Status.PLANNING, "正在流式生成页级蓝图...", true);
        StringBuilder streamedContent = new StringBuilder();
        long[] lastPersistAt = {System.currentTimeMillis()};
        int[] lastPersistLength = {0};
        String generatedPlanning = coursewareService.streamPptPlanning(request, chunk -> {
            if (!hasText(chunk)) {
                return;
            }
            streamedContent.append(chunk);
            workspaceStreamService.publishContentDelta(
                    document.getId(),
                    PptStreamDeltaResponse.builder()
                            .documentId(document.getId())
                            .delta(chunk)
                            .build()
            );
            maybePersistPlanningProgress(document, streamedContent.toString(), lastPersistLength, lastPersistAt);
        });

        String planningMarkdown = hasText(generatedPlanning) ? generatedPlanning : streamedContent.toString();
        persistPlanningMarkdown(document, planningMarkdown);
        workspaceStreamService.publishSnapshot(document.getId(), toResponse(document));

        PptDeckPlan plan = coursewareService.buildPptPlan(request, planningMarkdown);
        document.setPlanJson(writePlan(plan));
        pptDocumentMapper.updateById(document);

        updateDocumentStatus(document, PptDocument.Status.RENDERING, "正在渲染 PPT 文件并准备下载...", true);
        PptGenerateResponse response = coursewareService.renderPpt(request, plan);
        if (!response.isSuccess()) {
            throw new IllegalStateException(valueOrDefault(response.getError(), "PPT 渲染失败"));
        }

        revealSlidesProgressively(document, plan != null ? plan.getSlides() : null, null, true);
        completeGeneration(document, response, sessionId, "PPT 已生成完成，右侧工作区可以查看最终蓝图、结构化页面和下载文件。");
    }

    /**
     * 模式 B：上传即模板——AI 看 pptx 全部文本块按主题改写，原图片/版式/配色保留。
     * 不走 streamPptPlanning / buildPptPlan 那条"AI 重新规划版面"的老路。
     */
    private void generateWithFullReplacement(PptDocument document, PptGenerateRequest request,
                                             PptTemplate template, Long sessionId) {
        updateDocumentStatus(document, PptDocument.Status.PLANNING, "正在阅读模板并按主题改写所有文本...", true);

        // FullReplacement 没有"流式 planning 文本"——LLM 是一次性返回 JSON。
        // 给前端工作区一个静态摘要文案，方便 SSE 信道不被空数据卡住。
        String planningSummary = "AI 正在按教学主题改写模板中的所有文本块，原 pptx 的图片、版式、配色将完整保留。";
        persistPlanningMarkdown(document, planningSummary);
        workspaceStreamService.publishContentDelta(
                document.getId(),
                PptStreamDeltaResponse.builder()
                        .documentId(document.getId())
                        .delta(planningSummary)
                        .build()
        );

        updateDocumentStatus(document, PptDocument.Status.RENDERING, "正在替换模板文字并生成 PPT...", true);

        // FullReplacement 的 LLM 是一次性吃 300+ 文本块的同步调用，通常要 1-3 分钟，
        // 期间没有任何 slide-level 事件。开一个心跳定时任务向前端推 status，
        // 让 UI 知道"AI 还在思考"而不是"接口卡死"。
        ScheduledExecutorService heartbeat = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "ppt-render-heartbeat-" + document.getId());
            t.setDaemon(true);
            return t;
        });
        long renderStartMs = System.currentTimeMillis();
        heartbeat.scheduleAtFixedRate(
                () -> publishRenderingHeartbeat(document, renderStartMs),
                RENDER_HEARTBEAT_INTERVAL_SEC,
                RENDER_HEARTBEAT_INTERVAL_SEC,
                TimeUnit.SECONDS
        );

        PptGenerateResponse response;
        try {
            response = coursewareService.renderFullReplacementPpt(request, template);
        } finally {
            heartbeat.shutdownNow();
        }

        if (!response.isSuccess()) {
            throw new IllegalStateException(valueOrDefault(response.getError(), "PPT 渲染失败"));
        }

        // FullReplacement 现在会把"按页聚合的真实改写内容"塞进 response.plan.slides。
        // 优先用它喂 reveal（前端就能看到每页真实文字）；缺失时才回退到占位。
        List<PptSlidePlan> realSlides = response.getPlan() != null ? response.getPlan().getSlides() : null;
        List<PptSlidePlan> slidesForReveal = (realSlides != null && !realSlides.isEmpty())
                ? realSlides
                : buildSlidesForRevealFromBlueprint(request);
        // 模式 B 下，模板封面图只贴在第 1 页（其他页留空，前端走白底卡片显示真实文字），
        // 避免"每页都是同一张封面图"的误导视觉。
        revealSlidesProgressively(document, slidesForReveal, template, false);
        completeGeneration(document, response, sessionId, "PPT 已按模板视觉生成完成。AI 已改写所有文字内容，原图片和版式均保留。");
    }

    /**
     * 心跳事件：复用现有的 SSE status 通道。summary 只是临时覆盖给前端展示，不写库。
     */
    private void publishRenderingHeartbeat(PptDocument document, long renderStartMs) {
        try {
            long elapsedSec = (System.currentTimeMillis() - renderStartMs) / 1000;
            PptDocumentResponse snapshot = toResponse(document);
            snapshot.setSummary(String.format(
                    "AI 正在按主题改写模板里的全部文字（已 %d 秒，通常需要 1-3 分钟）...",
                    elapsedSec));
            workspaceStreamService.publishStatus(document.getId(), snapshot);
        } catch (Exception e) {
            log.debug("Rendering heartbeat publish failed: {}", e.getMessage());
        }
    }

    /**
     * 逐页揭幕：渲染完成后，按"骨架 → 背景 → 标题 → bullets 逐条 → 完成"的节奏
     * 向前端推送 slide-level 事件。所有页的快照同步落盘到 slides_progress_json，
     * 供断线重连时通过 snapshot 直接回放当前进度。
     *
     * @param useTemplateCoverOnAllSlides true=每页都贴模板封面图（模式 A/C，跟模板视觉强绑定时）；
     *                                     false=只第 1 页贴封面（模式 B FullReplacement，避免"每页都是同一张封面图"的误导）。
     */
    private void revealSlidesProgressively(PptDocument document,
                                           List<PptSlidePlan> slides,
                                           PptTemplate template,
                                           boolean useTemplateCoverOnAllSlides) {
        if (slides == null || slides.isEmpty()) {
            return;
        }

        String templateCover = template != null ? valueOrEmpty(template.getCoverUrl()) : "";
        int total = slides.size();
        List<PptSlideProgressEvent> progressList = new ArrayList<>(total);

        for (int index = 0; index < total; index++) {
            PptSlidePlan slide = slides.get(index);
            if (slide == null) {
                continue;
            }
            int slideNo = slide.getSlideNo() != null ? slide.getSlideNo() : (index + 1);
            String backgroundImageUrl = (useTemplateCoverOnAllSlides || slideNo == 1) ? templateCover : "";

            PptSlideProgressEvent event = PptSlideProgressEvent.builder()
                    .slideNo(slideNo)
                    .total(total)
                    .stage(PptSlideProgressEvent.Stage.SKELETON)
                    .layout(slide.getLayout())
                    .slotLayout(slide.getSlotLayout())
                    .build();
            workspaceStreamService.publishSlideSkeleton(document.getId(), event);
            sleepQuietly(revealSkeletonDelayMs);

            event.setStage(PptSlideProgressEvent.Stage.BACKGROUND_READY);
            event.setBackgroundImageUrl(backgroundImageUrl);
            workspaceStreamService.publishSlideBackground(document.getId(), event);
            sleepQuietly(revealBackgroundDelayMs);

            event.setStage(PptSlideProgressEvent.Stage.CONTENT_FILLING);
            if (hasText(slide.getTitle())) {
                workspaceStreamService.publishSlideContentDelta(
                        document.getId(),
                        PptStreamDeltaResponse.builder()
                                .documentId(document.getId())
                                .slideNo(slideNo)
                                .field("title")
                                .value(slide.getTitle())
                                .append(false)
                                .build()
                );
                event.setTitle(slide.getTitle());
                sleepQuietly(revealTitleDelayMs);
            }

            List<String> bullets = slide.getBullets();
            if (bullets != null && !bullets.isEmpty()) {
                List<String> accumulated = new ArrayList<>(bullets.size());
                for (String bullet : bullets) {
                    if (!hasText(bullet)) {
                        continue;
                    }
                    workspaceStreamService.publishSlideContentDelta(
                            document.getId(),
                            PptStreamDeltaResponse.builder()
                                    .documentId(document.getId())
                                    .slideNo(slideNo)
                                    .field("bullet")
                                    .value(bullet)
                                    .append(true)
                                    .build()
                    );
                    accumulated.add(bullet);
                    sleepQuietly(revealBulletDelayMs);
                }
                event.setBullets(accumulated);
            }

            if (hasText(slide.getVisualFocus())) {
                workspaceStreamService.publishSlideContentDelta(
                        document.getId(),
                        PptStreamDeltaResponse.builder()
                                .documentId(document.getId())
                                .slideNo(slideNo)
                                .field("visualFocus")
                                .value(slide.getVisualFocus())
                                .append(false)
                                .build()
                );
                event.setVisualFocus(slide.getVisualFocus());
            }
            if (hasText(slide.getSpeakerNotes())) {
                workspaceStreamService.publishSlideContentDelta(
                        document.getId(),
                        PptStreamDeltaResponse.builder()
                                .documentId(document.getId())
                                .slideNo(slideNo)
                                .field("speakerNotes")
                                .value(slide.getSpeakerNotes())
                                .append(false)
                                .build()
                );
                event.setSpeakerNotes(slide.getSpeakerNotes());
            }

            event.setStage(PptSlideProgressEvent.Stage.COMPLETED);
            event.setSlidePlan(slide);
            workspaceStreamService.publishSlideCompleted(document.getId(), event);
            progressList.add(event);

            // 每页揭幕完毕持久化一次，重连可以从这里读到当前进度
            document.setSlidesProgressJson(writeSlidesProgress(progressList));
            pptDocumentMapper.updateById(document);

            sleepQuietly(revealCompletionDelayMs);
        }
    }

    /**
     * 模式 A：fillPlan 只有 markerValues，没有结构化标题/bullets。
     * 这里启发式地拆字段——key 含 "title" 当标题，其他 value 作为 bullets——
     * 让 UI 至少能演示出"逐页揭幕"的视觉节奏。
     */
    private List<PptSlidePlan> buildSlidesForRevealFromFillPlan(TemplateFillPlan fillPlan) {
        if (fillPlan == null || fillPlan.getSlides() == null || fillPlan.getSlides().isEmpty()) {
            return List.of();
        }
        List<PptSlidePlan> slides = new ArrayList<>();
        int idx = 0;
        for (var slideFill : fillPlan.getSlides()) {
            idx++;
            Map<String, String> markers = slideFill != null ? slideFill.getMarkerValues() : null;
            String title = "";
            List<String> bullets = new ArrayList<>();
            if (markers != null) {
                for (Map.Entry<String, String> entry : markers.entrySet()) {
                    String key = entry.getKey() == null ? "" : entry.getKey().toLowerCase(Locale.ROOT);
                    String value = entry.getValue();
                    if (!hasText(value)) {
                        continue;
                    }
                    if (!hasText(title) && (key.contains("title") || key.contains("heading") || key.contains("subject"))) {
                        title = value;
                    } else {
                        bullets.add(value);
                    }
                }
            }
            slides.add(PptSlidePlan.builder()
                    .slideNo(idx)
                    .title(hasText(title) ? title : "第 " + idx + " 页")
                    .layout(idx == 1 ? "cover" : "content")
                    .bullets(bullets)
                    .build());
        }
        return slides;
    }

    /**
     * 模式 B：FullReplacement 没有任何页级结构信息，按 slideCount 造一组占位页，
     * 仅用于让前端动画跑起来——视觉上每页都是模板封面背景 + 主题标题占位。
     */
    private List<PptSlidePlan> buildSlidesForRevealFromBlueprint(PptGenerateRequest request) {
        int count = request != null && request.getSlideCount() != null && request.getSlideCount() > 0
                ? request.getSlideCount()
                : 6;
        String title = request != null ? valueOrDefault(request.getTitle(), "课件") : "课件";
        List<PptSlidePlan> slides = new ArrayList<>(count);
        for (int i = 1; i <= count; i++) {
            slides.add(PptSlidePlan.builder()
                    .slideNo(i)
                    .title(i == 1 ? title : title + " · 第 " + i + " 页")
                    .layout(i == 1 ? "cover" : "content")
                    .build());
        }
        return slides;
    }

    private void sleepQuietly(long millis) {
        if (millis <= 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private String writeSlidesProgress(List<PptSlideProgressEvent> progressList) {
        try {
            return objectMapper.writeValueAsString(progressList);
        } catch (Exception e) {
            log.warn("Failed to serialize slides progress", e);
            return "";
        }
    }

    private List<PptSlideProgressEvent> readSlidesProgress(String json) {
        if (!hasText(json)) {
            return List.of();
        }
        try {
            List<PptSlideProgressEvent> parsed = objectMapper.readValue(json, SLIDE_PROGRESS_LIST_TYPE);
            return parsed != null ? parsed : List.of();
        } catch (Exception e) {
            log.warn("Failed to parse slides progress json", e);
            return List.of();
        }
    }

    private void completeGeneration(PptDocument document, PptGenerateResponse response,
                                    Long sessionId, String message) {
        document.setDownloadUrl(response.getDownloadUrl());
        document.setExportFilePath(response.getFilePath());
        document.setFileName(response.getFileName());
        document.setErrorMessage(null);
        updateDocumentStatus(document, PptDocument.Status.COMPLETED,
                "PPT 已生成完成，可在右侧查看页面结构并下载文件。", false);

        chatSessionService.updateGenerationStatus(
                sessionId,
                ChatSession.GenerationStatus.COMPLETED,
                "ppt_workspace_ready"
        );
        saveAssistantMessage(sessionId, message);
        workspaceStreamService.publishCompleted(document.getId(), toResponse(document));

        // 异步把生成好的 pptx 用 LibreOffice 转 PDF → PDFBox 渲染每页 PNG → OSS，
        // 完成后通过 SSE 推 slide.background 让前端缩略图与主区域显示真实页面预览。
        scheduleThumbnailRender(document.getId(), response.getFilePath());
    }

    private void scheduleThumbnailRender(Long documentId, String relativeFilePath) {
        if (documentId == null || !hasText(relativeFilePath) || !thumbnailService.isEnabled()) {
            return;
        }
        pptThumbnailExecutor.execute(() -> renderAndApplyThumbnails(documentId, relativeFilePath));
    }

    private void renderAndApplyThumbnails(Long documentId, String relativeFilePath) {
        try {
            Path pptxPath = Paths.get(localStoragePath).toAbsolutePath().resolve(relativeFilePath).normalize();
            List<String> urls = thumbnailService.renderSlideThumbnails(pptxPath, documentId);
            if (urls.isEmpty()) {
                return;
            }
            applyThumbnailUrlsToProgress(documentId, urls);
        } catch (Exception e) {
            log.warn("Async thumbnail render failed: documentId={}", documentId, e);
        }
    }

    private void applyThumbnailUrlsToProgress(Long documentId, List<String> urls) {
        PptDocument fresh = pptDocumentMapper.selectById(documentId);
        if (fresh == null) {
            return;
        }
        List<PptSlideProgressEvent> progressList = new ArrayList<>(readSlidesProgress(fresh.getSlidesProgressJson()));
        if (progressList.isEmpty()) {
            return;
        }

        boolean changed = false;
        for (PptSlideProgressEvent event : progressList) {
            if (event == null || event.getSlideNo() == null) continue;
            int idx = event.getSlideNo() - 1;
            if (idx < 0 || idx >= urls.size()) continue;
            String url = urls.get(idx);
            if (!hasText(url)) continue;
            if (url.equals(event.getBackgroundImageUrl())) continue;
            event.setBackgroundImageUrl(url);
            changed = true;
            workspaceStreamService.publishSlideBackground(documentId, event);
        }
        if (changed) {
            fresh.setSlidesProgressJson(writeSlidesProgress(progressList));
            pptDocumentMapper.updateById(fresh);
        }
    }

    @Override
    public PptDocumentResponse getDocument(Long documentId, Long userId) {
        return toResponse(getOwnedDocument(documentId, userId));
    }

    @Override
    @Transactional
    public PptDocumentResponse exportDocument(Long documentId, Long userId) {
        PptDocument document = getOwnedDocument(documentId, userId);
        requireCompletedDocument(document);

        TeachingBlueprint blueprint = readBlueprint(document.getEnrichedBlueprintJson(), document.getSourceBlueprintJson());
        PptGenerateRequest request = buildPptRequest(blueprint);
        PptDeckPlan plan = readPlan(document.getPlanJson());
        if (plan == null || plan.getSlides() == null || plan.getSlides().isEmpty()) {
            String planningMarkdown = normalizePlanningMarkdown(document.getPlanningMarkdown(), request);
            plan = coursewareService.buildPptPlan(request, planningMarkdown);
            document.setPlanJson(writePlan(plan));
        }

        PptGenerateResponse response = coursewareService.renderPpt(request, plan);
        if (!response.isSuccess()) {
            throw new BusinessException(ErrorCode.SERVER_ERROR, response.getError());
        }

        document.setDownloadUrl(response.getDownloadUrl());
        document.setExportFilePath(response.getFilePath());
        document.setFileName(response.getFileName());
        pptDocumentMapper.updateById(document);

        PptDocumentResponse payload = toResponse(document);
        workspaceStreamService.publishSnapshot(documentId, payload);
        return payload;
    }

    private PptDocument getOwnedDocument(Long documentId, Long userId) {
        PptDocument document = pptDocumentMapper.selectOwnedById(documentId, userId);
        if (document == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "PPT 工作区不存在");
        }
        return document;
    }

    private void requireCompletedDocument(PptDocument document) {
        if (!PptDocument.Status.COMPLETED.equalsIgnoreCase(document.getStatus())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "当前 PPT 尚未生成完成，暂时不能导出");
        }
    }

    private void updateDocumentStatus(PptDocument document, String status, String summary, boolean publishImmediately) {
        document.setStatus(status);
        document.setSummary(summary);
        pptDocumentMapper.updateById(document);
        if (publishImmediately) {
            workspaceStreamService.publishStatus(document.getId(), toResponse(document));
        }
    }

    private void maybePersistPlanningProgress(PptDocument document,
                                              String currentContent,
                                              int[] lastPersistLength,
                                              long[] lastPersistAt) {
        int deltaLength = currentContent.length() - lastPersistLength[0];
        long now = System.currentTimeMillis();
        if (deltaLength < STREAM_PERSIST_STEP && now - lastPersistAt[0] < STREAM_PERSIST_INTERVAL_MS) {
            return;
        }

        persistPlanningMarkdown(document, currentContent);
        lastPersistLength[0] = currentContent.length();
        lastPersistAt[0] = now;
    }

    private void persistPlanningMarkdown(PptDocument document, String content) {
        document.setPlanningMarkdown(valueOrEmpty(content));
        pptDocumentMapper.updateById(document);
    }

    private void saveAssistantMessage(Long sessionId, String content) {
        chatSessionService.saveMessage(
                sessionId,
                "assistant",
                content,
                ChatSession.Mode.PPT,
                0,
                "PPT工作区",
                0,
                null,
                null
        );
    }

    private PptGenerateRequest buildPptRequest(TeachingBlueprint blueprint) {
        TeachingCore core = ensureCore(blueprint);
        return PptGenerateRequest.builder()
                .title(core.getTopic())
                .subject(core.getSubject())
                .grade(core.getGrade())
                .knowledgePoints(extractStringList(blueprint, "knowledgePoints"))
                .slideCount(parseInteger(blueprint.getExtension("slideCount")) != null
                        ? parseInteger(blueprint.getExtension("slideCount"))
                        : 10)
                .style(valueOrDefault(asString(blueprint.getExtension("style")), "简洁课堂"))
                .referenceText(asString(blueprint.getExtension("referenceText")))
                .userDescription(buildUserDescription(blueprint))
                .templateId(extractTemplateId(blueprint))
                .build();
    }

    private String buildUserDescription(TeachingBlueprint blueprint) {
        List<String> lines = new ArrayList<>();
        appendLine(lines, "用户补充", asString(blueprint.getExtension("notes")));
        appendLine(lines, "重点内容", joinValues(extractStringList(blueprint, "keyPoints")));
        appendLine(lines, "知识点", joinValues(extractStringList(blueprint, "knowledgePoints")));
        appendLine(lines, "约束条件", joinValues(extractStringList(blueprint, "userConstraints")));
        appendLine(lines, "附件参考", joinValues(extractStringList(blueprint, "attachmentNames")));
        appendLine(lines, "视觉风格", asString(blueprint.getExtension("visualStyle")));
        return String.join("\n", lines);
    }

    private PptWorkspaceCardData toCardData(PptDocument document) {
        return PptWorkspaceCardData.builder()
                .documentId(document.getId())
                .mode(ChatSession.Mode.PPT)
                .modeName("PPT")
                .title(document.getTitle())
                .status(document.getStatus())
                .statusText(resolveStatusText(document.getStatus()))
                .summary(document.getSummary())
                .templateId(document.getTemplateId())
                .templateName(document.getTemplateName())
                .build();
    }

    private PptDocumentResponse toResponse(PptDocument document) {
        Map<String, Object> sourceBlueprint = readBlueprintMap(document.getSourceBlueprintJson());
        Map<String, Object> enrichedBlueprint = readBlueprintMap(
                hasText(document.getEnrichedBlueprintJson()) ? document.getEnrichedBlueprintJson() : document.getSourceBlueprintJson()
        );
        PptDeckPlan plan = readPlan(document.getPlanJson());

        return PptDocumentResponse.builder()
                .documentId(document.getId())
                .title(document.getTitle())
                .status(document.getStatus())
                .statusText(resolveStatusText(document.getStatus()))
                .summary(document.getSummary())
                .planningMarkdown(valueOrEmpty(document.getPlanningMarkdown()))
                .downloadUrl(document.getDownloadUrl())
                .fileName(document.getFileName())
                .errorMessage(document.getErrorMessage())
                .templateId(document.getTemplateId())
                .templateName(document.getTemplateName())
                .sourceBlueprint(sourceBlueprint)
                .enrichedBlueprint(enrichedBlueprint)
                .knowledgeSources(extractKnowledgeSources(enrichedBlueprint))
                .enrichmentHighlights(buildEnrichmentHighlights(sourceBlueprint, enrichedBlueprint))
                .outline(extractOutline(plan))
                .plan(plan)
                .slidesProgress(readSlidesProgress(document.getSlidesProgressJson()))
                .updateTime(document.getUpdateTime())
                .build();
    }

    private String resolveStatusText(String status) {
        if (!hasText(status)) {
            return "准备中";
        }
        return switch (status.toLowerCase(Locale.ROOT)) {
            case PptDocument.Status.RETRIEVING -> "检索资料中";
            case PptDocument.Status.ENRICHING -> "补全蓝图中";
            case PptDocument.Status.PLANNING -> "生成页面方案中";
            case PptDocument.Status.RENDERING -> "渲染 PPT 中";
            case PptDocument.Status.COMPLETED -> "已完成";
            case PptDocument.Status.FAILED -> "生成失败";
            default -> "准备中";
        };
    }

    private String writeBlueprint(TeachingBlueprint blueprint) {
        try {
            return objectMapper.writeValueAsString(blueprint.toFlatMap());
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.SERVER_ERROR, "蓝图序列化失败");
        }
    }

    private String writePlan(PptDeckPlan plan) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(plan);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.SERVER_ERROR, "PPT 方案序列化失败");
        }
    }

    private TeachingBlueprint readBlueprint(String preferredJson, String fallbackJson) {
        try {
            String target = hasText(preferredJson) ? preferredJson : fallbackJson;
            if (!hasText(target)) {
                return TeachingBlueprint.fromLegacyMap(Map.of());
            }
            return TeachingBlueprint.fromLegacyMap(objectMapper.readValue(target, MAP_TYPE));
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.SERVER_ERROR, "蓝图解析失败");
        }
    }

    private PptDeckPlan readPlan(String json) {
        try {
            if (!hasText(json)) {
                return null;
            }
            return objectMapper.readValue(json, PptDeckPlan.class);
        } catch (Exception e) {
            log.warn("Failed to parse ppt plan json", e);
            return null;
        }
    }

    private Map<String, Object> readBlueprintMap(String json) {
        try {
            if (!hasText(json)) {
                return Map.of();
            }
            Map<String, Object> parsed = objectMapper.readValue(json, MAP_TYPE);
            return parsed != null ? parsed : Map.of();
        } catch (Exception e) {
            log.warn("Failed to parse ppt blueprint json", e);
            return Map.of();
        }
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

    private List<PptKnowledgeSourceItem> extractKnowledgeSources(Map<String, Object> enrichedBlueprint) {
        Object raw = enrichedBlueprint.get("knowledgeSources");
        if (!(raw instanceof Collection<?> collection) || collection.isEmpty()) {
            return List.of();
        }

        List<PptKnowledgeSourceItem> result = new ArrayList<>();
        for (Object item : collection) {
            if (!(item instanceof Map<?, ?> itemMap)) {
                continue;
            }
            result.add(PptKnowledgeSourceItem.builder()
                    .fileId(parseLong(itemMap.get("fileId")))
                    .fileName(asString(itemMap.get("fileName")))
                    .excerpt(asString(itemMap.get("excerpt")))
                    .score(parseFloat(itemMap.get("score")))
                    .build());
        }
        return result;
    }

    private List<String> buildEnrichmentHighlights(Map<String, Object> sourceBlueprint, Map<String, Object> enrichedBlueprint) {
        List<String> highlights = new ArrayList<>();
        addListHighlight(highlights, "新增参考资料", sourceBlueprint, enrichedBlueprint, "attachmentNames");
        addListHighlight(highlights, "补充知识点", sourceBlueprint, enrichedBlueprint, "knowledgePoints");
        addListHighlight(highlights, "补充重点内容", sourceBlueprint, enrichedBlueprint, "keyPoints");

        if (!extractKnowledgeSources(enrichedBlueprint).isEmpty()) {
            highlights.add("已结合知识库命中结果补充 PPT 蓝图参考。");
        }
        if (hasText(asString(enrichedBlueprint.get("referenceText")))) {
            highlights.add("最终蓝图已合并参考资料正文片段。");
        }
        return highlights;
    }

    private void addListHighlight(List<String> highlights,
                                  String label,
                                  Map<String, Object> sourceBlueprint,
                                  Map<String, Object> enrichedBlueprint,
                                  String key) {
        List<String> sourceValues = normalizeToStringList(sourceBlueprint.get(key));
        List<String> enrichedValues = normalizeToStringList(enrichedBlueprint.get(key));
        List<String> addedValues = enrichedValues.stream()
                .filter(value -> !sourceValues.contains(value))
                .toList();
        if (!addedValues.isEmpty()) {
            highlights.add(label + "：" + String.join("、", addedValues));
        }
    }

    private List<String> extractStringList(TeachingBlueprint blueprint, String... keys) {
        for (String key : keys) {
            List<String> values = normalizeToStringList(blueprint.getExtension(key));
            if (!values.isEmpty()) {
                return values;
            }
        }
        return List.of();
    }

    private List<String> normalizeToStringList(Object raw) {
        if (raw == null) {
            return List.of();
        }
        if (raw instanceof Collection<?> collection) {
            return collection.stream()
                    .map(this::asString)
                    .filter(this::hasText)
                    .distinct()
                    .toList();
        }

        String value = asString(raw);
        if (!hasText(value)) {
            return List.of();
        }

        return Stream.of(value.split("[,，、\\n]"))
                .map(String::trim)
                .filter(this::hasText)
                .distinct()
                .toList();
    }

    private TeachingCore ensureCore(TeachingBlueprint blueprint) {
        if (blueprint.getCore() == null) {
            blueprint.setCore(new TeachingCore());
        }
        if (blueprint.getExtensions() == null) {
            blueprint.setExtensions(new LinkedHashMap<>());
        }
        return blueprint.getCore();
    }

    private String extractTemplateId(TeachingBlueprint blueprint) {
        return firstNonBlankValue(blueprint, "templateId", "pptTemplateId");
    }

    private String firstNonBlankValue(TeachingBlueprint blueprint, String... keys) {
        for (String key : keys) {
            String value = asString(blueprint.getExtension(key));
            if (hasText(value)) {
                return value;
            }
        }
        return "";
    }

    private String normalizePlanningMarkdown(String value, PptGenerateRequest request) {
        if (hasText(value)) {
            return value.trim();
        }
        return "# " + valueOrDefault(request.getTitle(), "PPT")
                + "\n\n## 第 1 页 封面\n- " + valueOrDefault(request.getSubject(), "课堂主题")
                + "\n\n## 第 2 页 核心内容\n- " + valueOrDefault(joinValues(request.getKnowledgePoints()), "梳理本课重点内容");
    }

    private void appendLine(List<String> lines, String label, String value) {
        if (hasText(value)) {
            lines.add(label + "：" + value);
        }
    }

    private String joinValues(List<String> values) {
        return values == null || values.isEmpty() ? "" : String.join("、", values);
    }

    private String resolveTitle(ChatSession session, TeachingBlueprint blueprint) {
        String topic = blueprint.getCore() != null ? blueprint.getCore().getTopic() : null;
        String cleanedTopic = stripTitleNoise(topic);
        if (hasText(cleanedTopic)) {
            return cleanedTopic;
        }
        return hasText(session.getTitle()) ? session.getTitle() : "PPT 初稿";
    }

    /**
     * 从 topic 里抠掉模板风格描述。例如：
     * "手绘教师说课PPT" → ""（清洗后空，回退到 session.title）
     * "C语言选择排序PPT" → "C语言选择排序"
     */
    private String stripTitleNoise(String value) {
        if (!hasText(value)) {
            return "";
        }
        String cleaned = TITLE_STYLE_NOISE.matcher(value).replaceAll(" ");
        return cleaned.replaceAll("\\s+", " ").trim();
    }

    private TemplateInfo resolveTemplateInfo(String templateId) {
        if (!hasText(templateId)) {
            return new TemplateInfo("", "");
        }
        try {
            PptTemplate template = pptTemplateService.resolveTemplateReference(templateId);
            if (template == null) {
                return new TemplateInfo(templateId, "");
            }
            return new TemplateInfo(templateId, valueOrEmpty(template.getName()));
        } catch (Exception e) {
            log.warn("Resolve template metadata failed: {}", templateId, e);
            return new TemplateInfo(templateId, "");
        }
    }

    private void applyTemplateInfo(PptDocument document, TemplateInfo templateInfo) {
        if (hasText(templateInfo.templateId())) {
            document.setTemplateId(templateInfo.templateId());
        }
        if (hasText(templateInfo.templateName())) {
            document.setTemplateName(templateInfo.templateName());
        }
    }

    private Integer parseInteger(Object value) {
        try {
            if (value == null) {
                return null;
            }
            if (value instanceof Number number) {
                return number.intValue();
            }
            String text = String.valueOf(value).trim();
            return text.isEmpty() ? null : Integer.parseInt(text);
        } catch (Exception e) {
            return null;
        }
    }

    private Long parseLong(Object value) {
        try {
            if (value == null) {
                return null;
            }
            if (value instanceof Number number) {
                return number.longValue();
            }
            String text = String.valueOf(value).trim();
            return text.isEmpty() ? null : Long.parseLong(text);
        } catch (Exception e) {
            return null;
        }
    }

    private Float parseFloat(Object value) {
        try {
            if (value == null) {
                return null;
            }
            if (value instanceof Number number) {
                return number.floatValue();
            }
            String text = String.valueOf(value).trim();
            return text.isEmpty() ? null : Float.parseFloat(text);
        } catch (Exception e) {
            return null;
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String asString(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }

    private String valueOrDefault(String value, String fallback) {
        return hasText(value) ? value : fallback;
    }

    private record TemplateInfo(String templateId, String templateName) {
    }

    private PptTemplate resolveTemplateForRequest(String templateId) {
        if (!hasText(templateId)) {
            return null;
        }
        try {
            return pptTemplateService.resolveTemplateReference(templateId);
        } catch (Exception e) {
            log.warn("Resolve template for request failed: {}", templateId, e);
            return null;
        }
    }

    private boolean hasParsedMarkers(PptTemplate template) {
        if (template == null || !hasText(template.getRenderConfigJson())) {
            return false;
        }
        try {
            com.eduspark.eduspark.dto.ppt.TemplateStructure structure =
                    objectMapper.readValue(template.getRenderConfigJson(),
                            com.eduspark.eduspark.dto.ppt.TemplateStructure.class);
            if (structure.getSlides() == null) {
                return false;
            }
            return structure.getSlides().stream()
                    .anyMatch(s -> s.getMarkers() != null && !s.getMarkers().isEmpty());
        } catch (Exception e) {
            return false;
        }
    }
}
