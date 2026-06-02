package com.eduspark.eduspark.event;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.Map;

/**
 * 内容生成请求事件
 */
@Data
@AllArgsConstructor
public class GenerationRequestedEvent {
    private Long sessionId;
    private String mode;
    private Map<String, Object> blueprint;
}