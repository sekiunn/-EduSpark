package com.eduspark.eduspark.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.eduspark.eduspark.pojo.entity.InteractiveDocument;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * Mapper for editable interactive workspace documents.
 */
@Mapper
public interface InteractiveDocumentMapper extends BaseMapper<InteractiveDocument> {

    @Select("""
            SELECT *
            FROM interactive_document
            WHERE session_id = #{sessionId}
              AND is_deleted = 0
            ORDER BY id DESC
            LIMIT 1
            """)
    InteractiveDocument selectLatestBySessionId(@Param("sessionId") Long sessionId);

    @Select("""
            SELECT *
            FROM interactive_document
            WHERE id = #{documentId}
              AND user_id = #{userId}
              AND is_deleted = 0
            """)
    InteractiveDocument selectOwnedById(@Param("documentId") Long documentId, @Param("userId") Long userId);
}
