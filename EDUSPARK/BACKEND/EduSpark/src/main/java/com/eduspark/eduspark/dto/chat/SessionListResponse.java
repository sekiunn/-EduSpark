package com.eduspark.eduspark.dto.chat;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionListResponse {

    private String sessionId;
    private String title;
    private String lastMessage;
    private Integer messageCount;
    private Integer status;
    private String mode;
    private String stage;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
