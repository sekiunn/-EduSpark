package com.eduspark.eduspark.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.eduspark.eduspark.pojo.entity.PptDocument;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * Mapper for editable PPT workspace documents.
 */
@Mapper
public interface PptDocumentMapper extends BaseMapper<PptDocument> {

    @Select("""
            SELECT *
            FROM ppt_document
            WHERE session_id = #{sessionId}
              AND is_deleted = 0
            ORDER BY id DESC
            LIMIT 1
            """)
    PptDocument selectLatestBySessionId(@Param("sessionId") Long sessionId);

    @Select("""
            SELECT *
            FROM ppt_document
            WHERE id = #{documentId}
              AND user_id = #{userId}
              AND is_deleted = 0
            """)
    PptDocument selectOwnedById(@Param("documentId") Long documentId, @Param("userId") Long userId);
}
