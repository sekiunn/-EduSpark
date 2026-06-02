package com.eduspark.eduspark.service.impl;

import com.eduspark.eduspark.dto.chat.TeachingBlueprint;
import com.eduspark.eduspark.dto.knowledge.KnowledgeSearchRequest;
import com.eduspark.eduspark.dto.knowledge.KnowledgeSearchResponse;
import com.eduspark.eduspark.service.IKnowledgeService;
import com.eduspark.eduspark.service.IPptBlueprintEnrichmentService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * PPT blueprint enrichment backed by knowledge retrieval and reference merging.
 */
@Slf4j
@Service
public class PptBlueprintEnrichmentServiceImpl implements IPptBlueprintEnrichmentService {

    private static final int MAX_REFERENCE_TEXT = 12_000;
    private static final int MAX_RESULT_FRAGMENT = 1_200;
    private static final int MAX_ACCEPTED_RESULTS = 4;

    /**
     * PPT 元词 / 模板风格描述黑名单。用户的"补充要求"经常混入这类词
     * （如"手绘教师说课PPT"），如果直接拿去 BM25 检索会污染召回结果。
     * 这里在构造检索 query 时做最小清洗：把这些词从每个 part 里抠掉，
     * 剩下的合法关键词（如"C语言""选择排序"）才进入查询。
     */
    private static final Pattern STYLE_NOISE_PATTERN = Pattern.compile(
            "(?i)(手绘|说课|课件|模板|风格|配色|样式|主题|教学课件|教学模板|PPT|ppt)"
    );

    private final IKnowledgeService knowledgeService;

    /**
     * 知识库采纳门槛：用 vectorScore（余弦相似度，越大越相关）卡，低于此值视为"不够相关"、不采纳。
     * 之前用的是只反映"排第几"的 RRF 分 + 无条件保留第一条，导致库里没相关内容时也硬塞最接近的无关结果。
     */
    @Value("${rag.search.min-knowledge-threshold:0.5}")
    private float minKnowledgeThreshold;

    public PptBlueprintEnrichmentServiceImpl(IKnowledgeService knowledgeService) {
        this.knowledgeService = knowledgeService;
    }

    @Override
    public TeachingBlueprint enrichBlueprint(Long userId, TeachingBlueprint sourceBlueprint) {
        TeachingBlueprint baseBlueprint = cloneBlueprint(sourceBlueprint);
        KnowledgeBundle knowledgeBundle = retrieveKnowledge(userId, baseBlueprint);
        TeachingBlueprint enriched = applyKnowledgeBundle(baseBlueprint, knowledgeBundle);

        if (knowledgeBundle.isEmpty()) {
            log.info(
                    "Skip ppt knowledge enrichment because no accepted knowledge hits: userId={}, subject={}, topic={}",
                    userId,
                    enriched.getCore() != null ? enriched.getCore().getSubject() : "",
                    enriched.getCore() != null ? enriched.getCore().getTopic() : ""
            );
        }
        return enriched;
    }

    private KnowledgeBundle retrieveKnowledge(Long userId, TeachingBlueprint blueprint) {
        String query = buildKnowledgeQuery(blueprint);
        if (!hasText(query) || userId == null) {
            return KnowledgeBundle.empty();
        }

        try {
            KnowledgeSearchResponse response = knowledgeService.search(
                    KnowledgeSearchRequest.builder()
                            .query(query)
                            .topK(6)
                            .userId(userId)
                            .build()
            );
            return KnowledgeBundle.of(query, selectAcceptedResults(response));
        } catch (Exception e) {
            log.warn("Knowledge retrieval failed during ppt enrichment: userId={}, query={}", userId, query, e);
            return KnowledgeBundle.empty();
        }
    }

    private List<KnowledgeHit> selectAcceptedResults(KnowledgeSearchResponse response) {
        List<KnowledgeHit> accepted = new ArrayList<>();
        if (response == null || response.getResults() == null) {
            return accepted;
        }

        for (KnowledgeSearchResponse.KnowledgeSearchResult result : response.getResults()) {
            if (result == null || !hasText(result.getText())) {
                continue;
            }

            // 用真实语义相似度（vectorScore = 1 - 余弦距离，越大越相关）卡门槛，
            // 而不是只反映"排第几名"的 RRF score。达不到阈值一律不采纳——
            // 知识库没有够相关的内容时宁可不给参考，也绝不硬塞最接近的无关结果（会污染生成）。
            float similarity = result.getVectorScore() != null ? result.getVectorScore() : 0F;
            if (similarity < minKnowledgeThreshold) {
                continue;
            }

            accepted.add(new KnowledgeHit(
                    result.getFileId(),
                    valueOrEmpty(result.getFileName()),
                    truncate(valueOrEmpty(result.getText()), MAX_RESULT_FRAGMENT),
                    result.getScore()
            ));
            log.info("知识库采纳: file={}, similarity={}", result.getFileName(), similarity);

            if (accepted.size() >= MAX_ACCEPTED_RESULTS) {
                break;
            }
        }

        if (accepted.isEmpty()) {
            log.info("知识库无足够相关内容（相似度均 < {}），本次跳过知识增强——没有就是没有，不硬塞。",
                    minKnowledgeThreshold);
        }
        return accepted;
    }

    private TeachingBlueprint applyKnowledgeBundle(TeachingBlueprint blueprint, KnowledgeBundle knowledgeBundle) {
        TeachingBlueprint enriched = cloneBlueprint(blueprint);
        if (knowledgeBundle.isEmpty()) {
            return enriched;
        }

        List<Map<String, Object>> knowledgeSources = new ArrayList<>();
        LinkedHashSet<String> attachmentNames = new LinkedHashSet<>(extractStringList(enriched, "attachmentNames"));
        for (KnowledgeHit hit : knowledgeBundle.hits()) {
            attachmentNames.add(hit.fileName());

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("fileId", hit.fileId());
            row.put("fileName", hit.fileName());
            row.put("excerpt", hit.excerpt());
            row.put("score", hit.score());
            knowledgeSources.add(row);
        }

        if (!attachmentNames.isEmpty()) {
            enriched.putExtension("attachmentNames", new ArrayList<>(attachmentNames));
        }
        enriched.putExtension("knowledgeSources", knowledgeSources);
        enriched.putExtension("knowledgeQuery", knowledgeBundle.query());
        enriched.putExtension("referenceText", mergeText(
                asString(enriched.getExtension("referenceText")),
                knowledgeBundle.referenceText()
        ));
        return enriched;
    }

    private String buildKnowledgeQuery(TeachingBlueprint blueprint) {
        List<String> parts = new ArrayList<>();
        if (blueprint.getCore() != null) {
            appendIfPresent(parts, blueprint.getCore().getSubject());
            appendIfPresent(parts, blueprint.getCore().getGrade());
            appendIfPresent(parts, blueprint.getCore().getTopic());
        }
        parts.addAll(extractStringList(blueprint, "knowledgePoints"));
        parts.addAll(extractStringList(blueprint, "keyPoints"));

        return parts.stream()
                .map(this::stripStyleNoise)
                .filter(this::hasText)
                .distinct()
                .reduce((left, right) -> left + " " + right)
                .orElse("");
    }

    private TeachingBlueprint cloneBlueprint(TeachingBlueprint blueprint) {
        if (blueprint == null) {
            return TeachingBlueprint.fromLegacyMap(Map.of());
        }
        return TeachingBlueprint.fromLegacyMap(blueprint.toFlatMap());
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

    private String mergeText(String base, String extra) {
        if (!hasText(base)) {
            return truncate(valueOrEmpty(extra), MAX_REFERENCE_TEXT);
        }
        if (!hasText(extra)) {
            return truncate(base, MAX_REFERENCE_TEXT);
        }
        return truncate(base.trim() + "\n\n" + extra.trim(), MAX_REFERENCE_TEXT);
    }

    private void appendIfPresent(List<String> parts, String value) {
        if (hasText(value)) {
            parts.add(value.trim());
        }
    }

    /**
     * 把模板风格元词从 query 部件里抠掉。例如：
     * "手绘教师说课PPT" → "教师"，"C语言选择排序PPT" → "C语言选择排序"。
     * 清洗后若变空字符串，应在调用侧用 hasText 过滤掉。
     */
    private String stripStyleNoise(String value) {
        if (!hasText(value)) {
            return "";
        }
        String cleaned = STYLE_NOISE_PATTERN.matcher(value).replaceAll(" ");
        return cleaned.replaceAll("\\s+", " ").trim();
    }

    private String truncate(String value, int maxLength) {
        if (!hasText(value) || value.length() <= maxLength) {
            return valueOrEmpty(value);
        }
        return value.substring(0, maxLength);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }

    private String asString(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private record KnowledgeHit(Long fileId, String fileName, String excerpt, Float score) {
    }

    private record KnowledgeBundle(String query, List<KnowledgeHit> hits) {

        static KnowledgeBundle empty() {
            return new KnowledgeBundle("", List.of());
        }

        static KnowledgeBundle of(String query, List<KnowledgeHit> hits) {
            return new KnowledgeBundle(query == null ? "" : query, hits == null ? List.of() : hits);
        }

        boolean isEmpty() {
            return hits == null || hits.isEmpty();
        }

        String referenceText() {
            if (isEmpty()) {
                return "";
            }

            StringBuilder builder = new StringBuilder();
            for (KnowledgeHit hit : hits) {
                if (builder.length() > 0) {
                    builder.append("\n\n");
                }
                builder.append("资料：").append(hit.fileName()).append('\n')
                        .append(hit.excerpt());
            }
            return builder.toString();
        }
    }
}
