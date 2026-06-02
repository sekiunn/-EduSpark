package com.eduspark.eduspark.service.impl;

import com.aliyun.oss.OSS;
import com.aliyun.oss.common.utils.BinaryUtil;
import com.aliyun.oss.model.GetObjectRequest;
import com.aliyun.oss.model.OSSObject;
import com.aliyun.oss.model.ObjectMetadata;
import com.eduspark.eduspark.config.OssConfig;
import com.eduspark.eduspark.exception.BusinessException;
import com.eduspark.eduspark.exception.ErrorCode;
import com.eduspark.eduspark.service.IFileStorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * 阿里云OSS文件存储服务实现
 */
@Slf4j
@Service
public class OssFileStorageServiceImpl implements IFileStorageService {

    private final OSS ossClient;
    private final OssConfig ossConfig;

    public OssFileStorageServiceImpl(OSS ossClient, OssConfig ossConfig) {
        this.ossClient = ossClient;
        this.ossConfig = ossConfig;
    }

    @Override
    public String upload(String fileName, byte[] fileBytes, Long userId) {
        return upload(fileName, fileBytes, userId, null);
    }

    @Override
    public String upload(String fileName, byte[] fileBytes, Long userId, String contentTypeHint) {
        try {
            // 生成OSS key: knowledge/userId/日期/UUID_filename
            String datePath = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
            String uuid = UUID.randomUUID().toString().replace("-", "");
            String extension = getExtension(fileName);
            String ossKey = String.format("knowledge/%d/%s/%s.%s",
                    userId, datePath, uuid, extension);

            // 优先用浏览器报告的 contentType（比扩展名映射准），fallback 到按扩展名猜
            String finalContentType;
            if (contentTypeHint != null && !contentTypeHint.isBlank()
                    && !"application/octet-stream".equalsIgnoreCase(contentTypeHint)) {
                finalContentType = contentTypeHint;
            } else {
                finalContentType = getContentType(extension);
            }

            log.info("开始上传文件到OSS: fileName={}, ossKey={}, size={}, contentType={}",
                    fileName, ossKey, fileBytes.length, finalContentType);

            // 上传文件
            ByteArrayInputStream inputStream = new ByteArrayInputStream(fileBytes);
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(fileBytes.length);
            metadata.setContentType(finalContentType);
            // 显式设 inline，避免某些 OSS bucket 默认 attachment 强制下载
            metadata.setContentDisposition("inline");

            ossClient.putObject(ossConfig.getBucketName(), ossKey, inputStream, metadata);

            log.info("文件上传成功: ossKey={}", ossKey);

            return ossKey;

        } catch (Exception e) {
            log.error("OSS上传失败: fileName={}", fileName, e);
            throw new BusinessException(ErrorCode.FILE_UPLOAD_ERROR,
                    "文件上传失败: " + e.getMessage(), e);
        }
    }

    @Override
    public String uploadAtKey(String ossKey, byte[] fileBytes, String contentType) {
        if (ossKey == null || ossKey.isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "OSS key 不能为空");
        }
        try {
            ByteArrayInputStream inputStream = new ByteArrayInputStream(fileBytes);
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(fileBytes.length);
            metadata.setContentType(contentType != null ? contentType : "application/octet-stream");
            metadata.setContentDisposition("inline");

            ossClient.putObject(ossConfig.getBucketName(), ossKey, inputStream, metadata);
            log.info("uploadAtKey 成功: ossKey={}, size={}", ossKey, fileBytes.length);
            return ossKey;
        } catch (Exception e) {
            log.error("uploadAtKey 失败: ossKey={}", ossKey, e);
            throw new BusinessException(ErrorCode.FILE_UPLOAD_ERROR,
                    "文件上传失败: " + e.getMessage(), e);
        }
    }

    @Override
    public void delete(String filePath) {
        try {
            log.info("删除OSS文件: {}", filePath);
            ossClient.deleteObject(ossConfig.getBucketName(), filePath);
            log.info("文件删除成功: {}", filePath);
        } catch (Exception e) {
            log.error("OSS删除失败: filePath={}", filePath, e);
            throw new BusinessException(ErrorCode.FILE_DELETE_ERROR,
                    "文件删除失败: " + e.getMessage(), e);
        }
    }

    @Override
    public String getUrl(String filePath) {
        // 直接返回完整URL，不使用签名（bucket已设置公开读）
        return ossConfig.getBaseUrl() + "/" + filePath;
    }

    @Override
    public boolean isAvailable() {
        try {
            return ossClient.doesBucketExist(ossConfig.getBucketName());
        } catch (Exception e) {
            log.warn("OSS服务不可用: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 下载文件
     */
    public byte[] download(String filePath) {
        try {
            OSSObject object = ossClient.getObject(
                    new GetObjectRequest(ossConfig.getBucketName(), filePath));

            try (InputStream inputStream = object.getObjectContent()) {
                return inputStream.readAllBytes();
            }
        } catch (IOException e) {
            log.error("OSS下载失败: filePath={}", filePath, e);
            throw new BusinessException(ErrorCode.FILE_DOWNLOAD_ERROR,
                    "文件下载失败: " + e.getMessage(), e);
        }
    }

    /**
     * 获取文件输入流
     */
    public InputStream getInputStream(String filePath) {
        try {
            OSSObject object = ossClient.getObject(
                    new GetObjectRequest(ossConfig.getBucketName(), filePath));
            return object.getObjectContent();
        } catch (Exception e) {
            log.error("获取文件流失败: filePath={}", filePath, e);
            throw new BusinessException(ErrorCode.FILE_DOWNLOAD_ERROR,
                    "获取文件流失败: " + e.getMessage(), e);
        }
    }

    // ==================== 工具方法 ====================

    private String getExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
    }

    private String getContentType(String extension) {
        return switch (extension) {
            case "pdf" -> "application/pdf";
            case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "doc" -> "application/msword";
            case "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation";
            case "ppt" -> "application/vnd.ms-powerpoint";
            case "txt" -> "text/plain";
            case "md" -> "text/markdown";
            // 图片格式：扩展常见浏览器/手机/截图来源的格式，避免落到 octet-stream 触发下载
            case "png" -> "image/png";
            case "jpg", "jpeg", "jfif" -> "image/jpeg";
            case "gif" -> "image/gif";
            case "webp" -> "image/webp";
            case "bmp" -> "image/bmp";
            case "svg" -> "image/svg+xml";
            case "ico" -> "image/x-icon";
            case "tif", "tiff" -> "image/tiff";
            case "heic", "heif" -> "image/heic";
            case "avif" -> "image/avif";
            case "mp4" -> "video/mp4";
            case "mp3" -> "audio/mpeg";
            case "wav" -> "audio/wav";
            default -> "application/octet-stream";
        };
    }
}
