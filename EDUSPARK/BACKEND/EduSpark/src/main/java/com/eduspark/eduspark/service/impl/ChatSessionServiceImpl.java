package com.eduspark.eduspark.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.eduspark.eduspark.mapper.ChatMessageMapper;
import com.eduspark.eduspark.mapper.ChatSessionMapper;
import com.eduspark.eduspark.pojo.entity.ChatMessage;
import com.eduspark.eduspark.pojo.entity.ChatSession;
import com.eduspark.eduspark.service.IChatSessionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class ChatSessionServiceImpl implements IChatSessionService {

    private static final String ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ChatSessionMapper sessionMapper;
    private final ChatMessageMapper messageMapper;
    private final JdbcTemplate jdbcTemplate;

    public ChatSessionServiceImpl(ChatSessionMapper sessionMapper, ChatMessageMapper messageMapper, JdbcTemplate jdbcTemplate) {
        this.sessionMapper = sessionMapper;
        this.messageMapper = messageMapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    private static String generateSessionId() {
        StringBuilder sb = new StringBuilder("sess_");
        for (int i = 0; i < 16; i++) {
            sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }

    @Override
    @Transactional
    public ChatSession createSession(Long userId, String firstMessage) {
        String title = firstMessage != null && firstMessage.length() > 50
                ? firstMessage.substring(0, 50) + "..."
                : firstMessage;

        String sessionId = generateSessionId();

        ChatSession session = ChatSession.builder()
                .sessionId(sessionId)
                .userId(userId)
                .title(title)
                .status(ChatSession.Status.ACTIVE)
                .lastMessage(firstMessage != null && firstMessage.length() > 100
                        ? firstMessage.substring(0, 100) + "..."
                        : firstMessage)
                .messageCount(0)
                .mode(null)
                .stage(ChatSession.Stage.IDLE)
                .teachingElements(null)
                .build();

        sessionMapper.insert(session);
        log.info("创建会话: sessionId={}, dbId={}, userId={}, title={}", sessionId, session.getId(), userId, title);
        return session;
    }

    @Override
    public List<ChatSession> getSessionList(Long userId) {
        return sessionMapper.selectByUserId(userId);
    }

    @Override
    public ChatSession getBySessionId(String sessionId) {
        return sessionMapper.selectOne(
                new LambdaQueryWrapper<ChatSession>()
                        .eq(ChatSession::getSessionId, sessionId)
                        .eq(ChatSession::getIsDeleted, 0)
        );
    }

    @Override
    public ChatSession getSessionWithMessages(Long sessionId, Long userId) {
        log.info("getSessionWithMessages 查询: sessionId={}, userId={}", sessionId, userId);
        ChatSession session = sessionMapper.selectOne(
                new LambdaQueryWrapper<ChatSession>()
                        .eq(ChatSession::getId, sessionId)
                        .eq(ChatSession::getUserId, userId)
                        .eq(ChatSession::getIsDeleted, 0)
        );
        if (session == null) {
            log.warn("会话查询为空: sessionId={}, userId={}", sessionId, userId);
            ChatSession sessionWithoutUserId = sessionMapper.selectById(sessionId);
            if (sessionWithoutUserId != null) {
                log.warn("会话存在但userId不匹配: sessionId={}, sessionUserId={}, queryUserId={}",
                        sessionId, sessionWithoutUserId.getUserId(), userId);
            } else {
                log.warn("会话完全不存在: sessionId={}", sessionId);
            }
        } else {
            log.info("会话查询成功: sessionId={}, userId={}, mode={}", sessionId, userId, session.getMode());
        }
        return session;
    }

    @Override
    public List<ChatMessage> getSessionMessages(Long sessionId) {
        return messageMapper.selectBySessionId(sessionId);
    }

    @Override
    @Transactional
    public ChatMessage saveMessage(Long sessionId, String role, String content,
                                  String intentType, Integer layer, String layerDesc, Integer costMs,
                                  String cardType, String cardData) {
        ChatMessage message = ChatMessage.builder()
                .sessionId(sessionId)
                .role(role)
                .content(content)
                .intentType(intentType)
                .layer(layer)
                .layerDesc(layerDesc)
                .costMs(costMs)
                .cardType(cardType)
                .cardData(cardData)
                .build();

        messageMapper.insert(message);

        // 更新会话计数和最后消息（避免触发 jsonb 类型问题，直接用SQL）
        Integer currentCount = jdbcTemplate.queryForObject(
                "SELECT message_count FROM chat_session WHERE id = ?", Integer.class, sessionId);
        int newCount = (currentCount != null ? currentCount : 0) + 1;
        String truncatedLast = content != null && content.length() > 200
                ? content.substring(0, 200) + "..."
                : (content != null ? content : "");
        jdbcTemplate.update(
                "UPDATE chat_session SET message_count = ?, last_message = ? WHERE id = ?",
                newCount, truncatedLast, sessionId
        );

        log.debug("保存消息: sessionId={}, role={}, messageId={}, cardType={}", sessionId, role, message.getId(), cardType);
        return message;
    }

    @Override
    @Transactional
    public void deleteSession(Long sessionId, Long userId) {
        // 软删除会话（消息随会话级联删除，这里只删session）
        sessionMapper.delete(
                new LambdaQueryWrapper<ChatSession>()
                        .eq(ChatSession::getId, sessionId)
                        .eq(ChatSession::getUserId, userId)
        );
        log.info("删除会话: sessionId={}, userId={}", sessionId, userId);
    }

    @Override
    @Transactional
    public void updateLastMessage(Long sessionId, String lastMessage) {
        String truncated = lastMessage != null && lastMessage.length() > 200
                ? lastMessage.substring(0, 200) + "..."
                : lastMessage;
        jdbcTemplate.update(
                "UPDATE chat_session SET last_message = ?, update_time = NOW() WHERE id = ? AND is_deleted = 0",
                truncated, sessionId
        );
    }

    @Override
    @Transactional
    public void updateTeachingMode(Long sessionId, String mode, String stage, String teachingElements) {
        // 使用原生SQL更新，确保 jsonb 类型正确转换
        StringBuilder sql = new StringBuilder("UPDATE chat_session SET ");
        List<Object> params = new ArrayList<>();
        List<String> setClauses = new ArrayList<>();

        if (mode != null) {
            setClauses.add("mode = ?");
            params.add(mode);
        }
        if (stage != null) {
            setClauses.add("stage = ?");
            params.add(stage);
        }
        if (teachingElements != null) {
            // PostgreSQL jsonb 类型需要 ::jsonb 转换
            setClauses.add("teaching_elements = ?::jsonb");
            params.add(teachingElements);
        }

        if (setClauses.isEmpty()) {
            return;
        }

        sql.append(String.join(", ", setClauses));
        sql.append(" WHERE id = ?");
        params.add(sessionId);

        jdbcTemplate.update(sql.toString(), params.toArray());
        log.info("更新教学模式: sessionId={}, mode={}, stage={}", sessionId, mode, stage);
    }

    @Override
    @Transactional
    public void clearTeachingMode(Long sessionId) {
        jdbcTemplate.update(
                "UPDATE chat_session SET mode = NULL, stage = ?, teaching_elements = NULL, update_time = NOW() WHERE id = ? AND is_deleted = 0",
                ChatSession.Stage.IDLE,
                sessionId
        );
        log.info("娓呯┖鏁欏妯″紡: sessionId={}", sessionId);
    }

    @Override
    @Transactional
    public void updateGenerationStatus(Long sessionId, String status, String result) {
        if (result != null) {
            jdbcTemplate.update(
                    "UPDATE chat_session SET generation_status = ?, generation_result = ?, update_time = NOW() WHERE id = ? AND is_deleted = 0",
                    status, result, sessionId
            );
        } else {
            jdbcTemplate.update(
                    "UPDATE chat_session SET generation_status = ?, update_time = NOW() WHERE id = ? AND is_deleted = 0",
                    status, sessionId
            );
        }
        log.info("更新生成状态: sessionId={}, status={}", sessionId, status);
    }

    @Override
    @Transactional
    public void clearGenerationResult(Long sessionId, String status) {
        jdbcTemplate.update(
                "UPDATE chat_session SET generation_status = ?, generation_result = NULL, update_time = NOW() WHERE id = ? AND is_deleted = 0",
                status,
                sessionId
        );
        log.info("娓呯┖鐢熸垚缁撴灉: sessionId={}, status={}", sessionId, status);
    }

    @Override
    @Transactional
    public void updateTitle(Long sessionId, String title) {
        jdbcTemplate.update(
                "UPDATE chat_session SET title = ?, update_time = NOW() WHERE id = ? AND is_deleted = 0",
                title, sessionId
        );
        log.info("更新会话标题: sessionId={}, title={}", sessionId, title);
    }
}
