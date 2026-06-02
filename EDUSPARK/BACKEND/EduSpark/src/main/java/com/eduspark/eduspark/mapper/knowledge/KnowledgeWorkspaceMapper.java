package com.eduspark.eduspark.mapper.knowledge;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.eduspark.eduspark.pojo.entity.KnowledgeWorkspace;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 课程空间 Mapper。
 */
@Mapper
public interface KnowledgeWorkspaceMapper extends BaseMapper<KnowledgeWorkspace> {

    @Select("""
        SELECT id, user_id, name, description, cover_color, sort,
               create_time, update_time, is_deleted
        FROM knowledge_workspace
        WHERE user_id = #{userId} AND is_deleted = 0
        ORDER BY sort ASC, id ASC
        """)
    List<KnowledgeWorkspace> selectByUser(@Param("userId") Long userId);

    @Select("""
        SELECT workspace_id AS workspace_id, COUNT(*) AS cnt
        FROM knowledge_file
        WHERE user_id = #{userId} AND is_deleted = 0
        GROUP BY workspace_id
        """)
    List<java.util.Map<String, Object>> countFilesByWorkspace(@Param("userId") Long userId);
}
