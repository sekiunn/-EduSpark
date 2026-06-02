package com.eduspark.eduspark.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.eduspark.eduspark.pojo.entity.LessonPlanDocument;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * Mapper for editable lesson-plan workspace documents.
 */
@Mapper
public interface LessonPlanDocumentMapper extends BaseMapper<LessonPlanDocument> {

    @Select("""
            SELECT *
            FROM lesson_plan_document
            WHERE session_id = #{sessionId}
              AND is_deleted = 0
            ORDER BY id DESC
            LIMIT 1
            """)
    LessonPlanDocument selectLatestBySessionId(@Param("sessionId") Long sessionId);

    @Select("""
            SELECT *
            FROM lesson_plan_document
            WHERE id = #{documentId}
              AND user_id = #{userId}
              AND is_deleted = 0
            """)
    LessonPlanDocument selectOwnedById(@Param("documentId") Long documentId, @Param("userId") Long userId);
}
