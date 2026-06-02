package com.eduspark.eduspark.service;

import java.io.InputStream;

/**
 * 文件存储服务接口（支持本地存储和阿里云OSS）
 */
public interface IFileStorageService {

    /**
     * 上传文件
     *
     * @param fileName 文件名
     * @param fileBytes 文件内容
     * @param userId 用户ID（用于生成路径）
     * @return 文件访问URL
     */
    String upload(String fileName, byte[] fileBytes, Long userId);

    /**
     * 带 Content-Type 提示的上传——浏览器报告的 MIME 比根据扩展名猜更准。
     * 仅 OSS 实现会真正使用 hint；不重写时退化到 {@link #upload(String, byte[], Long)}.
     */
    default String upload(String fileName, byte[] fileBytes, Long userId, String contentTypeHint) {
        return upload(fileName, fileBytes, userId);
    }

    /**
     * 上传到指定 OSS key（绕过基于 userId/日期的默认路径）。
     * 用于系统级产物（如 PPT 缩略图）——需要稳定的 key 路径供前端访问且不依赖用户上下文。
     * 仅 OSS 实现真正生效；本地实现默认 fallback 到普通 upload（无 userId 时传 0）。
     *
     * @param ossKey     完整的 OSS key（含子目录），如 "ppt-thumbnails/123/slide-1.png"
     * @param fileBytes  文件内容
     * @param contentType 内容类型（如 "image/png"），不能为 null
     * @return 该文件的 OSS key（即传入的 ossKey）
     */
    default String uploadAtKey(String ossKey, byte[] fileBytes, String contentType) {
        return upload(ossKey, fileBytes, 0L, contentType);
    }

    /**
     * 删除文件
     *
     * @param filePath 文件路径（OSS key）
     */
    void delete(String filePath);

    /**
     * 获取文件访问URL
     *
     * @param filePath 文件路径（OSS key）
     * @return 访问URL
     */
    String getUrl(String filePath);

    /**
     * 获取文件输入流
     *
     * @param filePath 文件路径（OSS key）
     * @return 输入流
     */
    InputStream getInputStream(String filePath);

    /**
     * 检查服务是否可用
     */
    boolean isAvailable();
}
