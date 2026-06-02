package com.eduspark.eduspark.service;

import com.eduspark.eduspark.pojo.entity.ChatMessage;
import com.eduspark.eduspark.pojo.entity.ChatSession;

import java.util.List;

/**
 * 对话会话服务接口
 */
public interface IChatSessionService {

    ChatSession createSession(Long userId, String firstMessage);

    List<ChatSession> getSessionList(Long userId);

    ChatSession getSessionWithMessages(Long sessionId, Long userId);

    ChatSession getBySessionId(String sessionId);

    List<ChatMessage> getSessionMessages(Long sessionId);

    /**
     * 保存消息
     * @param sessionId 会话ID
     * @param role 角色
     * @param content 内容
     * @param intentType 意图类型
     * @param layer 层级
     * @param layerDesc 层级描述
     * @param costMs 耗时
     * @param cardType 卡片类型
     * @param cardData 卡片数据
     * @return 保存的消息
     */
    ChatMessage saveMessage(Long sessionId, String role, String content,
                            String intentType, Integer layer, String layerDesc, Integer costMs,
                            String cardType, String cardData);

    /**
     * 删除会话
     * @param sessionId 会话ID
     * @param userId 用户ID
     */
    void deleteSession(Long sessionId, Long userId);

    /**
     * 更新会话最后一条消息
     * @param sessionId 会话ID
     * @param lastMessage 最后消息摘要
     */
    void updateLastMessage(Long sessionId, String lastMessage);

    /**
     * 更新会话教学模式状态（mode/stage/teachingElements）
     * @param sessionId 会话ID
     * @param mode 工作模式
     * @param stage 当前阶段
     * @param teachingElements 教学要素JSON
     */
    void updateTeachingMode(Long sessionId, String mode, String stage, String teachingElements);

    void clearTeachingMode(Long sessionId);

    /**
     * 更新会话生成状态和结果
     * @param sessionId 会话ID
     * @param status 生成状态
     * @param result 生成结果JSON或错误信息
     */
    void updateGenerationStatus(Long sessionId, String status, String result);

    void clearGenerationResult(Long sessionId, String status);

    void updateTitle(Long sessionId, String title);
}
