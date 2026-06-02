package com.eduspark.eduspark.service;

/**
 * 联网搜索服务接口
 */
public interface IWebSearchService {

    /**
     * 联网搜索
     *
     * @param query 搜索关键词
     * @param topK  返回数量
     * @return 搜索结果列表
     */
    SearchResult search(String query, int topK);

    /**
     * 搜索结果
     */
    record SearchResult(
            String query,
            int total,
            java.util.List<SearchItem> items,
            boolean success,
            String error
    ) {}

    /**
     * 单条搜索结果
     */
    record SearchItem(
            String title,
            String snippet,
            String url
    ) {}
}
