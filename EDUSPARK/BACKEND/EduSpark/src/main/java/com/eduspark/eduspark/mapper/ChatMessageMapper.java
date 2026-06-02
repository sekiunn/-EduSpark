package com.eduspark.eduspark.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.eduspark.eduspark.pojo.entity.ChatMessage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 对话消息Mapper
 */
@Mapper
public interface ChatMessageMapper extends BaseMapper<ChatMessage> {

    /**
     * 查询会话的所有消息（按时间正序）
     */
    @Select("SELECT * FROM chat_message WHERE session_id = #{sessionId} ORDER BY create_time ASC")
    List<ChatMessage> selectBySessionId(@Param("sessionId") Long sessionId);

    /**
     * 删除会话的所有消息
     */
    @Select("DELETE FROM chat_message WHERE session_id = #{sessionId}")
    void deleteBySessionId(@Param("sessionId") Long sessionId);
}
