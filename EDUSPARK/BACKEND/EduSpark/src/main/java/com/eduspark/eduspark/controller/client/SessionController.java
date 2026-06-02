package com.eduspark.eduspark.controller.client;

import com.eduspark.eduspark.dto.chat.GenerationStatusResponse;
import com.eduspark.eduspark.dto.chat.SessionDetailResponse;
import com.eduspark.eduspark.dto.chat.SessionListResponse;
import com.eduspark.eduspark.dto.common.Result;
import com.eduspark.eduspark.pojo.entity.ChatMessage;
import com.eduspark.eduspark.pojo.entity.ChatSession;
import com.eduspark.eduspark.service.IChatSessionService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Validated
@RestController
@RequestMapping("/v1/chat/sessions")
public class SessionController {

    private final IChatSessionService chatSessionService;

    public SessionController(IChatSessionService chatSessionService) {
        this.chatSessionService = chatSessionService;
    }

    @PostMapping
    public Result<SessionDetailResponse> createSession(
            @RequestParam("firstMessage") String firstMessage,
            HttpServletRequest httpRequest
    ) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        log.info("创建会话: userId={}, firstMessage={}", userId, firstMessage);

        ChatSession session = chatSessionService.createSession(userId, firstMessage);

        return Result.success(toSessionDetailResponse(session));
    }

    @GetMapping
    public Result<List<SessionListResponse>> getSessionList(HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        log.debug("获取会话列表: userId={}", userId);

        List<ChatSession> sessions = chatSessionService.getSessionList(userId);

        List<SessionListResponse> result = sessions.stream()
                .map(this::toSessionListResponse)
                .collect(Collectors.toList());

        return Result.success(result);
    }

    @GetMapping("/{sessionId}")
    public Result<SessionDetailResponse> getSessionDetail(
            @PathVariable String sessionId,
            HttpServletRequest httpRequest
    ) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        log.info("获取会话详情: sessionId={}, userId={}", sessionId, userId);

        ChatSession session = chatSessionService.getBySessionId(sessionId);
        if (session == null || !session.getUserId().equals(userId)) {
            return Result.notFound("会话不存在");
        }

        List<ChatMessage> messages = chatSessionService.getSessionMessages(session.getId());

        return Result.success(toSessionDetailResponseWithMessages(session, messages));
    }

    @DeleteMapping("/{sessionId}")
    public Result<Void> deleteSession(
            @PathVariable String sessionId,
            HttpServletRequest httpRequest
    ) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        log.info("删除会话: sessionId={}, userId={}", sessionId, userId);

        ChatSession session = chatSessionService.getBySessionId(sessionId);
        if (session == null || !session.getUserId().equals(userId)) {
            return Result.notFound("会话不存在");
        }

        chatSessionService.deleteSession(session.getId(), userId);
        return Result.successMessage("删除成功");
    }

    @PutMapping("/{sessionId}/title")
    public Result<Void> updateTitle(
            @PathVariable String sessionId,
            @RequestBody java.util.Map<String, String> body,
            HttpServletRequest httpRequest
    ) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        ChatSession session = chatSessionService.getBySessionId(sessionId);
        if (session == null || !session.getUserId().equals(userId)) {
            return Result.notFound("会话不存在");
        }
        String title = body.get("title");
        if (title == null || title.isBlank()) {
            return Result.fail("标题不能为空");
        }
        chatSessionService.updateTitle(session.getId(), title);
        return Result.successMessage("更新成功");
    }

    @PostMapping("/{sessionId}/exit-mode")
    public Result<Void> exitTeachingMode(
            @PathVariable String sessionId,
            HttpServletRequest httpRequest
    ) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        ChatSession session = chatSessionService.getBySessionId(sessionId);
        if (session == null || !session.getUserId().equals(userId)) {
            return Result.notFound("会话不存在");
        }
        chatSessionService.clearTeachingMode(session.getId());
        chatSessionService.clearGenerationResult(session.getId(), ChatSession.GenerationStatus.PENDING);
        return Result.successMessage("已退出教学模式");
    }

    @GetMapping("/{sessionId}/generation-status")
    public Result<GenerationStatusResponse> getGenerationStatus(
            @PathVariable String sessionId,
            HttpServletRequest httpRequest
    ) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        ChatSession session = chatSessionService.getBySessionId(sessionId);
        if (session == null || !session.getUserId().equals(userId)) {
            return Result.notFound("会话不存在");
        }
        return Result.success(GenerationStatusResponse.builder()
                .sessionId(session.getSessionId())
                .generationStatus(session.getGenerationStatus())
                .generationResult(session.getGenerationResult())
                .build());
    }

    private SessionListResponse toSessionListResponse(ChatSession session) {
        return SessionListResponse.builder()
                .sessionId(session.getSessionId())
                .title(session.getTitle())
                .lastMessage(session.getLastMessage())
                .messageCount(session.getMessageCount())
                .status(session.getStatus())
                .mode(session.getMode())
                .stage(session.getStage())
                .createTime(session.getCreateTime())
                .updateTime(session.getUpdateTime())
                .build();
    }

    private SessionDetailResponse toSessionDetailResponse(ChatSession session) {
        return SessionDetailResponse.builder()
                .sessionId(session.getSessionId())
                .userId(session.getUserId())
                .title(session.getTitle())
                .status(session.getStatus())
                .mode(session.getMode())
                .stage(session.getStage())
                .teachingElements(session.getTeachingElements())
                .generationStatus(session.getGenerationStatus())
                .generationResult(session.getGenerationResult())
                .createTime(session.getCreateTime())
                .updateTime(session.getUpdateTime())
                .build();
    }

    private SessionDetailResponse toSessionDetailResponseWithMessages(ChatSession session, List<ChatMessage> messages) {
        List<SessionDetailResponse.MessageItem> messageItems = messages.stream()
                .map(m -> SessionDetailResponse.MessageItem.builder()
                        .id(m.getId())
                        .role(m.getRole())
                        .content(m.getContent())
                        .intentType(m.getIntentType())
                        .layer(m.getLayer())
                        .layerDesc(m.getLayerDesc())
                        .costMs(m.getCostMs())
                        .cardType(m.getCardType())
                        .cardData(m.getCardData())
                        .createTime(m.getCreateTime())
                        .build())
                .collect(Collectors.toList());

        return SessionDetailResponse.builder()
                .sessionId(session.getSessionId())
                .userId(session.getUserId())
                .title(session.getTitle())
                .status(session.getStatus())
                .mode(session.getMode())
                .stage(session.getStage())
                .teachingElements(session.getTeachingElements())
                .generationStatus(session.getGenerationStatus())
                .generationResult(session.getGenerationResult())
                .createTime(session.getCreateTime())
                .updateTime(session.getUpdateTime())
                .messages(messageItems)
                .build();
    }
}
