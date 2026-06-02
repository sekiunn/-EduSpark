package com.eduspark.eduspark.dto.chat;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Minimal blueprint used by the teaching-mode flow.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TeachingBlueprint implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private TeachingCore core;
    private Map<String, Object> extensions;

    public static TeachingBlueprint fromLegacyMap(Map<String, Object> legacyMap) {
        TeachingCore core = TeachingCore.fromMap(legacyMap);
        Map<String, Object> ext = new LinkedHashMap<>();

        if (legacyMap != null) {
            Set<String> reservedKeys = Set.of(
                    "subject", "grade", "topic", "duration",
                    "学科", "科目", "年级", "学段", "课题", "主题", "标题", "title", "时长", "课时长", "时间"
            );
            for (Map.Entry<String, Object> entry : legacyMap.entrySet()) {
                if (!reservedKeys.contains(entry.getKey())) {
                    ext.put(entry.getKey(), entry.getValue());
                }
            }
        }

        return TeachingBlueprint.builder()
                .core(core != null ? core : new TeachingCore())
                .extensions(ext)
                .build();
    }

    public Map<String, Object> toFlatMap() {
        Map<String, Object> result = new LinkedHashMap<>();
        if (core != null) {
            result.putAll(core.toMap());
        }
        if (extensions != null && !extensions.isEmpty()) {
            result.putAll(extensions);
        }
        return result;
    }

    public void putExtension(String key, Object value) {
        if (key == null || key.isBlank() || value == null) {
            return;
        }
        if (extensions == null) {
            extensions = new LinkedHashMap<>();
        }
        extensions.put(key, value);
    }

    public Object getExtension(String key) {
        if (extensions == null || key == null) {
            return null;
        }
        return extensions.get(key);
    }

    public boolean hasAnyContent() {
        return (core != null && !core.toMap().isEmpty())
                || (extensions != null && !extensions.isEmpty());
    }
}
