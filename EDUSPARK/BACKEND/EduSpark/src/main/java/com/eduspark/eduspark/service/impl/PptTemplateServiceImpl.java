package com.eduspark.eduspark.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.eduspark.eduspark.dto.common.PageResponse;
import com.eduspark.eduspark.dto.ppt.PptTemplateAdminResponse;
import com.eduspark.eduspark.dto.ppt.PptTemplateClientResponse;
import com.eduspark.eduspark.dto.ppt.PptTemplatePreParseResponse;
import com.eduspark.eduspark.dto.ppt.PptTemplateSceneRequest;
import com.eduspark.eduspark.dto.ppt.PptTemplateSceneResponse;
import com.eduspark.eduspark.dto.ppt.PptTemplateStyleRequest;
import com.eduspark.eduspark.dto.ppt.PptTemplateStyleResponse;
import com.eduspark.eduspark.dto.ppt.PptTemplateUpsertRequest;
import com.eduspark.eduspark.exception.BusinessException;
import com.eduspark.eduspark.mapper.PptTemplateMapper;
import com.eduspark.eduspark.mapper.PptTemplateSceneMapper;
import com.eduspark.eduspark.mapper.PptTemplateStyleMapper;
import com.eduspark.eduspark.pojo.entity.PptTemplate;
import com.eduspark.eduspark.pojo.entity.PptTemplateScene;
import com.eduspark.eduspark.pojo.entity.PptTemplateStyle;
import com.eduspark.eduspark.service.IFileStorageService;
import com.eduspark.eduspark.service.ILLMService;
import com.eduspark.eduspark.service.IPptTemplateService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.eduspark.eduspark.dto.ppt.TemplateMarkerInfo;
import com.eduspark.eduspark.dto.ppt.TemplateSlideStructure;
import com.eduspark.eduspark.dto.ppt.TemplateStructure;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFGroupShape;
import org.apache.poi.xslf.usermodel.XSLFNotes;
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTable;
import org.apache.poi.xslf.usermodel.XSLFTableCell;
import org.apache.poi.xslf.usermodel.XSLFTableRow;
import org.apache.poi.xslf.usermodel.XSLFTextParagraph;
import org.apache.poi.xslf.usermodel.XSLFTextRun;
import org.apache.poi.xslf.usermodel.XSLFTextShape;

import jakarta.annotation.PostConstruct;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
public class PptTemplateServiceImpl implements IPptTemplateService {

    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {
    };

    private static final Pattern MARKER_PATTERN = Pattern.compile("\\{\\{(.+?)}}");

    private final PptTemplateSceneMapper sceneMapper;
    private final PptTemplateStyleMapper styleMapper;
    private final PptTemplateMapper templateMapper;
    private final IFileStorageService fileStorageService;
    private final ObjectMapper objectMapper;
    /** 可选注入：LLM 不可用时 preParseTemplate 仍能给出 POI 解析结果，仅"AI 建议字段"为空。 */
    private final ObjectProvider<ILLMService> llmServiceProvider;

    @Value("${courseware.storage.local-path:./data/courseware}")
    private String localStoragePath;

    public PptTemplateServiceImpl(PptTemplateSceneMapper sceneMapper,
                                  PptTemplateStyleMapper styleMapper,
                                  PptTemplateMapper templateMapper,
                                  IFileStorageService fileStorageService,
                                  ObjectMapper objectMapper,
                                  ObjectProvider<ILLMService> llmServiceProvider) {
        this.sceneMapper = sceneMapper;
        this.styleMapper = styleMapper;
        this.templateMapper = templateMapper;
        this.fileStorageService = fileStorageService;
        this.objectMapper = objectMapper;
        this.llmServiceProvider = llmServiceProvider;
    }

    /**
     * 应用启动时清理 _pending/ 临时目录。这里的文件是 pre-parse 时落盘但用户最终没提交
     * createTemplate / updateTemplate 留下的孤儿，留着白占磁盘。
     */
    @PostConstruct
    void cleanupPendingDirOnStartup() {
        try {
            Path dir = pendingDir();
            if (!Files.isDirectory(dir)) {
                return;
            }
            try (Stream<Path> entries = Files.list(dir)) {
                long removed = entries
                        .filter(Files::isRegularFile)
                        .mapToLong(this::safeDeleteFileQuiet)
                        .sum();
                if (removed > 0) {
                    log.info("Cleaned {} orphan pptx files under _pending/", removed);
                }
            }
        } catch (Exception e) {
            log.warn("Cleanup _pending dir failed (non-fatal)", e);
        }
    }

    private long safeDeleteFileQuiet(Path path) {
        try {
            return Files.deleteIfExists(path) ? 1 : 0;
        } catch (IOException e) {
            log.debug("Delete file failed: {} - {}", path, e.getMessage());
            return 0;
        }
    }

    private Path templatesDir() {
        return Paths.get(localStoragePath).toAbsolutePath().normalize().resolve("templates");
    }

    private Path pendingDir() {
        return templatesDir().resolve("_pending");
    }

    @Override
    public List<PptTemplateSceneResponse> listAdminScenes(Boolean enabled) {
        return listScenes(enabled);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PptTemplateSceneResponse createScene(PptTemplateSceneRequest request) {
        String sceneCode = requiredCode(request.getSceneCode(), "场景编码不能为空");
        String sceneName = requiredText(request.getSceneName(), "场景名称不能为空");
        ensureSceneCodeUnique(sceneCode, null);
        ensureSceneNameUnique(sceneName, null);

        PptTemplateScene scene = PptTemplateScene.builder()
                .sceneCode(sceneCode)
                .sceneName(sceneName)
                .sort(defaultSort(request.getSort()))
                .enabled(defaultEnabled(request.getEnabled()))
                .build();
        sceneMapper.insert(scene);
        return toSceneResponse(scene);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PptTemplateSceneResponse updateScene(Long id, PptTemplateSceneRequest request) {
        PptTemplateScene existing = getSceneOrThrow(id);
        String sceneCode = requiredCode(request.getSceneCode(), "场景编码不能为空");
        String sceneName = requiredText(request.getSceneName(), "场景名称不能为空");
        ensureSceneCodeUnique(sceneCode, id);
        ensureSceneNameUnique(sceneName, id);

        existing.setSceneCode(sceneCode);
        existing.setSceneName(sceneName);
        existing.setSort(defaultSort(request.getSort()));
        existing.setEnabled(defaultEnabled(request.getEnabled()));
        sceneMapper.updateById(existing);
        return toSceneResponse(getSceneOrThrow(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteScene(Long id) {
        getSceneOrThrow(id);
        boolean hasStyles = styleMapper.selectCount(
                Wrappers.<PptTemplateStyle>lambdaQuery()
                        .eq(PptTemplateStyle::getSceneId, id)
        ) > 0;
        if (hasStyles) {
            throw new BusinessException(400, "该场景下仍存在风格，无法删除");
        }

        boolean hasTemplates = templateMapper.selectCount(
                Wrappers.<PptTemplate>lambdaQuery()
                        .eq(PptTemplate::getSceneId, id)
        ) > 0;
        if (hasTemplates) {
            throw new BusinessException(400, "该场景下仍存在模板，无法删除");
        }

        sceneMapper.deleteById(id);
    }

    @Override
    public List<PptTemplateStyleResponse> listAdminStyles(Long sceneId, Boolean enabled) {
        return listStyles(sceneId, enabled);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PptTemplateStyleResponse createStyle(PptTemplateStyleRequest request) {
        PptTemplateScene scene = getSceneOrThrow(request.getSceneId());
        String styleCode = requiredCode(request.getStyleCode(), "风格编码不能为空");
        String styleName = requiredText(request.getStyleName(), "风格名称不能为空");
        ensureStyleCodeUnique(request.getSceneId(), styleCode, null);
        ensureStyleNameUnique(request.getSceneId(), styleName, null);

        PptTemplateStyle style = PptTemplateStyle.builder()
                .sceneId(scene.getId())
                .styleCode(styleCode)
                .styleName(styleName)
                .sort(defaultSort(request.getSort()))
                .enabled(defaultEnabled(request.getEnabled()))
                .build();
        styleMapper.insert(style);
        return toStyleResponse(style, scene.getSceneName());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PptTemplateStyleResponse updateStyle(Long id, PptTemplateStyleRequest request) {
        PptTemplateStyle existing = getStyleOrThrow(id);
        PptTemplateScene scene = getSceneOrThrow(request.getSceneId());
        String styleCode = requiredCode(request.getStyleCode(), "风格编码不能为空");
        String styleName = requiredText(request.getStyleName(), "风格名称不能为空");
        ensureStyleCodeUnique(request.getSceneId(), styleCode, id);
        ensureStyleNameUnique(request.getSceneId(), styleName, id);

        existing.setSceneId(scene.getId());
        existing.setStyleCode(styleCode);
        existing.setStyleName(styleName);
        existing.setSort(defaultSort(request.getSort()));
        existing.setEnabled(defaultEnabled(request.getEnabled()));
        styleMapper.updateById(existing);
        return toStyleResponse(getStyleOrThrow(id), scene.getSceneName());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteStyle(Long id) {
        getStyleOrThrow(id);
        boolean hasTemplates = templateMapper.selectCount(
                Wrappers.<PptTemplate>lambdaQuery()
                        .eq(PptTemplate::getStyleId, id)
        ) > 0;
        if (hasTemplates) {
            throw new BusinessException(400, "该风格下仍存在模板，无法删除");
        }
        styleMapper.deleteById(id);
    }

    @Override
    public PageResponse<PptTemplateAdminResponse> listAdminTemplates(Long sceneId,
                                                                     Long styleId,
                                                                     Boolean enabled,
                                                                     String keyword,
                                                                     Integer page,
                                                                     Integer size) {
        int currentPage = page == null || page < 1 ? 1 : page;
        int pageSize = size == null || size < 1 ? 10 : Math.min(size, 100);
        Integer enabledValue = toEnabledValue(enabled);

        Page<PptTemplate> mpPage = new Page<>(currentPage, pageSize);
        LambdaQueryWrapper<PptTemplate> wrapper = Wrappers.lambdaQuery();
        wrapper.eq(sceneId != null, PptTemplate::getSceneId, sceneId)
                .eq(styleId != null, PptTemplate::getStyleId, styleId)
                .eq(enabledValue != null, PptTemplate::getEnabled, enabledValue)
                .and(hasText(keyword), q -> q.like(PptTemplate::getName, keyword)
                        .or()
                        .like(PptTemplate::getTemplateCode, keyword)
                        .or()
                        .like(PptTemplate::getEngineTemplateKey, keyword))
                .orderByDesc(PptTemplate::getIsDefault)
                .orderByAsc(PptTemplate::getSort)
                .orderByDesc(PptTemplate::getId);

        IPage<PptTemplate> result = templateMapper.selectPage(mpPage, wrapper);
        Map<Long, PptTemplateScene> sceneMap = sceneMap(result.getRecords().stream()
                .map(PptTemplate::getSceneId)
                .collect(Collectors.toSet()));
        Map<Long, PptTemplateStyle> styleMap = styleMap(result.getRecords().stream()
                .map(PptTemplate::getStyleId)
                .collect(Collectors.toSet()));

        List<PptTemplateAdminResponse> records = result.getRecords().stream()
                .map(template -> toAdminResponse(template, sceneMap, styleMap))
                .toList();

        return PageResponse.of(result.getTotal(), (int) result.getCurrent(), (int) result.getSize(), records);
    }

    @Override
    public PptTemplateAdminResponse getAdminTemplate(Long id) {
        PptTemplate template = getTemplateOrThrow(id);
        Map<Long, PptTemplateScene> sceneMap = sceneMap(List.of(template.getSceneId()));
        Map<Long, PptTemplateStyle> styleMap = styleMap(List.of(template.getStyleId()));
        return toAdminResponse(template, sceneMap, styleMap);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PptTemplateAdminResponse createTemplate(PptTemplateUpsertRequest request) {
        PptTemplateScene scene = getSceneOrThrow(request.getSceneId());
        PptTemplateStyle style = getStyleOrThrow(request.getStyleId());
        validateStyleBelongsToScene(style, scene.getId());

        String templateCode = buildTemplateCode(request.getTemplateCode(), request.getEngineTemplateKey(), request.getName());
        ensureTemplateCodeUnique(templateCode, null);

        // 若前端在新建对话框里走了 pre-parse 流程，pendingFileKey 非空 → 此时把临时 pptx 归档到正式目录。
        String templateFilePath = trimToNull(request.getTemplateFilePath());
        String renderConfigJson = trimToNull(request.getRenderConfigJson());
        if (hasText(request.getPendingFileKey())) {
            if (!hasText(renderConfigJson)) {
                throw new BusinessException(400, "缺少模板解析结果，请重新上传 pptx");
            }
            templateFilePath = movePendingFileToFinal(request.getPendingFileKey(), templateCode);
        }

        PptTemplate template = PptTemplate.builder()
                .templateCode(templateCode)
                .name(requiredText(request.getName(), "模板名称不能为空"))
                .sceneId(scene.getId())
                .styleId(style.getId())
                .coverUrl(trimToNull(request.getCoverUrl()))
                .previewImagesJson(toJsonArray(request.getPreviewImages()))
                .description(trimToNull(request.getDescription()))
                // engineTemplateKey 在第二版下是内部字段，默认跟 templateCode 一致即可。
                .engineTemplateKey(hasText(request.getEngineTemplateKey())
                        ? trimToNull(request.getEngineTemplateKey())
                        : templateCode)
                .promptHint(trimToNull(request.getPromptHint()))
                .blueprintConfigJson(trimToNull(request.getBlueprintConfigJson()))
                .renderConfigJson(renderConfigJson)
                .templateFilePath(templateFilePath)
                .enabled(defaultEnabled(request.getEnabled()))
                .isDefault(defaultFlag(request.getIsDefault()))
                .sort(defaultSort(request.getSort()))
                .version(request.getVersion() == null || request.getVersion() < 1 ? 1 : request.getVersion())
                .build();
        templateMapper.insert(template);

        if (Objects.equals(template.getIsDefault(), 1)) {
            resetOtherDefaultTemplates(template.getId(), template.getSceneId(), template.getStyleId());
        }
        return getAdminTemplate(template.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PptTemplateAdminResponse updateTemplate(Long id, PptTemplateUpsertRequest request) {
        PptTemplate existing = getTemplateOrThrow(id);
        PptTemplateScene scene = getSceneOrThrow(request.getSceneId());
        PptTemplateStyle style = getStyleOrThrow(request.getStyleId());
        validateStyleBelongsToScene(style, scene.getId());

        String templateCode = buildTemplateCode(request.getTemplateCode(), request.getEngineTemplateKey(), request.getName());
        ensureTemplateCodeUnique(templateCode, id);

        // 编辑模式如果换了 pptx：从 _pending 移到正式目录，记录旧路径以便提交后清理。
        String previousFilePath = existing.getTemplateFilePath();
        String templateFilePath = trimToNull(request.getTemplateFilePath());
        String renderConfigJson = trimToNull(request.getRenderConfigJson());
        boolean replacedPptx = false;
        if (hasText(request.getPendingFileKey())) {
            if (!hasText(renderConfigJson)) {
                throw new BusinessException(400, "缺少模板解析结果，请重新上传 pptx");
            }
            templateFilePath = movePendingFileToFinal(request.getPendingFileKey(), templateCode);
            replacedPptx = true;
        }

        existing.setTemplateCode(templateCode);
        existing.setName(requiredText(request.getName(), "模板名称不能为空"));
        existing.setSceneId(scene.getId());
        existing.setStyleId(style.getId());
        existing.setCoverUrl(trimToNull(request.getCoverUrl()));
        existing.setPreviewImagesJson(toJsonArray(request.getPreviewImages()));
        existing.setDescription(trimToNull(request.getDescription()));
        existing.setEngineTemplateKey(hasText(request.getEngineTemplateKey())
                ? trimToNull(request.getEngineTemplateKey())
                : templateCode);
        existing.setPromptHint(trimToNull(request.getPromptHint()));
        existing.setBlueprintConfigJson(trimToNull(request.getBlueprintConfigJson()));
        existing.setRenderConfigJson(renderConfigJson);
        existing.setTemplateFilePath(templateFilePath);
        existing.setEnabled(defaultEnabled(request.getEnabled()));
        existing.setIsDefault(defaultFlag(request.getIsDefault()));
        existing.setSort(defaultSort(request.getSort()));
        existing.setVersion(request.getVersion() == null || request.getVersion() < 1 ? 1 : request.getVersion());
        templateMapper.updateById(existing);

        if (Objects.equals(existing.getIsDefault(), 1)) {
            resetOtherDefaultTemplates(existing.getId(), existing.getSceneId(), existing.getStyleId());
        }

        // 数据库已经指向新 pptx，可以安全清理旧文件
        if (replacedPptx && hasText(previousFilePath) && !Objects.equals(previousFilePath, templateFilePath)) {
            deleteOldTemplateFile(previousFilePath);
        }
        return getAdminTemplate(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PptTemplateAdminResponse toggleTemplate(Long id) {
        PptTemplate template = getTemplateOrThrow(id);
        template.setEnabled(Objects.equals(template.getEnabled(), 1) ? 0 : 1);
        templateMapper.updateById(template);
        return getAdminTemplate(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteTemplate(Long id) {
        getTemplateOrThrow(id);
        templateMapper.deleteById(id);
    }

    @Override
    public String uploadTemplateAsset(MultipartFile file, Long userId) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(400, "上传文件不能为空");
        }

        try {
            String extension = resolveExtension(file.getOriginalFilename());
            String fileName = "ppt-template-" + UUID.randomUUID().toString().replace("-", "")
                    + (extension.isBlank() ? "" : "." + extension);
            // 把浏览器报告的 MIME（如 image/png）传进去，避免 OSS 用 octet-stream 触发下载
            String filePath = fileStorageService.upload(
                    fileName,
                    file.getBytes(),
                    userId == null ? 0L : userId,
                    file.getContentType()
            );
            return fileStorageService.getUrl(filePath);
        } catch (Exception e) {
            log.error("Upload ppt template asset failed", e);
            throw new BusinessException(500, "模板素材上传失败: " + e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PptTemplateAdminResponse uploadTemplateFile(Long templateId, MultipartFile file) {
        validatePptxUpload(file);
        PptTemplate template = getTemplateOrThrow(templateId);

        Path templatesDir;
        Path targetPath = null;
        try {
            templatesDir = templatesDir();
            Files.createDirectories(templatesDir);

            String savedName = template.getTemplateCode() + "-" + UUID.randomUUID().toString().substring(0, 8) + ".pptx";
            targetPath = templatesDir.resolve(savedName);
            file.transferTo(targetPath.toFile());

            // 解析失败时 parseTemplateMarkers 会抛错，下面 catch 里会删掉刚写入的物理文件。
            TemplateStructure structure = parseTemplateMarkers(targetPath);

            String previousPath = template.getTemplateFilePath();
            template.setTemplateFilePath(savedName);
            template.setRenderConfigJson(objectMapper.writeValueAsString(structure));
            templateMapper.updateById(template);

            // 数据库已经指向新文件，可以安全删除旧文件。失败只记 warn，不阻断业务。
            deleteOldTemplateFile(previousPath);

            log.info("PPT template file uploaded: templateId={}, file={}, markers={}",
                    templateId, savedName, countMarkers(structure));
            return getAdminTemplate(templateId);
        } catch (BusinessException e) {
            safeDeleteFileQuiet(targetPath);
            throw e;
        } catch (Exception e) {
            safeDeleteFileQuiet(targetPath);
            log.error("Upload ppt template file failed: templateId={}", templateId, e);
            throw new BusinessException(500, "模板文件上传失败: " + e.getMessage());
        }
    }

    @Override
    public PptTemplatePreParseResponse preParseTemplate(MultipartFile file) {
        validatePptxUpload(file);

        Path targetPath = null;
        try {
            Path dir = pendingDir();
            Files.createDirectories(dir);

            String pendingKey = UUID.randomUUID().toString().replace("-", "");
            targetPath = dir.resolve(pendingKey + ".pptx");
            file.transferTo(targetPath.toFile());

            TemplateStructure structure = parseTemplateMarkers(targetPath);

            // ── 自动建议字段 ──
            // 名称：POI 抽首页标题，兜底文件名（不需要 LLM，几乎一定能给出）
            String suggestedName = deriveSuggestedName(targetPath, file.getOriginalFilename());

            // 描述 + 场景/风格：调 LLM 看 pptx 前几页文本。LLM 不可用时三项都返回 null，前端 graceful 处理。
            SuggestedMeta meta = suggestMetadataViaLLM(targetPath);

            return PptTemplatePreParseResponse.builder()
                    .pendingFileKey(pendingKey)
                    .originalFileName(file.getOriginalFilename())
                    .totalSlides(structure.getTotalSlides())
                    .markerCount(countMarkers(structure))
                    .repeatableSlideIndex(structure.getRepeatableSlideIndex())
                    .renderConfigJson(objectMapper.writeValueAsString(structure))
                    .slides(buildSlideSummaries(structure))
                    .suggestedName(suggestedName)
                    .suggestedDescription(meta.description())
                    .suggestedSceneId(meta.sceneId())
                    .suggestedStyleId(meta.styleId())
                    .build();
        } catch (BusinessException e) {
            safeDeleteFileQuiet(targetPath);
            throw e;
        } catch (Exception e) {
            safeDeleteFileQuiet(targetPath);
            log.error("Pre-parse ppt template failed", e);
            throw new BusinessException(500, "模板解析失败: " + e.getMessage());
        }
    }

    /**
     * 取 pptx 首页的有意义标题文本。优先策略：第一个非空、不包含 {{ 占位符 }} 的 TextShape；
     * 再 fallback 到去掉 .pptx 后缀的原始文件名。
     */
    private String deriveSuggestedName(Path pptxPath, String originalFileName) {
        try (XMLSlideShow pptx = new XMLSlideShow(new FileInputStream(pptxPath.toFile()))) {
            if (!pptx.getSlides().isEmpty()) {
                XSLFSlide first = pptx.getSlides().get(0);
                for (XSLFShape shape : first.getShapes()) {
                    if (!(shape instanceof XSLFTextShape ts)) {
                        continue;
                    }
                    String text = getFullText(ts).trim();
                    if (text.isEmpty()) {
                        continue;
                    }
                    // 跳过纯占位符（{{标题}}）这种不能当模板名的内容
                    if (text.contains("{{") && text.contains("}}")) {
                        continue;
                    }
                    String firstLine = text.split("\\r?\\n", 2)[0].trim();
                    if (firstLine.isEmpty()) {
                        continue;
                    }
                    if (firstLine.length() > 60) {
                        firstLine = firstLine.substring(0, 60);
                    }
                    return firstLine;
                }
            }
        } catch (Exception e) {
            log.debug("Extract first slide title failed: {}", e.getMessage());
        }
        // 兜底：用文件名（去掉扩展名）
        if (originalFileName == null || originalFileName.isBlank()) {
            return null;
        }
        String name = originalFileName;
        int dot = name.lastIndexOf('.');
        if (dot > 0) {
            name = name.substring(0, dot);
        }
        return name.trim();
    }

    /**
     * 读 pptx 前 N 页的纯文本作为输入摘要，限制总字符数避免 token 浪费。
     */
    private String extractTextDigest(Path pptxPath, int maxSlides, int maxChars) {
        StringBuilder sb = new StringBuilder();
        try (XMLSlideShow pptx = new XMLSlideShow(new FileInputStream(pptxPath.toFile()))) {
            List<XSLFSlide> slides = pptx.getSlides();
            int n = Math.min(slides.size(), maxSlides);
            outer:
            for (int i = 0; i < n; i++) {
                for (XSLFShape shape : slides.get(i).getShapes()) {
                    if (shape instanceof XSLFTextShape ts) {
                        String text = getFullText(ts).trim();
                        if (!text.isEmpty()) {
                            if (sb.length() > 0) {
                                sb.append('\n');
                            }
                            sb.append(text);
                            if (sb.length() >= maxChars) {
                                break outer;
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Extract text digest failed: {}", e.getMessage());
        }
        if (sb.length() > maxChars) {
            sb.setLength(maxChars);
        }
        return sb.toString();
    }

    /**
     * 让 LLM 看 pptx 文本摘要 + 现有场景/风格目录，输出描述 + 推荐的 sceneCode / styleCode。
     * 任一环节失败（LLM 不可用、网络出错、JSON 解析失败、code 没匹配到）都 graceful 返回 EMPTY。
     */
    private SuggestedMeta suggestMetadataViaLLM(Path pptxPath) {
        ILLMService llm = llmServiceProvider == null ? null : llmServiceProvider.getIfAvailable();
        if (llm == null) {
            return SuggestedMeta.EMPTY;
        }

        String digest = extractTextDigest(pptxPath, 5, 1500);
        if (digest.isBlank()) {
            return SuggestedMeta.EMPTY;
        }

        // 拉取所有 enabled 场景 / 风格作为可选项
        List<PptTemplateScene> scenes = sceneMapper.selectList(
                Wrappers.<PptTemplateScene>lambdaQuery().eq(PptTemplateScene::getEnabled, 1));
        List<PptTemplateStyle> styles = styleMapper.selectList(
                Wrappers.<PptTemplateStyle>lambdaQuery().eq(PptTemplateStyle::getEnabled, 1));
        if (scenes.isEmpty() || styles.isEmpty()) {
            return SuggestedMeta.EMPTY;
        }

        String sceneCatalog = scenes.stream()
                .map(s -> s.getSceneCode() + "=" + s.getSceneName())
                .collect(Collectors.joining("、"));
        String styleCatalog = styles.stream()
                .map(s -> s.getStyleCode() + "=" + s.getStyleName())
                .collect(Collectors.joining("、"));

        String system = "你是 PPT 教学模板分类助手。严格按 JSON 返回，不要用 markdown 代码块包裹，不要有多余文字。";
        String user = "下面是一份 pptx 模板的前几页文本摘要，请输出单个 JSON 对象：\n"
                + "{\"description\":\"一句话描述该模板的主题与风格，≤30 个中文字\",\"sceneCode\":\"...\",\"styleCode\":\"...\"}\n\n"
                + "可选 sceneCode：" + sceneCatalog + "\n"
                + "可选 styleCode：" + styleCatalog + "\n"
                + "如果实在判断不出来，sceneCode/styleCode 留空字符串。\n\n"
                + "模板文本摘要：\n" + digest;

        try {
            String response = llm.chat(List.of(
                    new ILLMService.ChatMessage("system", system),
                    new ILLMService.ChatMessage("user", user)
            ));
            if (response == null || response.isBlank()) {
                return SuggestedMeta.EMPTY;
            }
            String json = stripMarkdownFence(response.trim());
            Map<String, Object> parsed = objectMapper.readValue(json, new TypeReference<>() {
            });
            String description = asNullableString(parsed.get("description"));
            String sceneCode = asNullableString(parsed.get("sceneCode"));
            String styleCode = asNullableString(parsed.get("styleCode"));

            Long sceneId = scenes.stream()
                    .filter(s -> sceneCode != null && sceneCode.equalsIgnoreCase(s.getSceneCode()))
                    .map(PptTemplateScene::getId)
                    .findFirst()
                    .orElse(null);
            // 风格必须属于建议的场景，避免出现"工科 + 樱粉"这种 style 不属 scene 的情况
            Long styleId = (sceneId == null)
                    ? null
                    : styles.stream()
                    .filter(s -> styleCode != null && styleCode.equalsIgnoreCase(s.getStyleCode()))
                    .filter(s -> Objects.equals(s.getSceneId(), sceneId))
                    .map(PptTemplateStyle::getId)
                    .findFirst()
                    .orElse(null);

            return new SuggestedMeta(description, sceneId, styleId);
        } catch (Exception e) {
            log.warn("LLM template metadata suggestion failed: {}", e.getMessage());
            return SuggestedMeta.EMPTY;
        }
    }

    private String asNullableString(Object value) {
        if (value == null) {
            return null;
        }
        return trimToNull(String.valueOf(value));
    }

    private String stripMarkdownFence(String text) {
        if (text.startsWith("```")) {
            int firstLn = text.indexOf('\n');
            int lastFence = text.lastIndexOf("```");
            if (firstLn > 0 && lastFence > firstLn) {
                return text.substring(firstLn + 1, lastFence).trim();
            }
        }
        return text;
    }

    private record SuggestedMeta(String description, Long sceneId, Long styleId) {
        static final SuggestedMeta EMPTY = new SuggestedMeta(null, null, null);
    }

    private void validatePptxUpload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(400, "上传文件不能为空");
        }
        String originalName = file.getOriginalFilename();
        if (originalName == null || !originalName.toLowerCase(Locale.ROOT).endsWith(".pptx")) {
            throw new BusinessException(400, "仅支持上传 .pptx 文件");
        }
    }

    private int countMarkers(TemplateStructure structure) {
        if (structure == null || structure.getSlides() == null) {
            return 0;
        }
        return structure.getSlides().stream()
                .mapToInt(s -> s.getMarkers() == null ? 0 : s.getMarkers().size())
                .sum();
    }

    private List<PptTemplatePreParseResponse.SlideSummary> buildSlideSummaries(TemplateStructure structure) {
        if (structure == null || structure.getSlides() == null) {
            return List.of();
        }
        return structure.getSlides().stream()
                .map(s -> PptTemplatePreParseResponse.SlideSummary.builder()
                        .slideIndex(s.getSlideIndex())
                        .slideRole(s.getSlideRole())
                        .markerNames(s.getMarkers() == null
                                ? List.of()
                                : s.getMarkers().stream()
                                .map(TemplateMarkerInfo::getName)
                                .toList())
                        .build())
                .toList();
    }

    /**
     * 把 _pending/&lt;key&gt;.pptx move 到 templates/&lt;templateCode&gt;-&lt;uuid8&gt;.pptx 下并返回相对路径。
     * 临时文件不存在（比如经过 _pending 清理后用户才提交）抛 BusinessException 让前端重新上传。
     */
    private String movePendingFileToFinal(String pendingFileKey, String templateCode) {
        Path pendingPath = pendingDir().resolve(pendingFileKey + ".pptx");
        if (!Files.exists(pendingPath)) {
            throw new BusinessException(400, "模板文件已过期或不存在，请重新上传 pptx");
        }
        try {
            Path templatesDir = templatesDir();
            Files.createDirectories(templatesDir);
            String savedName = templateCode + "-" + UUID.randomUUID().toString().substring(0, 8) + ".pptx";
            Path targetPath = templatesDir.resolve(savedName);
            try {
                Files.move(pendingPath, targetPath, StandardCopyOption.ATOMIC_MOVE);
            } catch (Exception atomicFail) {
                // ATOMIC_MOVE 在某些 FS / 跨卷场景不支持，退回普通 move（复制+删源）。
                Files.move(pendingPath, targetPath, StandardCopyOption.REPLACE_EXISTING);
            }
            return savedName;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Move pending pptx failed: pendingKey={}, code={}", pendingFileKey, templateCode, e);
            throw new BusinessException(500, "模板文件归档失败: " + e.getMessage());
        }
    }

    private void deleteOldTemplateFile(String relativePath) {
        if (!hasText(relativePath)) {
            return;
        }
        try {
            Path path = templatesDir().resolve(relativePath).normalize();
            // 防止 ../ 注入逃逸到 templates 目录之外
            if (!path.startsWith(templatesDir())) {
                log.warn("Refuse to delete file outside templates dir: {}", relativePath);
                return;
            }
            if (Files.deleteIfExists(path)) {
                log.info("Removed old template file: {}", relativePath);
            }
        } catch (Exception e) {
            log.warn("Delete old template file failed: {} - {}", relativePath, e.getMessage());
        }
    }

    @Override
    public List<PptTemplateSceneResponse> listClientScenes() {
        return listScenes(true);
    }

    @Override
    public List<PptTemplateStyleResponse> listClientStyles(Long sceneId) {
        return listStyles(sceneId, true);
    }

    @Override
    public List<PptTemplateClientResponse> listClientTemplates(Long sceneId, Long styleId, String keyword) {
        LambdaQueryWrapper<PptTemplate> wrapper = Wrappers.lambdaQuery();
        // 决策已纯走 pptx 模板路线：教师端只显示有 pptx 文件的模板。没有 pptx 的（包括历史遗留的配色种子）
        // 都不再可见，避免老师选了却得到完全无关的程序化渲染结果。
        wrapper.eq(PptTemplate::getEnabled, 1)
                .isNotNull(PptTemplate::getTemplateFilePath)
                .ne(PptTemplate::getTemplateFilePath, "")
                .eq(sceneId != null, PptTemplate::getSceneId, sceneId)
                .eq(styleId != null, PptTemplate::getStyleId, styleId)
                .and(hasText(keyword), q -> q.like(PptTemplate::getName, keyword)
                        .or()
                        .like(PptTemplate::getDescription, keyword))
                .orderByDesc(PptTemplate::getIsDefault)
                .orderByAsc(PptTemplate::getSort)
                .orderByDesc(PptTemplate::getId);

        List<PptTemplate> templates = templateMapper.selectList(wrapper);
        Map<Long, PptTemplateScene> sceneMap = sceneMap(templates.stream()
                .map(PptTemplate::getSceneId)
                .collect(Collectors.toSet()));
        Map<Long, PptTemplateStyle> styleMap = styleMap(templates.stream()
                .map(PptTemplate::getStyleId)
                .collect(Collectors.toSet()));

        return templates.stream()
                .map(template -> toClientResponse(template, sceneMap, styleMap))
                .toList();
    }

    @Override
    public PptTemplateClientResponse getClientTemplate(Long id) {
        PptTemplate template = getTemplateOrThrow(id);
        if (!Objects.equals(template.getEnabled(), 1) || !isRenderableTemplate(template)) {
            throw new BusinessException(404, "模板不存在");
        }
        Map<Long, PptTemplateScene> sceneMap = sceneMap(List.of(template.getSceneId()));
        Map<Long, PptTemplateStyle> styleMap = styleMap(List.of(template.getStyleId()));
        return toClientResponse(template, sceneMap, styleMap);
    }

    /**
     * 判断模板是否"可渲染"：决策已纯走 pptx 模板路线，必须有 pptx 文件才算可渲染。
     */
    private boolean isRenderableTemplate(PptTemplate template) {
        return template != null && hasText(template.getTemplateFilePath());
    }

    @Override
    public PptTemplate resolveTemplateReference(String templateRef) {
        if (!hasText(templateRef)) {
            return null;
        }

        String normalizedRef = templateRef.trim();
        PptTemplate template = null;
        if (normalizedRef.chars().allMatch(Character::isDigit)) {
            template = templateMapper.selectOne(
                    Wrappers.<PptTemplate>lambdaQuery()
                            .eq(PptTemplate::getId, Long.parseLong(normalizedRef))
                            .eq(PptTemplate::getEnabled, 1)
                            .last("LIMIT 1")
            );
        }
        if (template == null) {
            template = templateMapper.selectOne(
                    Wrappers.<PptTemplate>lambdaQuery()
                            .eq(PptTemplate::getTemplateCode, normalizedRef)
                            .eq(PptTemplate::getEnabled, 1)
                            .last("LIMIT 1")
            );
        }
        if (template == null) {
            template = templateMapper.selectOne(
                    Wrappers.<PptTemplate>lambdaQuery()
                            .eq(PptTemplate::getEngineTemplateKey, normalizedRef)
                            .eq(PptTemplate::getEnabled, 1)
                            .last("LIMIT 1")
            );
        }
        return template;
    }

    private List<PptTemplateSceneResponse> listScenes(Boolean enabled) {
        Integer enabledValue = toEnabledValue(enabled);
        LambdaQueryWrapper<PptTemplateScene> wrapper = Wrappers.lambdaQuery();
        wrapper.eq(enabledValue != null, PptTemplateScene::getEnabled, enabledValue)
                .orderByAsc(PptTemplateScene::getSort)
                .orderByDesc(PptTemplateScene::getId);
        return sceneMapper.selectList(wrapper).stream()
                .map(this::toSceneResponse)
                .toList();
    }

    private List<PptTemplateStyleResponse> listStyles(Long sceneId, Boolean enabled) {
        Integer enabledValue = toEnabledValue(enabled);
        LambdaQueryWrapper<PptTemplateStyle> wrapper = Wrappers.lambdaQuery();
        wrapper.eq(sceneId != null, PptTemplateStyle::getSceneId, sceneId)
                .eq(enabledValue != null, PptTemplateStyle::getEnabled, enabledValue)
                .orderByAsc(PptTemplateStyle::getSort)
                .orderByDesc(PptTemplateStyle::getId);
        List<PptTemplateStyle> styles = styleMapper.selectList(wrapper);
        Map<Long, PptTemplateScene> sceneMap = sceneMap(styles.stream()
                .map(PptTemplateStyle::getSceneId)
                .collect(Collectors.toSet()));
        return styles.stream()
                .map(style -> toStyleResponse(style, sceneMap.get(style.getSceneId()) == null
                        ? null
                        : sceneMap.get(style.getSceneId()).getSceneName()))
                .toList();
    }

    private PptTemplateScene getSceneOrThrow(Long id) {
        if (id == null) {
            throw new BusinessException(400, "场景ID不能为空");
        }
        PptTemplateScene scene = sceneMapper.selectById(id);
        if (scene == null) {
            throw new BusinessException(404, "场景不存在");
        }
        return scene;
    }

    private PptTemplateStyle getStyleOrThrow(Long id) {
        if (id == null) {
            throw new BusinessException(400, "风格ID不能为空");
        }
        PptTemplateStyle style = styleMapper.selectById(id);
        if (style == null) {
            throw new BusinessException(404, "风格不存在");
        }
        return style;
    }

    private PptTemplate getTemplateOrThrow(Long id) {
        if (id == null) {
            throw new BusinessException(400, "模板ID不能为空");
        }
        PptTemplate template = templateMapper.selectById(id);
        if (template == null) {
            throw new BusinessException(404, "模板不存在");
        }
        return template;
    }

    private void ensureSceneCodeUnique(String sceneCode, Long excludeId) {
        PptTemplateScene existing = sceneMapper.selectOne(
                Wrappers.<PptTemplateScene>lambdaQuery()
                        .eq(PptTemplateScene::getSceneCode, sceneCode)
                        .ne(excludeId != null, PptTemplateScene::getId, excludeId)
                        .last("LIMIT 1")
        );
        if (existing != null) {
            throw new BusinessException(400, "场景编码已存在");
        }
    }

    private void ensureSceneNameUnique(String sceneName, Long excludeId) {
        PptTemplateScene existing = sceneMapper.selectOne(
                Wrappers.<PptTemplateScene>lambdaQuery()
                        .eq(PptTemplateScene::getSceneName, sceneName)
                        .ne(excludeId != null, PptTemplateScene::getId, excludeId)
                        .last("LIMIT 1")
        );
        if (existing != null) {
            throw new BusinessException(400, "场景名称已存在");
        }
    }

    private void ensureStyleCodeUnique(Long sceneId, String styleCode, Long excludeId) {
        PptTemplateStyle existing = styleMapper.selectOne(
                Wrappers.<PptTemplateStyle>lambdaQuery()
                        .eq(PptTemplateStyle::getSceneId, sceneId)
                        .eq(PptTemplateStyle::getStyleCode, styleCode)
                        .ne(excludeId != null, PptTemplateStyle::getId, excludeId)
                        .last("LIMIT 1")
        );
        if (existing != null) {
            throw new BusinessException(400, "该场景下风格编码已存在");
        }
    }

    private void ensureStyleNameUnique(Long sceneId, String styleName, Long excludeId) {
        PptTemplateStyle existing = styleMapper.selectOne(
                Wrappers.<PptTemplateStyle>lambdaQuery()
                        .eq(PptTemplateStyle::getSceneId, sceneId)
                        .eq(PptTemplateStyle::getStyleName, styleName)
                        .ne(excludeId != null, PptTemplateStyle::getId, excludeId)
                        .last("LIMIT 1")
        );
        if (existing != null) {
            throw new BusinessException(400, "该场景下风格名称已存在");
        }
    }

    private void ensureTemplateCodeUnique(String templateCode, Long excludeId) {
        PptTemplate existing = templateMapper.selectOne(
                Wrappers.<PptTemplate>lambdaQuery()
                        .eq(PptTemplate::getTemplateCode, templateCode)
                        .ne(excludeId != null, PptTemplate::getId, excludeId)
                        .last("LIMIT 1")
        );
        if (existing != null) {
            throw new BusinessException(400, "模板编码已存在");
        }
    }

    private void validateStyleBelongsToScene(PptTemplateStyle style, Long sceneId) {
        if (!Objects.equals(style.getSceneId(), sceneId)) {
            throw new BusinessException(400, "所选风格不属于当前场景");
        }
    }

    private void resetOtherDefaultTemplates(Long currentId, Long sceneId, Long styleId) {
        List<PptTemplate> otherDefaults = templateMapper.selectList(
                Wrappers.<PptTemplate>lambdaQuery()
                        .eq(PptTemplate::getSceneId, sceneId)
                        .eq(PptTemplate::getStyleId, styleId)
                        .eq(PptTemplate::getIsDefault, 1)
                        .ne(PptTemplate::getId, currentId)
        );
        for (PptTemplate item : otherDefaults) {
            item.setIsDefault(0);
            templateMapper.updateById(item);
        }
    }

    private Map<Long, PptTemplateScene> sceneMap(Collection<Long> sceneIds) {
        if (sceneIds == null || sceneIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return sceneMapper.selectBatchIds(sceneIds).stream()
                .collect(Collectors.toMap(PptTemplateScene::getId, scene -> scene, (left, right) -> left, LinkedHashMap::new));
    }

    private Map<Long, PptTemplateStyle> styleMap(Collection<Long> styleIds) {
        if (styleIds == null || styleIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return styleMapper.selectBatchIds(styleIds).stream()
                .collect(Collectors.toMap(PptTemplateStyle::getId, style -> style, (left, right) -> left, LinkedHashMap::new));
    }

    private PptTemplateSceneResponse toSceneResponse(PptTemplateScene scene) {
        return PptTemplateSceneResponse.builder()
                .id(scene.getId())
                .sceneCode(scene.getSceneCode())
                .sceneName(scene.getSceneName())
                .sort(scene.getSort())
                .enabled(scene.getEnabled())
                .createTime(scene.getCreateTime())
                .updateTime(scene.getUpdateTime())
                .build();
    }

    private PptTemplateStyleResponse toStyleResponse(PptTemplateStyle style, String sceneName) {
        return PptTemplateStyleResponse.builder()
                .id(style.getId())
                .sceneId(style.getSceneId())
                .sceneName(sceneName)
                .styleCode(style.getStyleCode())
                .styleName(style.getStyleName())
                .sort(style.getSort())
                .enabled(style.getEnabled())
                .createTime(style.getCreateTime())
                .updateTime(style.getUpdateTime())
                .build();
    }

    private PptTemplateAdminResponse toAdminResponse(PptTemplate template,
                                                     Map<Long, PptTemplateScene> sceneMap,
                                                     Map<Long, PptTemplateStyle> styleMap) {
        PptTemplateScene scene = sceneMap.get(template.getSceneId());
        PptTemplateStyle style = styleMap.get(template.getStyleId());
        return PptTemplateAdminResponse.builder()
                .id(template.getId())
                .templateCode(template.getTemplateCode())
                .name(template.getName())
                .sceneId(template.getSceneId())
                .sceneName(scene == null ? null : scene.getSceneName())
                .styleId(template.getStyleId())
                .styleName(style == null ? null : style.getStyleName())
                .coverUrl(template.getCoverUrl())
                .previewImages(parseJsonArray(template.getPreviewImagesJson()))
                .description(template.getDescription())
                .engineTemplateKey(template.getEngineTemplateKey())
                .promptHint(template.getPromptHint())
                .blueprintConfigJson(template.getBlueprintConfigJson())
                .renderConfigJson(template.getRenderConfigJson())
                .enabled(template.getEnabled())
                .isDefault(template.getIsDefault())
                .sort(template.getSort())
                .version(template.getVersion())
                .templateFilePath(template.getTemplateFilePath())
                .createTime(template.getCreateTime())
                .updateTime(template.getUpdateTime())
                .build();
    }

    private PptTemplateClientResponse toClientResponse(PptTemplate template,
                                                       Map<Long, PptTemplateScene> sceneMap,
                                                       Map<Long, PptTemplateStyle> styleMap) {
        PptTemplateScene scene = sceneMap.get(template.getSceneId());
        PptTemplateStyle style = styleMap.get(template.getStyleId());
        return PptTemplateClientResponse.builder()
                .id(template.getId())
                .templateCode(template.getTemplateCode())
                .name(template.getName())
                .sceneId(template.getSceneId())
                .sceneName(scene == null ? null : scene.getSceneName())
                .styleId(template.getStyleId())
                .styleName(style == null ? null : style.getStyleName())
                .coverUrl(template.getCoverUrl())
                .previewImages(parseJsonArray(template.getPreviewImagesJson()))
                .description(template.getDescription())
                .isDefault(template.getIsDefault())
                .build();
    }

    private List<String> parseJsonArray(String json) {
        if (!hasText(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, STRING_LIST_TYPE);
        } catch (Exception e) {
            log.warn("Parse previewImagesJson failed: {}", json, e);
            return List.of();
        }
    }

    private String toJsonArray(List<String> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        List<String> normalized = values.stream()
                .map(this::trimToNull)
                .filter(Objects::nonNull)
                .toList();
        if (normalized.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(normalized);
        } catch (Exception e) {
            throw new BusinessException(400, "预览图列表序列化失败");
        }
    }

    private String buildTemplateCode(String templateCode, String engineTemplateKey, String templateName) {
        String candidate = hasText(templateCode) ? templateCode : engineTemplateKey;
        if (!hasText(candidate)) {
            candidate = templateName;
        }
        candidate = trimToNull(candidate);
        if (candidate == null) {
            throw new BusinessException(400, "模板编码不能为空");
        }

        String normalized = candidate.trim()
                .replaceAll("\\s+", "_")
                .replaceAll("[^0-9A-Za-z_-]", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_+|_+$", "");
        if (normalized.isBlank()) {
            throw new BusinessException(400, "模板编码不合法");
        }
        return normalized.toLowerCase(Locale.ROOT);
    }

    private String requiredText(String value, String message) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            throw new BusinessException(400, message);
        }
        return normalized;
    }

    private String requiredCode(String value, String message) {
        String normalized = requiredText(value, message)
                .replaceAll("\\s+", "_")
                .replaceAll("[^0-9A-Za-z_-]", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_+|_+$", "");
        if (normalized.isBlank()) {
            throw new BusinessException(400, message);
        }
        return normalized.toLowerCase(Locale.ROOT);
    }

    private Integer defaultSort(Integer sort) {
        return sort == null ? 0 : sort;
    }

    private Integer defaultEnabled(Integer enabled) {
        return enabled == null ? 1 : (enabled == 0 ? 0 : 1);
    }

    private Integer toEnabledValue(Boolean enabled) {
        if (enabled == null) {
            return null;
        }
        return enabled ? 1 : 0;
    }

    private Integer defaultFlag(Integer flag) {
        return flag == null ? 0 : (flag == 0 ? 0 : 1);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private boolean hasText(String value) {
        return trimToNull(value) != null;
    }

    private String resolveExtension(String fileName) {
        if (!hasText(fileName) || !fileName.contains(".")) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
    }

    TemplateStructure parseTemplateMarkers(Path pptxPath) {
        List<TemplateSlideStructure> slideStructures = new ArrayList<>();
        int totalSlides;
        int bestContentSlideIndex = -1;
        int bestMarkerCount = 0;

        try (XMLSlideShow pptx = new XMLSlideShow(new FileInputStream(pptxPath.toFile()))) {
            List<XSLFSlide> slides = pptx.getSlides();
            totalSlides = slides.size();

            for (int i = 0; i < slides.size(); i++) {
                XSLFSlide slide = slides.get(i);
                List<TemplateMarkerInfo> markers = new ArrayList<>();

                // 主页面内容：扫描所有 shape（包含表格 / 组合形状递归 / 普通文本框）
                collectMarkersFromShapes(slide.getShapes(), markers);

                // 备注页（speaker notes）：教师上课提词器，模板里也允许放标记给 AI 填写
                XSLFNotes notes = slide.getNotes();
                if (notes != null) {
                    collectMarkersFromShapes(notes.getShapes(), markers);
                }

                String role = detectSlideRole(i, totalSlides, markers);
                slideStructures.add(TemplateSlideStructure.builder()
                        .slideIndex(i)
                        .slideRole(role)
                        .markers(markers)
                        .build());

                boolean isContent = !"cover".equals(role) && !"summary".equals(role);
                if (isContent && markers.size() > bestMarkerCount) {
                    bestMarkerCount = markers.size();
                    bestContentSlideIndex = i;
                }
            }
        } catch (Exception e) {
            // 解析失败时不再吞掉异常返回空骨架（那样会得到一个指向损坏 pptx 的"假"模板）。
            // 抛出去让上层在事务回滚时一并清理物理文件。
            log.warn("Failed to parse template markers from: {}", pptxPath, e);
            throw new BusinessException(400, "PPTX 文件解析失败，请确认文件未损坏：" + e.getMessage());
        }

        return TemplateStructure.builder()
                .totalSlides(totalSlides)
                .repeatableSlideIndex(bestContentSlideIndex)
                .slides(slideStructures)
                .build();
    }

    /**
     * 递归扫描 shapes 中的所有 {{标记}}：
     *   - XSLFTextShape：直接抽 raw text（XSLFTableCell 是 TextShape 子类，所以表格单元格也会走到这里）
     *   - XSLFTable：遍历 rows → cells，cell 自己是 XSLFTextShape
     *   - XSLFGroupShape：递归到内部 shapes
     */
    private void collectMarkersFromShapes(java.util.Collection<? extends XSLFShape> shapes,
                                          List<TemplateMarkerInfo> markers) {
        if (shapes == null) {
            return;
        }
        for (XSLFShape shape : shapes) {
            if (shape instanceof XSLFTable table) {
                for (XSLFTableRow row : table.getRows()) {
                    for (XSLFTableCell cell : row.getCells()) {
                        collectMarkersFromText(cell, getFullText(cell), markers);
                    }
                }
            } else if (shape instanceof XSLFGroupShape group) {
                collectMarkersFromShapes(group.getShapes(), markers);
            } else if (shape instanceof XSLFTextShape textShape) {
                collectMarkersFromText(textShape, getFullText(textShape), markers);
            }
            // 其他类型（图片、SmartArt 主体、Chart 等）暂不扫描。
        }
    }

    private void collectMarkersFromText(XSLFShape shape, String fullText, List<TemplateMarkerInfo> markers) {
        if (fullText == null || fullText.isEmpty()) {
            return;
        }
        Matcher matcher = MARKER_PATTERN.matcher(fullText);
        while (matcher.find()) {
            // 兼容 {{ 空格 标 题 空格 }} —— trim 后用作 key，避免后端替换时 key 对不上。
            String markerName = matcher.group(1).trim();
            if (!markerName.isEmpty()) {
                markers.add(TemplateMarkerInfo.builder()
                        .name(markerName)
                        .shapeId(String.valueOf(shape.getShapeId()))
                        .build());
            }
        }
    }

    private String getFullText(XSLFTextShape textShape) {
        StringBuilder sb = new StringBuilder();
        for (XSLFTextParagraph paragraph : textShape.getTextParagraphs()) {
            for (XSLFTextRun run : paragraph.getTextRuns()) {
                sb.append(run.getRawText());
            }
        }
        return sb.toString();
    }

    private String detectSlideRole(int slideIndex, int totalSlides, List<TemplateMarkerInfo> markers) {
        if (totalSlides <= 1) {
            return "cover";
        }
        if (slideIndex == 0) {
            return "cover";
        }
        if (slideIndex == totalSlides - 1) {
            return "summary";
        }
        return "content";
    }
}
