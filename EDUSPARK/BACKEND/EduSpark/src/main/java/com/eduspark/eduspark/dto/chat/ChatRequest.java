package com.eduspark.eduspark.dto.chat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 对话请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatRequest {

    private String sessionId;

    private String mode;

    @NotBlank(message = "消息内容不能为空")
    private String message;

    @Builder.Default
    private Boolean webSearchEnabled = false;

    private List<ChatMessage> history;

    private List<MultipartFile> attachments;

    private Long referencedFileId;

    private String templateId;

    /**
     * 教学模式动作（来自卡片按钮点击）
     * confirm - 用户点击确认按钮
     * supplement - 用户点击补充细节按钮
     */
    private String action;

    /**
     * 聊天消息
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChatMessage {
        private String role;    // system / user / assistant
        private String content;
    }
}
