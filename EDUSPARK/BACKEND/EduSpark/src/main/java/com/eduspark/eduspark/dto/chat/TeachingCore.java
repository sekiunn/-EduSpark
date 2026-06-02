package com.eduspark.eduspark.dto.chat;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Minimal structured fields required before generation.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TeachingCore implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String subject;
    private String grade;
    private String topic;
    private Integer duration;

    public boolean isComplete() {
        return hasText(subject)
                && hasText(grade)
                && hasText(topic)
                && duration != null
                && duration > 0;
    }

    public List<String> getMissingFields() {
        List<String> missing = new ArrayList<>();
        if (!hasText(subject)) {
            missing.add("学科");
        }
        if (!hasText(grade)) {
            missing.add("年级");
        }
        if (!hasText(topic)) {
            missing.add("课题");
        }
        if (duration == null || duration <= 0) {
            missing.add("课时长");
        }
        return missing;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> result = new LinkedHashMap<>();
        if (hasText(subject)) {
            result.put("subject", subject.trim());
        }
        if (hasText(grade)) {
            result.put("grade", grade.trim());
        }
        if (hasText(topic)) {
            result.put("topic", topic.trim());
        }
        if (duration != null && duration > 0) {
            result.put("duration", duration);
        }
        return result;
    }

    public static TeachingCore fromMap(Map<String, Object> source) {
        if (source == null || source.isEmpty()) {
            return new TeachingCore();
        }

        return TeachingCore.builder()
                .subject(readText(source, "subject", "学科", "科目"))
                .grade(readText(source, "grade", "年级", "学段"))
                .topic(readText(source, "topic", "课题", "主题", "标题", "title"))
                .duration(parseDuration(readText(source, "duration", "时长", "课时长", "时间")))
                .build();
    }

    public static Integer parseDuration(String raw) {
        if (!hasText(raw)) {
            return null;
        }

        String normalized = raw.trim();
        Matcher directNumber = Pattern.compile("(\\d{1,3})").matcher(normalized);
        if (directNumber.find()) {
            return Integer.parseInt(directNumber.group(1));
        }

        Map<String, Integer> chineseValues = Map.ofEntries(
                Map.entry("十", 10),
                Map.entry("十五", 15),
                Map.entry("二十", 20),
                Map.entry("三十", 30),
                Map.entry("四十", 40),
                Map.entry("四十五", 45),
                Map.entry("五十", 50),
                Map.entry("六十", 60),
                Map.entry("九十", 90)
        );

        for (Map.Entry<String, Integer> entry : chineseValues.entrySet()) {
            if (normalized.contains(entry.getKey())) {
                return entry.getValue();
            }
        }

        return null;
    }

    private static String readText(Map<String, Object> source, String... keys) {
        for (String key : keys) {
            Object value = source.get(key);
            if (value == null) {
                continue;
            }
            if (value instanceof Number number) {
                return String.valueOf(number);
            }
            String text = String.valueOf(value).trim();
            if (!text.isEmpty()) {
                return text;
            }
        }
        return null;
    }

    private static boolean hasText(String text) {
        return text != null && !text.isBlank();
    }
}
