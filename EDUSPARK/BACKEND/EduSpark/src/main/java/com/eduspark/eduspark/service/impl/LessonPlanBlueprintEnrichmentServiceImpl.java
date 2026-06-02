package com.eduspark.eduspark.service.impl;

import com.eduspark.eduspark.dto.chat.TeachingBlueprint;
import com.eduspark.eduspark.dto.knowledge.KnowledgeSearchRequest;
import com.eduspark.eduspark.dto.knowledge.KnowledgeSearchResponse;
import com.eduspark.eduspark.service.IKnowledgeService;
import com.eduspark.eduspark.service.ILessonPlanBlueprintEnrichmentService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Lesson-plan blueprint enrichment backed by knowledge retrieval only.
 */
@Slf4j
@Service
public class LessonPlanBlueprintEnrichmentServiceImpl implements ILessonPlanBlueprintEnrichmentService {

    private static final int MAX_REFERENCE_TEXT = 12000;
    private static final int MAX_RESULT_FRAGMENT = 1800;
    private static final int MAX_ACCEPTED_RESULTS = 4;
    private static final float MIN_ACCEPTED_SCORE = 0.03F;
    private static final List<String> WEAK_CONNECTORS = List.of(
            "的", "地", "得", "了", "和", "与", "及", "及其", "并", "并且", "以及"
    );

    private final IKnowledgeService knowledgeService;

    public LessonPlanBlueprintEnrichmentServiceImpl(IKnowledgeService knowledgeService) {
        this.knowledgeService = knowledgeService;
    }

    @Override
    public TeachingBlueprint enrichBlueprint(Long userId, TeachingBlueprint sourceBlueprint) {
        TeachingBlueprint baseBlueprint = cloneBlueprint(sourceBlueprint);
        KnowledgeBundle knowledgeBundle = retrieveKnowledge(userId, baseBlueprint);
        TeachingBlueprint withKnowledge = applyKnowledgeBundle(baseBlueprint, knowledgeBundle);
        if (knowledgeBundle.isEmpty()) {
            log.info(
                    "Skip lesson-plan knowledge enrichment because no accepted knowledge hits: userId={}, subject={}, topic={}",
                    userId,
                    baseBlueprint.getCore() != null ? baseBlueprint.getCore().getSubject() : "",
                    baseBlueprint.getCore() != null ? baseBlueprint.getCore().getTopic() : ""
            );
        }
        return withKnowledge;
    }

    private KnowledgeBundle retrieveKnowledge(Long userId, TeachingBlueprint blueprint) {
        String query = buildKnowledgeQuery(blueprint);
        if (!hasText(query)) {
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
            List<KnowledgeSearchResponse.KnowledgeSearchResult> results =
                    response != null && response.getResults() != null ? response.getResults() : List.of();
            if (results.isEmpty()) {
                return KnowledgeBundle.empty();
            }

            List<KnowledgeSearchResponse.KnowledgeSearchResult> acceptedResults =
                    filterKnowledgeResults(results, blueprint);
            if (acceptedResults.isEmpty()) {
                log.info(
                        "Ignore unrelated knowledge hits during lesson-plan enrichment: userId={}, query={}, rawCount={}",
                        userId,
                        query,
                        results.size()
                );
                return KnowledgeBundle.empty();
            }

            StringBuilder referenceText = new StringBuilder();
            LinkedHashSet<String> attachmentNames =
                    new LinkedHashSet<>(extractStringList(blueprint, "attachmentNames"));
            List<Map<String, Object>> knowledgeSources = new ArrayList<>();

            String existingReferenceText = asString(blueprint.getExtension("referenceText"));
            if (hasText(existingReferenceText)) {
                referenceText.append(existingReferenceText);
            }

            for (KnowledgeSearchResponse.KnowledgeSearchResult item : acceptedResults) {
                String fileName = hasText(item.getFileName()) ? item.getFileName() : "知识库片段";
                String excerpt = truncate(item.getText(), MAX_RESULT_FRAGMENT);

                attachmentNames.add(fileName);
                if (referenceText.length() > 0) {
                    referenceText.append("\n\n");
                }
                referenceText.append("【").append(fileName).append("】\n").append(excerpt);

                Map<String, Object> sourceItem = new LinkedHashMap<>();
                sourceItem.put("fileId", item.getFileId());
                sourceItem.put("fileName", fileName);
                sourceItem.put("excerpt", excerpt);
                sourceItem.put("score", item.getScore());
                knowledgeSources.add(sourceItem);
            }

            return new KnowledgeBundle(
                    truncate(referenceText.toString(), MAX_REFERENCE_TEXT),
                    new ArrayList<>(attachmentNames),
                    knowledgeSources
            );
        } catch (Exception e) {
            log.warn("Knowledge retrieval failed during lesson-plan enrichment: userId={}, query={}", userId, query, e);
            return KnowledgeBundle.empty();
        }
    }

    private List<KnowledgeSearchResponse.KnowledgeSearchResult> filterKnowledgeResults(
            List<KnowledgeSearchResponse.KnowledgeSearchResult> results,
            TeachingBlueprint blueprint
    ) {
        List<String> anchorTerms = buildAnchorTerms(blueprint);
        return results.stream()
                .filter(item -> item != null && hasText(item.getText()))
                .filter(item -> isRelevantResult(item, anchorTerms))
                .limit(MAX_ACCEPTED_RESULTS)
                .toList();
    }

    private boolean isRelevantResult(KnowledgeSearchResponse.KnowledgeSearchResult item, List<String> anchorTerms) {
        float score = item.getScore() != null ? item.getScore() : 0F;
        if (anchorTerms.isEmpty()) {
            return score >= MIN_ACCEPTED_SCORE;
        }

        String haystack = normalizeText(item.getFileName()) + "\n" + normalizeText(item.getText());
        int matchCount = 0;
        for (String anchor : anchorTerms) {
            if (haystack.contains(anchor)) {
                matchCount++;
            }
        }
        return matchCount >= 2 || (matchCount >= 1 && score >= MIN_ACCEPTED_SCORE);
    }

    private List<String> buildAnchorTerms(TeachingBlueprint blueprint) {
        LinkedHashSet<String> terms = new LinkedHashSet<>();
        if (blueprint == null) {
            return List.of();
        }

        addAnchorTerms(terms, blueprint.getCore() != null ? blueprint.getCore().getSubject() : null);
        addAnchorTerms(terms, blueprint.getCore() != null ? blueprint.getCore().getTopic() : null);
        for (String knowledgePoint : extractStringList(blueprint, "knowledgePoints", "知识点")) {
            addAnchorTerms(terms, knowledgePoint);
        }
        return new ArrayList<>(terms);
    }

    private void addAnchorTerms(LinkedHashSet<String> terms, String raw) {
        if (!hasText(raw)) {
            return;
        }

        String normalized = normalizeText(raw);
        if (normalized.length() >= 2) {
            terms.add(normalized);
        }

        String compact = normalizeText(removeWeakConnectors(raw));
        if (compact.length() >= 2) {
            terms.add(compact);
        }

        for (String part : raw.split("[\\s,，。！？、；:：()（）\\[\\]【】<>《》\"'`]+")) {
            String normalizedPart = normalizeText(part);
            if (normalizedPart.length() >= 2) {
                terms.add(normalizedPart);
            }

            String compactPart = normalizeText(removeWeakConnectors(part));
            if (compactPart.length() >= 2) {
                terms.add(compactPart);
            }
        }
    }

    private TeachingBlueprint applyKnowledgeBundle(TeachingBlueprint blueprint, KnowledgeBundle bundle) {
        TeachingBlueprint result = cloneBlueprint(blueprint);
        if (hasText(bundle.referenceText())) {
            result.putExtension("referenceText", bundle.referenceText());
        }
        if (!bundle.attachmentNames().isEmpty()) {
            result.putExtension("attachmentNames", bundle.attachmentNames());
        }
        if (!bundle.knowledgeSources().isEmpty()) {
            result.putExtension("knowledgeSources", bundle.knowledgeSources());
        }
        return result;
    }

    private TeachingBlueprint cloneBlueprint(TeachingBlueprint blueprint) {
        return TeachingBlueprint.fromLegacyMap(blueprint != null ? blueprint.toFlatMap() : Map.of());
    }

    private String buildKnowledgeQuery(TeachingBlueprint blueprint) {
        if (blueprint == null) {
            return "";
        }

        return Stream.of(
                        blueprint.getCore() != null ? blueprint.getCore().getSubject() : null,
                        blueprint.getCore() != null ? blueprint.getCore().getGrade() : null,
                        blueprint.getCore() != null ? blueprint.getCore().getTopic() : null,
                        joinValues(extractStringList(blueprint, "knowledgePoints", "知识点"))
                )
                .filter(this::hasText)
                .collect(Collectors.joining(" "));
    }

    private List<String> extractStringList(TeachingBlueprint blueprint, String... keys) {
        if (blueprint == null || keys == null) {
            return List.of();
        }

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

        return Stream.of(value.split("[,，、\n]"))
                .map(String::trim)
                .filter(this::hasText)
                .distinct()
                .toList();
    }

    private String joinValues(List<String> values) {
        return values == null || values.isEmpty() ? "" : String.join("、", values);
    }

    private String truncate(String value, int maxLength) {
        if (!hasText(value) || value.length() <= maxLength) {
            return valueOrEmpty(value);
        }
        return value.substring(0, maxLength);
    }

    private String removeWeakConnectors(String value) {
        String compact = value == null ? "" : value.trim();
        for (String connector : WEAK_CONNECTORS) {
            compact = compact.replace(connector, "");
        }
        return compact.trim();
    }

    private String normalizeText(String value) {
        if (!hasText(value)) {
            return "";
        }
        return value.toLowerCase(Locale.ROOT)
                .replaceAll("[\\s\\p{Punct}，。！？、；：“”‘’（）《》【】]+", "");
    }

    private String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }

    private String asString(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private record KnowledgeBundle(
            String referenceText,
            List<String> attachmentNames,
            List<Map<String, Object>> knowledgeSources
    ) {
        boolean isEmpty() {
            return (referenceText == null || referenceText.isBlank())
                    && (attachmentNames == null || attachmentNames.isEmpty())
                    && (knowledgeSources == null || knowledgeSources.isEmpty());
        }

        static KnowledgeBundle empty() {
            return new KnowledgeBundle("", List.of(), List.of());
        }
    }
}
