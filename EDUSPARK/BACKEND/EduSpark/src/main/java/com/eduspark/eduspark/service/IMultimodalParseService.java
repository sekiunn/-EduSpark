package com.eduspark.eduspark.service;

/**
 * 多模态内容解析服务接口
 * 支持 PPT、图片 等非纯文本格式的内容提取
 */
public interface IMultimodalParseService {

    /**
     * 从 PPT/PPTX 文件中提取文本内容
     * @param fileData 文件字节数据
     * @param fileName 文件名（用于确定格式）
     * @return 提取的文本内容
     */
    String extractTextFromPpt(byte[] fileData, String fileName);

    /**
     * 使用视觉模型解析图片内容（Ollama Qwen-VL / LLaVA）
     * @param imageData 图片字节数据
     * @param prompt    提示词（可选）
     * @return 图片描述/内容
     */
    String describeImage(byte[] imageData, String prompt);

    /**
     * 从图片中提取文字（OCR）
     * @param imageData 图片字节数据
     * @return 识别的文字
     */
    String extractTextFromImage(byte[] imageData);

    /**
     * 检查多模态服务是否可用
     */
    boolean isAvailable();
}
