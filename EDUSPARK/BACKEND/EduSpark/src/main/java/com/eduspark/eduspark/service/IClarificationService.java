package com.eduspark.eduspark.service;

import com.eduspark.eduspark.dto.chat.ClarificationResult;
import com.eduspark.eduspark.pojo.entity.ChatSession;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * Teaching-mode clarification service.
 */
public interface IClarificationService {

    default ClarificationResult startClarification(ChatSession session, String userMessage) {
        return startClarification(session, userMessage, null, null);
    }

    default ClarificationResult startClarification(ChatSession session, String userMessage, MultipartFile[] attachments) {
        return startClarification(session, userMessage, attachments, null);
    }

    ClarificationResult startClarification(ChatSession session, String userMessage, MultipartFile[] attachments, String templateId);

    default ClarificationResult continueClarification(ChatSession session, String userAnswer) {
        return continueClarification(session, userAnswer, null);
    }

    ClarificationResult continueClarification(ChatSession session, String userAnswer, String templateId);

    /**
     * PPT 模式快速直达：合并模板 + 附件 + 主题文本后直接进入生成阶段，跳过 clarifying/confirming。
     * 调用方应保证 {@code templateId} 非空，且用户已提供主题文本或附件。
     */
    default ClarificationResult fastPathForPpt(ChatSession session,
                                               String userMessage,
                                               MultipartFile[] attachments,
                                               String templateId) {
        return startClarification(session, userMessage, attachments, templateId);
    }

    ClarificationResult confirmAndGenerate(ChatSession session);

    void doAsyncGenerate(Long sessionId, String mode, Map<String, Object> blueprint);

    String getBlueprintJson(ChatSession session);
}
