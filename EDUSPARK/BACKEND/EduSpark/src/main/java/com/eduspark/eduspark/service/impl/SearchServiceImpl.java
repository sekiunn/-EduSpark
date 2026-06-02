package com.eduspark.eduspark.service.impl;

import com.eduspark.eduspark.dto.knowledge.KnowledgeSearchResponse;
import com.eduspark.eduspark.mapper.knowledge.KnowledgeChunkMapper;
import com.eduspark.eduspark.pojo.entity.KnowledgeChunk;
import com.eduspark.eduspark.service.IEmbeddingService;
import com.eduspark.eduspark.service.ISearchService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;

/**
 * 混合检索服务实现
 * 算法：向量检索 + BM25 + RRF 倒数排名融合
 */
@Slf4j
@Service
public class SearchServiceImpl implements ISearchService {

    private static final int RRF_K = 10;
    private static final String QUERY_SPLIT_REGEX = "[\\s,，。！？、；：:()（）\\[\\]【】<>《》\"'`]+";
    private static final Set<String> QUERY_STOP_WORDS = Set.of(
            "\u7684", "\u4e86", "\u662f", "\u5728", "\u6709", "\u548c", "\u4e2d", "\u7b49", "\u8fd9", "\u90a3",
            "\u4e2a", "\u4eec", "\u600e\u4e48", "\u4ec0\u4e48", "\u5982\u4f55", "\u4e3a\u4ec0\u4e48",
            "\u54ea\u4e9b", "\u90a3\u4e2a", "\u8fd9\u4e2a", "\u5173\u4e8e", "\u6709\u5173", "\u76f8\u5173"
    );
    private static final List<String> INNER_CONNECTORS = List.of(
            "\u7684", "\u5730", "\u5f97", "\u4e86", "\u548c", "\u4e0e", "\u53ca", "\u53ca\u5176",
            "\u5e76", "\u5e76\u4e14", "\u4ee5\u53ca"
    );

    private final KnowledgeChunkMapper chunkMapper;
    private final IEmbeddingService embeddingService;
    private final Executor searchTaskExecutor;

    @Value("${rag.search.weights.vector:0.6}")
    private float vectorWeight;

    @Value("${rag.search.weights.bm25:0.4}")
    private float bm25Weight;

    @Autowired
    public SearchServiceImpl(KnowledgeChunkMapper chunkMapper,
                             IEmbeddingService embeddingService,
                             @Qualifier("searchTaskExecutor") Executor searchTaskExecutor) {
        this.chunkMapper = chunkMapper;
        this.embeddingService = embeddingService;
        this.searchTaskExecutor = searchTaskExecutor;
    }

    SearchServiceImpl(KnowledgeChunkMapper chunkMapper, IEmbeddingService embeddingService) {
        this(chunkMapper, embeddingService, Runnable::run);
    }

    @Override
    public KnowledgeSearchResponse hybridSearch(
            String query,
            int topK,
            Long userId,
            Long workspaceId,
            Float vectorW,
            Float bm25W
    ) {
        long startTime = System.currentTimeMillis();

        float vw = vectorW != null ? vectorW : vectorWeight;
        float bw = bm25W != null ? bm25W : bm25Weight;

        float[] queryVector = embeddingService.embed(query);
        String vectorArray = toPostgresVector(queryVector);
        String bm25Keywords = extractKeywords(query);
        CompletableFuture<List<KnowledgeChunk>> vectorFuture = CompletableFuture.supplyAsync(
                () -> chunkMapper.vectorSearch(vectorArray, userId, workspaceId, topK * 2),
                searchTaskExecutor
        );
        CompletableFuture<List<KnowledgeChunk>> bm25Future = CompletableFuture.supplyAsync(
                () -> chunkMapper.bm25Search(bm25Keywords, userId, workspaceId, topK * 2),
                searchTaskExecutor
        );

        List<KnowledgeChunk> vectorResults = await(vectorFuture);
        List<KnowledgeChunk> bm25Results = await(bm25Future);

        List<KnowledgeSearchResponse.KnowledgeSearchResult> fused =
                rrfFusion(vectorResults, bm25Results, vw, bw, topK);

        long cost = System.currentTimeMillis() - startTime;
        log.debug("混合检索完成: query={}, vector={}, bm25={}, fused={}, cost={}ms",
                query, vectorResults.size(), bm25Results.size(), fused.size(), cost);

        return KnowledgeSearchResponse.builder()
                .query(query)
                .costMs(cost)
                .total(fused.size())
                .results(fused)
                .build();
    }

    private <T> T await(CompletableFuture<T> future) {
        try {
            return future.join();
        } catch (CompletionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException("Parallel search execution failed", cause);
        }
    }

    private List<KnowledgeSearchResponse.KnowledgeSearchResult> rrfFusion(
            List<KnowledgeChunk> vectorResults,
            List<KnowledgeChunk> bm25Results,
            float vw,
            float bw,
            int topK
    ) {
        Map<Long, KnowledgeChunk> merged = new LinkedHashMap<>();
        for (KnowledgeChunk chunk : vectorResults) {
            merged.put(chunk.getId(), chunk);
        }
        for (KnowledgeChunk chunk : bm25Results) {
            merged.computeIfAbsent(chunk.getId(), ignored -> chunk);
        }

        List<ScoredResult> scored = new ArrayList<>();
        for (KnowledgeChunk chunk : merged.values()) {
            int vRank = getRank(vectorResults, chunk.getId());
            float vRrf = vRank > 0 ? vw / (RRF_K + vRank) : 0F;

            int bRank = getRank(bm25Results, chunk.getId());
            float bRrf = bRank > 0 ? bw / (RRF_K + bRank) : 0F;

            float rrfScore = vRrf + bRrf;

            KnowledgeSearchResponse.KnowledgeSearchResult result =
                    KnowledgeSearchResponse.KnowledgeSearchResult.builder()
                            .chunkId(chunk.getId())
                            .fileId(chunk.getFileId())
                            .fileName(chunk.getFileName())
                            .text(chunk.getChunkText())
                            .score(rrfScore)
                            .source("hybrid")
                            .vectorScore(normalize(chunk.getVectorScore()))
                            .bm25Score(normalize(chunk.getBm25Score()))
                            .build();

            scored.add(new ScoredResult(rrfScore, result));
        }

        return scored.stream()
                .sorted((left, right) -> Float.compare(right.score, left.score))
                .limit(topK)
                .map(ScoredResult::result)
                .toList();
    }

    private int getRank(List<KnowledgeChunk> results, Long chunkId) {
        if (results == null) {
            return 0;
        }
        for (int i = 0; i < results.size(); i++) {
            if (results.get(i).getId().equals(chunkId)) {
                return i + 1;
            }
        }
        return 0;
    }

    private float normalize(Float val) {
        if (val == null) {
            return 0F;
        }
        return Math.max(0F, Math.min(1F, val));
    }

    private String toPostgresVector(float[] arr) {
        if (arr == null || arr.length == 0) {
            return "[]";
        }
        StringBuilder builder = new StringBuilder("[");
        for (int i = 0; i < arr.length; i++) {
            builder.append(arr[i]);
            if (i < arr.length - 1) {
                builder.append(",");
            }
        }
        return builder.append("]").toString();
    }

    private String extractKeywords(String query) {
        if (query == null || query.isBlank()) {
            return query;
        }

        Map<String, String> keywords = new LinkedHashMap<>();
        for (String token : query.trim().split(QUERY_SPLIT_REGEX)) {
            collectKeyword(keywords, token);

            String compact = removeWeakConnectors(token);
            if (!compact.equals(token)) {
                collectKeyword(keywords, compact);
            }
        }

        String result = String.join(" OR ", keywords.values());
        log.debug("构建BM25查询: query={}, result={}", query, result);
        return result.isEmpty() ? query.trim() : result;
    }

    private void collectKeyword(Map<String, String> keywords, String candidate) {
        if (candidate == null) {
            return;
        }

        String trimmed = candidate.trim();
        if (trimmed.isEmpty()) {
            return;
        }

        String normalized = trimmed.toLowerCase(Locale.ROOT);
        if (QUERY_STOP_WORDS.contains(normalized)) {
            return;
        }
        if (normalized.length() == 1 && normalized.charAt(0) > 127) {
            return;
        }

        keywords.putIfAbsent(normalized, trimmed);
    }

    private String removeWeakConnectors(String value) {
        String compact = value == null ? "" : value.trim();
        for (String connector : INNER_CONNECTORS) {
            compact = compact.replace(connector, "");
        }
        return compact.trim();
    }

    private record ScoredResult(float score, KnowledgeSearchResponse.KnowledgeSearchResult result) {
    }
}
