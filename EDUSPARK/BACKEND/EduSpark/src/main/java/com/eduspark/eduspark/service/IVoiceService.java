package com.eduspark.eduspark.service;

/**
 * 语音识别服务接口（Whisper）
 */
public interface IVoiceService {

    /**
     * 语音转文字
     * @param audioData 音频数据（字节数组）
     * @param fileName  原始文件名（用于确定格式）
     * @return 转写文本
     */
    String transcribe(byte[] audioData, String fileName);

    /**
     * 语音转文字（带语言参数）
     * @param audioData 音频数据
     * @param fileName  原始文件名
     * @param language  语言代码（如 "zh", "en"）
     * @return 转写文本
     */
    String transcribe(byte[] audioData, String fileName, String language);

    /**
     * 检查语音服务是否可用
     * @return true=可用
     */
    boolean isAvailable();
}
