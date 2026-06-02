package com.eduspark.eduspark.dto.interactive;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Hidden implementation context for the interactive mode.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InteractiveContext implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String subject;

    private String grade;

    private String topic;

    private String scene;

    private String interactionType;

    private String pageGoal;

    private String studentAction;

    private List<String> mustHaveInteractions;

    private String teachingFocus;

    private String styleHint;

    private List<String> constraints;

    private String notes;

    private String referenceText;

    private List<String> attachmentNames;

    private Integer currentVersion;

    private String renderMode;

    private String interactiveType;

    private String templateId;

    private String templateVersion;

    private Map<String, Object> templateSpec;

    public static InteractiveContext fromMap(Map<String, Object> raw) {
        if (raw == null || raw.isEmpty()) {
            return empty();
        }
        return InteractiveContext.builder()
                .subject(firstText(raw, "subject", "学科"))
                .grade(firstText(raw, "grade", "年级", "学段", "audience"))
                .topic(firstText(raw, "topic", "title", "课题", "主题"))
                .scene(firstText(raw, "scene", "usageScene", "使用场景", "场景"))
                .interactionType(firstText(raw, "interactionType", "deliveryFormat", "interactionMode", "承载形式", "互动类型"))
                .pageGoal(firstText(raw, "pageGoal", "goal", "pageTarget", "页面目标"))
                .studentAction(firstText(raw, "studentAction", "interactionIdea", "学生要做什么", "互动构想"))
                .mustHaveInteractions(firstList(raw, "mustHaveInteractions", "interactionHints"))
                .teachingFocus(firstText(raw, "teachingFocus", "teachingMethod", "teachingFocusHint"))
                .styleHint(firstText(raw, "styleHint", "visualStyle", "animationLevel", "style"))
                .constraints(firstList(raw, "constraints", "userConstraints"))
                .notes(firstText(raw, "notes"))
                .referenceText(firstText(raw, "referenceText"))
                .attachmentNames(firstList(raw, "attachmentNames"))
                .currentVersion(firstInteger(raw.get("currentVersion"), 1))
                .renderMode(firstText(raw, "renderMode"))
                .interactiveType(firstText(raw, "interactiveType"))
                .templateId(firstText(raw, "templateId"))
                .templateVersion(firstText(raw, "templateVersion"))
                .templateSpec(firstMap(raw, "templateSpec"))
                .build();
    }

    public static InteractiveContext empty() {
        return InteractiveContext.builder()
                .mustHaveInteractions(List.of())
                .constraints(List.of())
                .attachmentNames(List.of())
                .currentVersion(1)
                .templateSpec(Map.of())
                .build();
    }

    public Map<String, Object> toMap() {
        Map<String, Object> result = new LinkedHashMap<>();
        putIfPresent(result, "subject", subject);
        putIfPresent(result, "grade", grade);
        putIfPresent(result, "topic", topic);
        putIfPresent(result, "scene", scene);
        putIfPresent(result, "interactionType", interactionType);
        putIfPresent(result, "pageGoal", pageGoal);
        putIfPresent(result, "studentAction", studentAction);
        putIfPresent(result, "mustHaveInteractions", mustHaveInteractions);
        putIfPresent(result, "teachingFocus", teachingFocus);
        putIfPresent(result, "styleHint", styleHint);
        putIfPresent(result, "constraints", constraints);
        putIfPresent(result, "notes", notes);
        putIfPresent(result, "referenceText", referenceText);
        putIfPresent(result, "attachmentNames", attachmentNames);
        putIfPresent(result, "currentVersion", currentVersion);
        putIfPresent(result, "renderMode", renderMode);
        putIfPresent(result, "interactiveType", interactiveType);
        putIfPresent(result, "templateId", templateId);
        putIfPresent(result, "templateVersion", templateVersion);
        putIfPresent(result, "templateSpec", templateSpec);
        return result;
    }

    public List<String> safeMustHaveInteractions() {
        return mustHaveInteractions == null ? List.of() : mustHaveInteractions;
    }

    public List<String> safeConstraints() {
        return constraints == null ? List.of() : constraints;
    }

    public List<String> safeAttachmentNames() {
        return attachmentNames == null ? List.of() : attachmentNames;
    }

    public Map<String, Object> safeTemplateSpec() {
        return templateSpec == null ? Map.of() : templateSpec;
    }

    private static void putIfPresent(Map<String, Object> target, String key, Object value) {
        if (value == null) {
            return;
        }
        if (value instanceof String text && text.isBlank()) {
            return;
        }
        if (value instanceof Collection<?> collection && collection.isEmpty()) {
            return;
        }
        target.put(key, value);
    }

    private static String firstText(Map<String, Object> raw, String... keys) {
        return Stream.of(keys)
                .map(raw::get)
                .map(InteractiveContext::normalizeText)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    private static List<String> firstList(Map<String, Object> raw, String... keys) {
        return Stream.of(keys)
                .map(raw::get)
                .map(InteractiveContext::normalizeList)
                .filter(list -> !list.isEmpty())
                .findFirst()
                .orElse(List.of());
    }

    private static Map<String, Object> firstMap(Map<String, Object> raw, String... keys) {
        return Stream.of(keys)
                .map(raw::get)
                .map(InteractiveContext::normalizeMap)
                .filter(map -> !map.isEmpty())
                .findFirst()
                .orElse(Map.of());
    }

    private static String normalizeText(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() || "null".equalsIgnoreCase(text) ? null : text;
    }

    private static List<String> normalizeList(Object value) {
        if (value == null) {
            return List.of();
        }
        if (value instanceof Collection<?> collection) {
            return collection.stream()
                    .map(InteractiveContext::normalizeText)
                    .filter(Objects::nonNull)
                    .distinct()
                    .toList();
        }
        String text = normalizeText(value);
        if (text == null) {
            return List.of();
        }
        return Stream.of(text.split("[,，。；;\\n]"))
                .map(String::trim)
                .filter(part -> !part.isBlank())
                .distinct()
                .toList();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> normalizeMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            return map.entrySet().stream()
                    .filter(entry -> normalizeText(entry.getKey()) != null)
                    .collect(LinkedHashMap::new,
                            (target, entry) -> target.put(String.valueOf(entry.getKey()), entry.getValue()),
                            LinkedHashMap::putAll);
        }
        if (value instanceof String textValue) {
            String text = normalizeText(textValue);
            if (text == null || !text.startsWith("{") || !text.endsWith("}")) {
                return Map.of();
            }
        }
        return Map.of();
    }

    private static Integer firstInteger(Object value, int defaultValue) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        String text = normalizeText(value);
        if (text == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }
}
