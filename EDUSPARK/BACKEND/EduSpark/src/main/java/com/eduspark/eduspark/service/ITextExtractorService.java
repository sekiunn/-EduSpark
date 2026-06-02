package com.eduspark.eduspark.service;

import org.springframework.web.multipart.MultipartFile;

/**
 * 文本提取服务接口
 */
public interface ITextExtractorService {

    /**
     * 从文件中提取纯文本
     * 支持: PDF, Word, TXT, MD, PPT, PPTX, 图片
     */
    String extractText(MultipartFile file);

    /**
     * 从字节数组中提取文本（用于知识库处理流程）
     * @param data      文件字节数据
     * @param fileName  文件名
     * @param fileType  文件类型（小写）
     */
    String extractText(byte[] data, String fileName, String fileType);
}
