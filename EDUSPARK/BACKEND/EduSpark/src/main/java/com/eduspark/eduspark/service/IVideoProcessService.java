package com.eduspark.eduspark.service;

import java.util.List;

/**
 * 视频处理服务接口
 * 负责视频关键帧抽取和内容分析
 */
public interface IVideoProcessService {

    /**
     * 分析视频内容，返回结构化描述
     *
     * @param videoData 视频二进制数据
     * @param fileName  文件名（用于判断格式）
     * @return 视频内容描述（包含关键帧分析结果和整体摘要）
     */
    VideoAnalysisResult analyzeVideo(byte[] videoData, String fileName);

    /**
     * 从视频中抽取关键帧
     *
     * @param videoData 视频二进制数据
     * @param fileName  文件名
     * @return 关键帧图片列表（Base64编码）
     */
    List<byte[]> extractKeyFrames(byte[] videoData, String fileName);

    /**
     * 获取视频元信息（时长、分辨率、帧率等）
     *
     * @param videoData 视频二进制数据
     * @return 视频元信息
     */
    VideoMetadata getVideoMetadata(byte[] videoData);

    /**
     * 检查 FFmpeg 是否可用
     */
    boolean isFFmpegAvailable();

    /**
     * 视频分析结果
     */
    record VideoAnalysisResult(
            String summary,              // 视频整体摘要
            List<FrameDescription> frames, // 各关键帧描述
            VideoMetadata metadata,      // 视频元信息
            int totalFramesAnalyzed      // 分析的总帧数
    ) {}

    /**
     * 单帧描述
     */
    record FrameDescription(
            int frameIndex,      // 帧序号
            double timestamp,    // 时间戳（秒）
            String description   // 帧内容描述
    ) {}

    /**
     * 视频元信息
     */
    record VideoMetadata(
            double duration,     // 时长（秒）
            int width,           // 宽度
            int height,          // 高度
            double frameRate,    // 帧率
            String codec,        // 编码格式
            long fileSize        // 文件大小（字节）
    ) {}
}
