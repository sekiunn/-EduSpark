package com.eduspark.eduspark.service.impl;

import com.eduspark.eduspark.service.IWebSearchService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

/**
 * 联网搜索服务实现
 * <p>
 * 使用 DuckDuckGo 免API Key的搜索接口
 * 如需更强大的搜索能力，可替换为 SerpAPI / Google Custom Search API / 百度搜索API
 */
@Slf4j
@Service
public class WebSearchServiceImpl implements IWebSearchService {

    private final RestTemplate restTemplate;

    @Value("${websearch.enabled:true}")
    private boolean enabled;

    @Value("${websearch.base-url:https://api.duckduckgo.com}")
    private String baseUrl;

    public WebSearchServiceImpl(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Override
    public SearchResult search(String query, int topK) {
        if (!enabled) {
            log.debug("联网搜索已禁用");
            return new SearchResult(query, 0, Collections.emptyList(), false, "联网搜索已禁用");
        }

        try {
            log.info("联网搜索: query={}, topK={}", query, topK);

            // DuckDuckGo Instant Answer API
            String url = baseUrl + "/?q=" + encode(query) + "&format=json&no_html=1&skip_disambig=1";

            Map<String, Object> response = restTemplate.getForObject(url, Map.class);
            if (response == null) {
                return new SearchResult(query, 0, Collections.emptyList(), false, "搜索服务返回为空");
            }

            List<SearchItem> items = new ArrayList<>();

            // 解析AbstractText
            Object abstractText = response.get("AbstractText");
            if (abstractText instanceof String text && !text.isBlank()) {
                Object heading = response.get("Heading");
                String title = heading instanceof String h ? h : query;
                items.add(new SearchItem(title, text, (String) response.get("AbstractURL")));
            }

            // 解析RelatedTopics
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> relatedTopics =
                    (List<Map<String, Object>>) response.get("RelatedTopics");
            if (relatedTopics != null && items.size() < topK) {
                for (Map<String, Object> topic : relatedTopics) {
                    if (items.size() >= topK) break;

                    Object text = topic.get("Text");
                    Object icon = topic.get("Icon");
                    if (text instanceof String snippet && !snippet.isBlank()) {
                        String iconUrl = icon instanceof Map<?, ?> m
                                ? (String) m.get("URL") : null;
                        if (iconUrl != null && iconUrl.contains("gif")) continue; // 跳过广告图片

                        items.add(new SearchItem(
                                snippet.length() > 50 ? snippet.substring(0, 50) + "..." : snippet,
                                snippet,
                                (String) topic.get("FirstURL")
                        ));
                    }
                }
            }

            log.info("联网搜索完成: query={}, results={}", query, items.size());

            return new SearchResult(query, items.size(), items, true, null);

        } catch (Exception e) {
            log.error("联网搜索失败: query={}, error={}", query, e.getMessage());
            return new SearchResult(query, 0, Collections.emptyList(), false,
                    "搜索服务暂时不可用: " + e.getMessage());
        }
    }

    private String encode(String str) {
        try {
            return java.net.URLEncoder.encode(str, "UTF-8");
        } catch (Exception e) {
            return str;
        }
    }
}
