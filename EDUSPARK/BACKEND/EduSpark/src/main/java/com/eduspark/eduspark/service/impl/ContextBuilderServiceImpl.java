package com.eduspark.eduspark.service.impl;

import com.eduspark.eduspark.dto.knowledge.KnowledgeSearchResponse;
import com.eduspark.eduspark.service.IContextBuilderService;
import com.eduspark.eduspark.service.IWebSearchService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 上下文构建服务实现
 */
@Service
public class ContextBuilderServiceImpl implements IContextBuilderService {

    @Override
    public String buildKnowledgeContext(KnowledgeSearchResponse result, int maxTokens) {
        if (result == null || result.getResults() == null || result.getResults().isEmpty()) {
            return null;
        }

        StringBuilder ctx = new StringBuilder();
        int usedTokens = 0;
        int index = 1;

        for (KnowledgeSearchResponse.KnowledgeSearchResult item : result.getResults()) {
            int tokens = item.getText().length() / 2;
            if (usedTokens + tokens > maxTokens) break;

            ctx.append("[").append(index).append("] ")
               .append("【来源: ").append(item.getFileName()).append("】\n")
               .append(item.getText()).append("\n\n");
            usedTokens += tokens;
            index++;
        }

        return ctx.toString();
    }

    @Override
    public String buildWebContext(IWebSearchService.SearchResult webResult) {
        if (webResult == null || webResult.items() == null) return "";

        StringBuilder ctx = new StringBuilder();
        for (IWebSearchService.SearchItem item : webResult.items()) {
            ctx.append("【").append(item.title()).append("】\n")
               .append(item.snippet()).append("\n\n");
        }
        return ctx.toString();
    }

    @Override
    public String buildReferencedFileContext(List<KnowledgeSearchResponse.KnowledgeSearchResult> results) {
        if (results == null || results.isEmpty()) return null;

        StringBuilder ctx = new StringBuilder();
        ctx.append("【用户引用的文档内容】\n\n");
        for (KnowledgeSearchResponse.KnowledgeSearchResult item : results) {
            ctx.append(item.getText()).append("\n\n");
        }
        return ctx.toString();
    }
}
