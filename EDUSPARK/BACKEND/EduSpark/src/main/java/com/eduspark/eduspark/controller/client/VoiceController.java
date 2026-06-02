package com.eduspark.eduspark.controller.client;

import com.eduspark.eduspark.dto.common.Result;
import com.eduspark.eduspark.service.IVoiceService;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * 语音识别控制器（客户端）
 */
@Slf4j
@Validated
@RestController
@RequestMapping("/v1/voice")
public class VoiceController {

    private final IVoiceService voiceService;

    public VoiceController(IVoiceService voiceService) {
        this.voiceService = voiceService;
    }

    /**
     * 语音转文字（Whisper）
     * 接收前端录音的音频文件，返回转写文本
     *
     * @param file     音频文件（支持 webm, mp3, wav, m4a）
     * @param language 语言代码（可选，默认 zh）
     * @return 转写结果
     */
    @PostMapping("/transcribe")
    public Result<VoiceTranscribeResponse> transcribe(
            @RequestParam("file") @NotNull(message = "音频文件不能为空") MultipartFile file,
            @RequestParam(value = "language", required = false, defaultValue = "zh") String language
    ) {
        log.info("收到语音识别请求: fileName={}, size={}, language={}",
                file.getOriginalFilename(), file.getSize(), language);

        // 验证文件类型
        String fileName = file.getOriginalFilename();
        if (!isValidAudioFile(fileName)) {
            return Result.fail("不支持的音频格式，仅支持 webm, mp3, wav, m4a");
        }

        // 验证文件大小（最大 10MB）
        if (file.getSize() > 10 * 1024 * 1024) {
            return Result.fail("音频文件过大，最大支持 10MB");
        }

        try {
            byte[] audioData = file.getBytes();
            String text = voiceService.transcribe(audioData, fileName, language);

            VoiceTranscribeResponse response = new VoiceTranscribeResponse();
            response.setText(text);
            response.setLanguage(language);
            response.setDuration(null); // 前端自行记录时长，后端无法从文件大小准确计算

            log.info("语音识别成功: textLength={}", text.length());
            return Result.success(response);

        } catch (Exception e) {
            log.error("语音识别失败: {}", e.getMessage());
            return Result.fail("语音识别失败: " + e.getMessage());
        }
    }

    /**
     * 语音服务健康检查
     */
    @GetMapping("/health")
    public Result<Boolean> checkHealth() {
        boolean available = voiceService.isAvailable();
        return Result.<Boolean>builder()
                .code(available ? 200 : 503)
                .message(available ? "语音服务正常" : "语音服务不可用")
                .data(available)
                .build();
    }

    /**
     * 检查是否为支持的音频格式
     */
    private boolean isValidAudioFile(String fileName) {
        if (fileName == null) return false;
        String lower = fileName.toLowerCase();
        return lower.endsWith(".webm") ||
                lower.endsWith(".mp3") ||
                lower.endsWith(".wav") ||
                lower.endsWith(".m4a") ||
                lower.endsWith(".ogg") ||
                lower.endsWith(".aac");
    }

    /**
     * 语音转文字响应
     */
    public static class VoiceTranscribeResponse {
        private String text;
        private String language;
        private Integer duration; // 估算时长（秒）

        public String getText() {
            return text;
        }

        public void setText(String text) {
            this.text = text;
        }

        public String getLanguage() {
            return language;
        }

        public void setLanguage(String language) {
            this.language = language;
        }

        public Integer getDuration() {
            return duration;
        }

        public void setDuration(Integer duration) {
            this.duration = duration;
        }
    }
}
