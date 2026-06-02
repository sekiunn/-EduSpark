package com.eduspark.eduspark.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.eduspark.eduspark.pojo.entity.ChatSession;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 对话会话Mapper
 */
@Mapper
public interface ChatSessionMapper extends BaseMapper<ChatSession> {

    /**
     * 查询用户的所有会话（按更新时间倒序）
     */
    @Select("SELECT * FROM chat_session WHERE user_id = #{userId} AND is_deleted = 0 ORDER BY update_time DESC")
    List<ChatSession> selectByUserId(@Param("userId") Long userId);

    /**
     * 查询用户活跃的会话
     */
    @Select("SELECT * FROM chat_session WHERE user_id = #{userId} AND is_deleted = 0 AND status = 1 ORDER BY update_time DESC")
    List<ChatSession> selectActiveByUserId(@Param("userId") Long userId);
}
