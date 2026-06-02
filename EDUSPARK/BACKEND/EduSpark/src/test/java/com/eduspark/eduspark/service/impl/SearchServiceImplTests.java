package com.eduspark.eduspark.service.impl;

import com.eduspark.eduspark.mapper.knowledge.KnowledgeChunkMapper;
import com.eduspark.eduspark.service.IEmbeddingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SearchServiceImplTests {

    @Mock
    private KnowledgeChunkMapper knowledgeChunkMapper;

    @Mock
    private IEmbeddingService embeddingService;

    private SearchServiceImpl searchService;

    @BeforeEach
    void setUp() {
        searchService = new SearchServiceImpl(knowledgeChunkMapper, embeddingService);
    }

    @Test
    void hybridSearchShouldBuildStableBm25KeywordsForMixedLanguageQuery() {
        when(embeddingService.embed(anyString())).thenReturn(new float[]{0.1F, 0.2F});
        when(knowledgeChunkMapper.vectorSearch(anyString(), eq(1L), any(), eq(4))).thenReturn(List.of());
        when(knowledgeChunkMapper.bm25Search(anyString(), eq(1L), any(), eq(4))).thenReturn(List.of());

        searchService.hybridSearch("Java 大一 java的基础语法", 2, 1L, null, null, null);

        verify(knowledgeChunkMapper).bm25Search("Java OR 大一 OR java的基础语法 OR java基础语法", 1L, null, 4);
    }
}
