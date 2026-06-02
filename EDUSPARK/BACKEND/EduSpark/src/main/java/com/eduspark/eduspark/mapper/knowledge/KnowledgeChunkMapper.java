package com.eduspark.eduspark.mapper.knowledge;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.eduspark.eduspark.pojo.entity.KnowledgeChunk;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 知识库分块 Mapper
 * 包含自定义向量检索和全文检索的原生SQL
 */
@Mapper
public interface KnowledgeChunkMapper extends BaseMapper<KnowledgeChunk> {

    /**
     * 向量相似度检索（使用 pgvector 余弦距离）
     * 返回按相似度降序排列的结果
     *
     * @param vectorArray 向量（PostgreSQL vector格式字符串）
     * @param userId      用户ID
     * @param limit       返回数量
     * @return 检索结果列表
     */
    @Select("""
        SELECT c.id, c.file_id, c.chunk_index, c.chunk_text,
               c.chunk_hash, c.token_count,
               1 - (c.embedding <=> CAST(#{vectorArray} AS VECTOR(1024))) AS similarity,
               f.file_name
        FROM knowledge_chunk c
        JOIN knowledge_file f ON c.file_id = f.id
        WHERE f.user_id = #{userId}
          AND f.status = 1
          AND c.embedding IS NOT NULL
          AND (CAST(#{workspaceId} AS BIGINT) IS NULL OR f.workspace_id = CAST(#{workspaceId} AS BIGINT))
        ORDER BY c.embedding <=> CAST(#{vectorArray} AS VECTOR(1024))
        LIMIT #{limit}
        """)
    @Results({
            @Result(column = "id", property = "id"),
            @Result(column = "file_id", property = "fileId"),
            @Result(column = "chunk_index", property = "chunkIndex"),
            @Result(column = "chunk_text", property = "chunkText"),
            @Result(column = "chunk_hash", property = "chunkHash"),
            @Result(column = "token_count", property = "tokenCount"),
            @Result(column = "similarity", property = "vectorScore"),
            @Result(column = "file_name", property = "fileName")
    })
    List<KnowledgeChunk> vectorSearch(
            @Param("vectorArray") String vectorArray,
            @Param("userId") Long userId,
            @Param("workspaceId") Long workspaceId,
            @Param("limit") int limit
    );

    /**
     * BM25全文检索（使用 PGroonga 中文分词）
     * 
     * PGroonga 自动分词，识别词语边界（如"氢氧化物"是一个词）
     * 使用 &@~ 操作符支持 OR 查询语法
     *
     * @param query  检索关键词（空格分隔表示 OR）
     * @param userId 用户ID
     * @param limit  返回数量
     * @return 检索结果列表
     */
    @Select("""
        SELECT c.id, c.file_id, c.chunk_index, c.chunk_text,
               c.chunk_hash, c.token_count,
               f.file_name,
               pgroonga_score(c.tableoid, c.ctid) AS rank_score
        FROM knowledge_chunk c
        JOIN knowledge_file f ON c.file_id = f.id
        WHERE f.user_id = #{userId}
          AND f.status = 1
          AND c.chunk_text &@~ #{keywords}
          AND (CAST(#{workspaceId} AS BIGINT) IS NULL OR f.workspace_id = CAST(#{workspaceId} AS BIGINT))
        ORDER BY rank_score DESC, c.id ASC
        LIMIT #{limit}
        """)
    @Results({
            @Result(column = "id", property = "id"),
            @Result(column = "file_id", property = "fileId"),
            @Result(column = "chunk_index", property = "chunkIndex"),
            @Result(column = "chunk_text", property = "chunkText"),
            @Result(column = "chunk_hash", property = "chunkHash"),
            @Result(column = "token_count", property = "tokenCount"),
            @Result(column = "rank_score", property = "bm25Score"),
            @Result(column = "file_name", property = "fileName")
    })
    List<KnowledgeChunk> bm25Search(
            @Param("keywords") String keywords,
            @Param("userId") Long userId,
            @Param("workspaceId") Long workspaceId,
            @Param("limit") int limit
    );

    /**
     * 根据文件ID删除所有分块
     */
    @Delete("DELETE FROM knowledge_chunk WHERE file_id = #{fileId}")
    int deleteByFileId(@Param("fileId") Long fileId);

    /**
     * 批量插入分块
     */
    @Insert("""
        INSERT INTO knowledge_chunk
            (id, file_id, chunk_index, chunk_text, chunk_hash,
             token_count, embedding, create_time)
        VALUES
            (#{chunk.id}, #{chunk.fileId}, #{chunk.chunkIndex},
             #{chunk.chunkText}, #{chunk.chunkHash},
             #{chunk.tokenCount}, #{chunk.embedding}::vector,
             #{chunk.createTime})
        ON CONFLICT (chunk_hash) DO NOTHING
        """)
    int insertChunk(@Param("chunk") KnowledgeChunk chunk);

    @Select("""
        SELECT id, file_id, chunk_index, chunk_text, chunk_hash, token_count, create_time
        FROM knowledge_chunk
        WHERE file_id = #{fileId}
        ORDER BY chunk_index ASC
        LIMIT #{limit}
        """)
    @Results({
            @Result(column = "id", property = "id"),
            @Result(column = "file_id", property = "fileId"),
            @Result(column = "chunk_index", property = "chunkIndex"),
            @Result(column = "chunk_text", property = "chunkText"),
            @Result(column = "chunk_hash", property = "chunkHash"),
            @Result(column = "token_count", property = "tokenCount"),
            @Result(column = "create_time", property = "createTime")
    })
    List<KnowledgeChunk> selectByFileIdLimit(
            @Param("fileId") Long fileId,
            @Param("limit") int limit
    );
}
