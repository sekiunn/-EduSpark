package com.eduspark.eduspark.service;

/**
 * RAG 异步文件处理服务接口
 */
public interface IRagProcessService {

    /**
     * 异步处理上传文件
     */
    void processFileAsync(Long fileId);

    /**
     * 删除文件及其所有分块
     */
    void deleteFile(Long fileId);
}
