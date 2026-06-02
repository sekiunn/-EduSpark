package com.eduspark.eduspark.service.impl;

import com.eduspark.eduspark.dto.knowledge.KnowledgeSearchResponse;
import com.eduspark.eduspark.service.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 三层检索服务实现
 * <p>
 * 检索流程（仅检索，不生成回答）：
 * 1. 第一层：本地知识库检索（向量 + BM25）
 *    - 找到结果 → 返回 context，由调用方决定如何使用
 *    - 未找到结果 → 进入第二层
 *
 * 2. 第二层：联网搜索（用户开启时）
 *    - 开启但未找到结果 → 进入第三层
 *    - 关闭但未找到结果 → 返回引导消息
 *
 * 3. 第三层：返回 llmFallback 标记，由调用方调用 LLM 兜底
 */
@Slf4j
@Service
public class TriLevelSearchServiceImpl implements ITriLevelSearchService {

    private final ISearchService searchService;
    private final IKnowledgeService knowledgeService;
    private final IWebSearchService webSearchService;
    private final ILLMService llmService;
    private final ISystemPromptService systemPromptService;
    private final IContextBuilderService contextBuilderService;

    @Value("${rag.search.max-context-tokens:4000}")
    private int maxContextTokens;

    @Value("${rag.search.min-knowledge-threshold:0.5}")
    private float minKnowledgeThreshold;

    public TriLevelSearchServiceImpl(
            ISearchService searchService,
            IKnowledgeService knowledgeService,
            IWebSearchService webSearchService,
            ILLMService llmService,
            ISystemPromptService systemPromptService,
            IContextBuilderService contextBuilderService
    ) {
        this.searchService = searchService;
        this.knowledgeService = knowledgeService;
        this.webSearchService = webSearchService;
        this.llmService = llmService;
        this.systemPromptService = systemPromptService;
        this.contextBuilderService = contextBuilderService;
    }

    @Override
    public TriLevelSearchResult search(TriLevelSearchRequest request) {
        long startTime = System.currentTimeMillis();
        String query = request.query();

        log.info("三层检索开始: query={}, webSearch={}, userId={}",
                query, request.webSearchEnabled(), request.userId());

        try {
            // ========== 第一层：本地知识库检索 ==========
            log.debug("第一层：本地知识库检索");
            KnowledgeSearchResponse localResult = searchService.hybridSearch(
                    query, request.topK(), request.userId(), null, 0.6f, 0.4f
            );

            boolean hasLocalKnowledge = localResult != null
                    && localResult.getResults() != null
                    && !localResult.getResults().isEmpty();

            boolean hasFullKnowledge = hasLocalKnowledge
                    && localResult.getResults().get(0).getScore() >= minKnowledgeThreshold;
            boolean hasPartialKnowledge = hasLocalKnowledge
                    && localResult.getResults().get(0).getScore() >= 0.3f;

            if (hasLocalKnowledge) {
                float topScore = localResult.getResults().get(0).getScore();
                log.debug("第一层命中: results={}, topScore={}",
                        localResult.getTotal(), topScore);

                if (topScore < 0.01f) {
                    log.warn("知识库结果分数过低 ({}), 返回引导消息", topScore);
                    long cost = System.currentTimeMillis() - startTime;
                    return new TriLevelSearchResult(
                            query, buildGuidanceMessage(query), cost, 0, "知识库无相关内容",
                            localResult, null,
                            recommendUpload(query, null), false, false,
                            null, null
                    );
                }

                // 只构建上下文，不调 LLM！返回给调用方流式生成
                String context = contextBuilderService.buildKnowledgeContext(localResult, maxContextTokens);
                long cost = System.currentTimeMillis() - startTime;
                log.info("三层检索完成[第一层]: query={}, layer=1, cost={}ms",
                        query, cost);

                return new TriLevelSearchResult(
                        query, null, cost, 1, "本地知识库",
                        localResult, null,
                        null, hasPartialKnowledge, hasFullKnowledge,
                        context, "rag"
                );

            } else if (request.webSearchEnabled()) {
                // ========== 无本地知识 + 联网开启 → 第二层联网搜索 ==========
                log.debug("第一层未命中，进入第二层：联网搜索");

                IWebSearchService.SearchResult webResult =
                        webSearchService.search(query, 5);

                if (webResult.success() && webResult.total() > 0) {
                    String webContext = contextBuilderService.buildWebContext(webResult);
                    long cost = System.currentTimeMillis() - startTime;

                    log.info("三层检索完成[第二层]: query={}, layer=2, cost={}ms",
                            query, cost);

                    return new TriLevelSearchResult(
                            query, null, cost, 2, "联网搜索",
                            localResult, webResult,
                            recommendUpload(query, null), false, false,
                            webContext, "webSearch"
                    );

                } else {
                    log.debug("第二层未命中，进入第三层：LLM兜底");
                    return buildFallbackResult(query, null, request.userId(), startTime);
                }

            } else {
                // ========== 无本地知识 + 联网关闭 ==========
                log.debug("第一层未命中，联网已关闭，返回引导");

                String displayQuery = request.originalQuery() != null ? request.originalQuery() : query;
                long cost = System.currentTimeMillis() - startTime;

                return new TriLevelSearchResult(
                        query, buildGuidanceMessage(displayQuery), cost, 0, "知识库空+联网关闭",
                        localResult, null,
                        recommendUpload(displayQuery, null), false, false,
                        null, null
                );
            }

        } catch (Exception e) {
            log.error("三层检索异常: query={}", query, e);
            return buildFallbackResult(query, null, request.userId(), startTime);
        }
    }

    /**
     * 构建第三层 LLM 兜底结果（不调 LLM，只返回标记）
     */
    private TriLevelSearchResult buildFallbackResult(
            String query,
            KnowledgeSearchResponse localResult,
            Long userId,
            long startTime
    ) {
        log.debug("第三层：LLM兜底（标记）");

        long cost = System.currentTimeMillis() - startTime;
        String recommended = recommendUpload(query, null);

        log.info("三层检索完成[第三层-兜底]: query={}, layer=3, cost={}ms", query, cost);

        return new TriLevelSearchResult(
                query, null, cost, 3, "LLM兜底",
                localResult, null,
                recommended, false, false,
                null, "llmFallback"
        );
    }

    /**
     * 推荐用户上传资料
     */
    private String recommendUpload(String query, KnowledgeSearchResponse localResult) {
        if (query == null || query.isBlank()) return null;

        String[] likelySubjects = detectSubject(query);

        if (likelySubjects.length > 0) {
            String subjects = String.join("、", likelySubjects);
            return String.format(
                    "💡 建议：为了更好地回答您关于「%s」的问题，" +
                    "您可以上传相关的教学资料（如教材、教案、习题等），" +
                    "系统会自动学习并在后续提供更精准的回答。",
                    subjects
            );
        }

        return null;
    }

    /**
     * 检测查询涉及的主题（简化版，可扩展为NLP分析）
     */
    private String[] detectSubject(String query) {
        Map<String, String[]> subjectKeywords = new LinkedHashMap<>();

        subjectKeywords.put("数学", new String[]{"函数", "方程", "几何", "代数", "微积分", "概率", "数列", "向量", "矩阵"});
        subjectKeywords.put("物理", new String[]{"力学", "电磁", "光学", "热学", "量子", "牛顿", "能量", "运动"});
        subjectKeywords.put("化学", new String[]{"元素", "反应", "分子", "原子", "化学键", "方程式", "有机"});
        subjectKeywords.put("语文", new String[]{"古诗", "文言", "作文", "阅读", "修辞", "语法"});
        subjectKeywords.put("英语", new String[]{"语法", "词汇", "阅读", "写作", "听力"});
        subjectKeywords.put("历史", new String[]{"朝代", "战争", "革命", "文明", "事件"});
        subjectKeywords.put("生物", new String[]{"细胞", "基因", "生态", "进化", "器官"});

        List<String> detected = new ArrayList<>();

        for (Map.Entry<String, String[]> entry : subjectKeywords.entrySet()) {
            for (String keyword : entry.getValue()) {
                if (query.contains(keyword)) {
                    detected.add(entry.getKey());
                    break;
                }
            }
        }

        return detected.toArray(new String[0]);
    }

    /**
     * 构建引导消息
     */
    private String buildGuidanceMessage(String query) {
        StringBuilder msg = new StringBuilder();

        msg.append("🔍 我在您的本地知识库中没有找到与「").append(query).append("」直接相关的内容。\n\n");
        msg.append("💡 您可以：\n");
        msg.append("1. 点击「联网搜索」开关，让我从网络获取相关信息\n");
        msg.append("2. 上传相关的教学资料（如教材、教案），我会自动学习\n");
        msg.append("3. 换一个更具体的问题试试\n\n");

        String recommended = recommendUpload(query, null);
        if (recommended != null) {
            msg.append("\n").append(recommended);
        }

        return msg.toString();
    }
}
