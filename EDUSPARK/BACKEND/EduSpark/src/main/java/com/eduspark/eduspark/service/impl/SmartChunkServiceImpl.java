package com.eduspark.eduspark.service.impl;

import com.eduspark.eduspark.exception.BusinessException;
import com.eduspark.eduspark.exception.ErrorCode;
import com.eduspark.eduspark.service.ISmartChunkService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 智能分块服务实现
 */
@Slf4j
@Service
public class SmartChunkServiceImpl implements ISmartChunkService {

    @Value("${rag.chunk.target-size:600}")
    private int targetSize;

    @Value("${rag.chunk.min-size:100}")
    private int minSize;

    @Value("${rag.chunk.overlap:80}")
    private int overlap;

    @Override
    public List<ChunkResult> chunkText(String text) {
        if (text == null || text.isBlank()) return Collections.emptyList();

        try {
            text = cleanText(text);
            if (text.isBlank()) return Collections.emptyList();

            List<String> units = splitBySemanticBoundary(text);
            List<ChunkResult> chunks = buildChunks(units);

            log.debug("文本分块完成: 原始={}, 分块数={}", text.length(), chunks.size());
            return chunks;

        } catch (Exception e) {
            throw new BusinessException(ErrorCode.KNOWLEDGE_CHUNK_ERROR,
                    "文本分块失败: " + e.getMessage(), e);
        }
    }

    private String cleanText(String text) {
        return text.replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]", "")
                .replaceAll("[ \\t]+", " ")
                .replaceAll("\\r\\n", "\\n")
                .trim();
    }

    private List<String> splitBySemanticBoundary(String text) {
        String[] parts = text.split("\\n{2,}");
        if (parts.length > 1) {
            return Arrays.stream(parts).filter(s -> !s.isBlank()).map(String::trim).toList();
        }
        parts = text.split("[。！？；\\n]");
        if (parts.length > 1) {
            return Arrays.stream(parts).filter(s -> !s.isBlank()).map(String::trim).toList();
        }
        parts = text.split("[，,]");
        if (parts.length > 1) {
            return Arrays.stream(parts).filter(s -> !s.isBlank()).map(String::trim).toList();
        }
        return Collections.singletonList(text.trim());
    }

    private List<ChunkResult> buildChunks(List<String> units) {
        List<ChunkResult> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        for (String unit : units) {
            if (unit.isBlank()) continue;

            if (current.length() + unit.length() <= targetSize) {
                if (current.length() > 0) current.append("\n");
                current.append(unit);
            } else {
                if (current.length() >= minSize) {
                    chunks.add(makeChunk(current.toString()));
                    current = new StringBuilder(getOverlapText(current.toString()));
                }
                if (unit.length() > targetSize) {
                    List<String> subs = recursiveSplit(unit);
                    for (String sub : subs) {
                        if (sub.length() >= minSize) chunks.add(makeChunk(sub));
                    }
                    current = new StringBuilder();
                } else {
                    current.append("\\n").append(unit);
                }
            }
        }

        if (current.length() >= minSize) chunks.add(makeChunk(current.toString()));
        return chunks;
    }

    private List<String> recursiveSplit(String text) {
        List<String> result = new ArrayList<>();
        if (text.length() <= targetSize) {
            result.add(text);
            return result;
        }
        int split = findBestSplitPoint(text);
        if (split <= 0 || split >= text.length()) split = targetSize;

        String first = text.substring(0, split).trim();
        String second = text.substring(split).trim();
        if (!first.isBlank()) result.add(first);
        if (!second.isBlank()) result.addAll(recursiveSplit(second));
        return result;
    }

    private int findBestSplitPoint(String text) {
        String seg = text.substring(0, Math.min(targetSize, text.length()));
        Matcher m = Pattern.compile("[。！？；\\n]").matcher(seg);
        int last = -1;
        while (m.find()) last = m.end();
        if (last > targetSize * 0.3) return last;

        m = Pattern.compile("[，,]").matcher(seg);
        last = -1;
        while (m.find()) last = m.end();
        return last > 0 ? last : targetSize;
    }

    private String getOverlapText(String text) {
        return text.length() <= overlap ? text : text.substring(text.length() - overlap);
    }

    private ChunkResult makeChunk(String text) {
        return new ChunkResult(text.trim(), (int) (text.length() * 0.5));
    }
}
