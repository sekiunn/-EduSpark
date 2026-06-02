package com.eduspark.eduspark.mapper.knowledge;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.eduspark.eduspark.dto.knowledge.KnowledgeFilePageRequest;
import com.eduspark.eduspark.pojo.entity.KnowledgeFile;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 知识库文件 Mapper
 */
@Mapper
public interface KnowledgeFileMapper extends BaseMapper<KnowledgeFile> {

    /**
     * 分页查询用户文件
     */
    @Select("""
        SELECT id, file_name, file_type, file_size, file_path, file_hash,
               user_id, workspace_id, status, chunk_count, error_message, category,
               description, create_time, update_time
        FROM knowledge_file
        WHERE user_id = #{request.userId}
          AND is_deleted = 0
          AND (#{request.workspaceId} IS NULL OR workspace_id = #{request.workspaceId})
          AND (#{request.status} IS NULL OR status = #{request.status})
          AND (#{request.fileType} IS NULL OR file_type = #{request.fileType})
          AND (#{request.keyword} IS NULL OR file_name LIKE '%' || #{request.keyword} || '%')
        ORDER BY create_time DESC
        """)
    IPage<KnowledgeFile> selectPageByRequest(
            Page<KnowledgeFile> page,
            @Param("request") KnowledgeFilePageRequest request
    );

    /**
     * 根据文件哈希查询（去重）
     */
    @Select("SELECT * FROM knowledge_file WHERE file_hash = #{fileHash} AND is_deleted = 0 LIMIT 1")
    KnowledgeFile selectByFileHash(@Param("fileHash") String fileHash);

    /**
     * 查询处理失败的文件
     */
    @Select("""
        SELECT * FROM knowledge_file
        WHERE status = 2 AND is_deleted = 0
        ORDER BY create_time DESC
        LIMIT #{limit}
        """)
    java.util.List<KnowledgeFile> selectFailedFiles(@Param("limit") int limit);
}
