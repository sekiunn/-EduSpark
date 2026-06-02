package com.eduspark.eduspark.dto.chat;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionDetailResponse {

    private String sessionId;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long userId;
    private String title;
    private Integer status;
    private String mode;
    private String stage;
    private String teachingElements;
    private String generationStatus;
    private String generationResult;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    private List<MessageItem> messages;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MessageItem {
        @JsonSerialize(using = ToStringSerializer.class)
        private Long id;
        private String role;
        private String content;
        private String intentType;
        private Integer layer;
        private String layerDesc;
        private Integer costMs;
        private String cardType;
        private String cardData;
        private LocalDateTime createTime;
    }
}
