package com.eduspark.eduspark.service.impl;

import com.eduspark.eduspark.service.IMultimodalParseService;
import com.eduspark.eduspark.service.IVideoProcessService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.*;

/**
 * 视频处理服务实现
 * 使用 FFmpeg 抽取关键帧 + 视觉模型分析
 */
@Slf4j
@Service
public class VideoProcessServiceImpl implements IVideoProcessService {

    @Value("${multimodal.video.enabled:true}")
    private boolean videoEnabled;

    @Value("${multimodal.video.ffmpeg-path:ffmpeg}")
    private String ffmpegPath;

    @Value("${multimodal.video.frame-interval:5}")
    private int frameInterval;

    @Value("${multimodal.video.max-frames:10}")
    private int maxFrames;

    @Value("${multimodal.video.max-duration:300}")
    private int maxDuration;

    @Value("${multimodal.video.temp-dir:./temp/video}")
    private String tempDir;

    private final IMultimodalParseService multimodalParseService;
    private final ObjectMapper objectMapper;

    private final ExecutorService executor = Executors.newFixedThreadPool(4);

    public VideoProcessServiceImpl(IMultimodalParseService multimodalParseService) {
        this.multimodalParseService = multimodalParseService;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public VideoAnalysisResult analyzeVideo(byte[] videoData, String fileName) {
        if (!videoEnabled) {
            throw new RuntimeException("视频处理功能未启用");
        }
        if (!isFFmpegAvailable()) {
            throw new RuntimeException("FFmpeg 不可用，请确保已安装 FFmpeg 并添加到系统 PATH");
        }

        log.info("开始分析视频: {}, 大小: {} bytes", fileName, videoData.length);
        long startTime = System.currentTimeMillis();

        Path tempVideoPath = null;
        Path frameOutputDir = null;

        try {
            Path tempDirPath = Paths.get(tempDir);
            if (!Files.exists(tempDirPath)) {
                Files.createDirectories(tempDirPath);
            }

            String baseName = UUID.randomUUID().toString();
            tempVideoPath = tempDirPath.resolve(baseName + getExtension(fileName));
            Files.write(tempVideoPath, videoData);
            log.debug("临时视频文件: {}", tempVideoPath);

            VideoMetadata metadata = getVideoMetadata(videoData);
            log.info("视频元信息: 时长={}s, 分辨率={}x{}, 帧率={}", 
                    metadata.duration(), metadata.width(), metadata.height(), metadata.frameRate());

            frameOutputDir = tempDirPath.resolve(baseName + "_frames");
            Files.createDirectories(frameOutputDir);

            List<byte[]> frames = extractKeyFramesInternal(tempVideoPath, frameOutputDir, metadata);
            log.info("抽取关键帧 {} 张", frames.size());

            List<FrameDescription> frameDescriptions = analyzeFramesParallel(frames, metadata);

            String summary = generateVideoSummary(frameDescriptions, metadata);

            long elapsed = System.currentTimeMillis() - startTime;
            log.info("视频分析完成, 耗时: {}ms", elapsed);

            return new VideoAnalysisResult(summary, frameDescriptions, metadata, frames.size());

        } catch (Exception e) {
            log.error("视频分析失败: {}", e.getMessage(), e);
            throw new RuntimeException("视频分析失败: " + e.getMessage(), e);
        } finally {
            cleanupTempFiles(tempVideoPath, frameOutputDir);
        }
    }

    @Override
    public List<byte[]> extractKeyFrames(byte[] videoData, String fileName) {
        if (!videoEnabled || !isFFmpegAvailable()) {
            return Collections.emptyList();
        }

        Path tempVideoPath = null;
        Path frameOutputDir = null;

        try {
            Path tempDirPath = Paths.get(tempDir);
            if (!Files.exists(tempDirPath)) {
                Files.createDirectories(tempDirPath);
            }

            String baseName = UUID.randomUUID().toString();
            tempVideoPath = tempDirPath.resolve(baseName + getExtension(fileName));
            Files.write(tempVideoPath, videoData);

            frameOutputDir = tempDirPath.resolve(baseName + "_frames");
            Files.createDirectories(frameOutputDir);

            VideoMetadata metadata = getVideoMetadata(videoData);
            return extractKeyFramesInternal(tempVideoPath, frameOutputDir, metadata);

        } catch (Exception e) {
            log.error("关键帧抽取失败: {}", e.getMessage(), e);
            return Collections.emptyList();
        } finally {
            cleanupTempFiles(tempVideoPath, frameOutputDir);
        }
    }

    @Override
    public VideoMetadata getVideoMetadata(byte[] videoData) {
        Path tempVideoPath = null;

        try {
            Path tempDirPath = Paths.get(tempDir);
            if (!Files.exists(tempDirPath)) {
                Files.createDirectories(tempDirPath);
            }

            tempVideoPath = tempDirPath.resolve(UUID.randomUUID().toString() + ".mp4");
            Files.write(tempVideoPath, videoData);

            return getVideoMetadataFromPath(tempVideoPath);

        } catch (Exception e) {
            log.warn("获取视频元信息失败，使用默认值: {}", e.getMessage());
            return new VideoMetadata(0, 0, 0, 0, "unknown", videoData.length);
        } finally {
            if (tempVideoPath != null) {
                try {
                    Files.deleteIfExists(tempVideoPath);
                } catch (IOException ignored) {}
            }
        }
    }

    private VideoMetadata getVideoMetadataFromPath(Path videoPath) {
        try {
            List<String> cmd = List.of(
                    ffmpegPath.replace("ffmpeg", "ffprobe"),
                    "-v", "quiet",
                    "-print_format", "json",
                    "-show_format",
                    "-show_streams",
                    videoPath.toString()
            );

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            String output = new String(process.getInputStream().readAllBytes());
            process.waitFor(10, TimeUnit.SECONDS);

            return parseMetadataFromJson(output, videoPath);

        } catch (Exception e) {
            log.error("FFprobe 执行失败: {}", e.getMessage());
            return new VideoMetadata(0, 0, 0, 0, "unknown", videoPath.toFile().length());
        }
    }

    private VideoMetadata parseMetadataFromJson(String json, Path videoPath) {
        try {
            Map<String, Object> data = objectMapper.readValue(json, Map.class);

            Map<String, Object> format = (Map<String, Object>) data.get("format");
            double duration = 0;
            if (format != null && format.get("duration") != null) {
                duration = Double.parseDouble(format.get("duration").toString());
            }

            int width = 0, height = 0;
            double frameRate = 0;
            String codec = "unknown";

            List<Map<String, Object>> streams = (List<Map<String, Object>>) data.get("streams");
            if (streams != null) {
                for (Map<String, Object> stream : streams) {
                    if ("video".equals(stream.get("codec_type"))) {
                        width = ((Number) stream.getOrDefault("width", 0)).intValue();
                        height = ((Number) stream.getOrDefault("height", 0)).intValue();
                        codec = (String) stream.getOrDefault("codec_name", "unknown");

                        String rFrameRate = (String) stream.get("r_frame_rate");
                        if (rFrameRate != null && rFrameRate.contains("/")) {
                            String[] parts = rFrameRate.split("/");
                            double num = Double.parseDouble(parts[0]);
                            double den = Double.parseDouble(parts[1]);
                            frameRate = den > 0 ? num / den : 0;
                        }
                        break;
                    }
                }
            }

            return new VideoMetadata(duration, width, height, frameRate, codec, videoPath.toFile().length());

        } catch (Exception e) {
            log.warn("解析视频元信息 JSON 失败: {}", e.getMessage());
            return new VideoMetadata(0, 0, 0, 0, "unknown", videoPath.toFile().length());
        }
    }

    private List<byte[]> extractKeyFramesInternal(Path videoPath, Path outputDir, VideoMetadata metadata) 
            throws IOException, InterruptedException {
        
        double effectiveDuration = Math.min(metadata.duration(), maxDuration);
        int effectiveInterval = frameInterval;
        
        if (effectiveDuration > 0) {
            int estimatedFrames = (int) (effectiveDuration / effectiveInterval);
            if (estimatedFrames > maxFrames) {
                effectiveInterval = (int) (effectiveDuration / maxFrames);
            }
        }

        List<String> cmd = List.of(
                ffmpegPath,
                "-i", videoPath.toString(),
                "-vf", String.format("fps=1/%d,scale=640:-1", effectiveInterval),
                "-frames:v", String.valueOf(maxFrames),
                "-q:v", "2",
                outputDir.resolve("frame_%03d.jpg").toString()
        );

        log.debug("FFmpeg 命令: {}", String.join(" ", cmd));

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        Process process = pb.start();

        String output = new String(process.getInputStream().readAllBytes());
        boolean finished = process.waitFor(60, TimeUnit.SECONDS);

        if (!finished) {
            process.destroyForcibly();
            throw new RuntimeException("FFmpeg 执行超时");
        }

        if (process.exitValue() != 0) {
            log.warn("FFmpeg 退出码非零: {}, 输出: {}", process.exitValue(), output);
        }

        List<byte[]> frames = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(outputDir, "frame_*.jpg")) {
            for (Path framePath : stream) {
                frames.add(Files.readAllBytes(framePath));
            }
        }

        frames.sort((a, b) -> {
            return a.length - b.length;
        });

        return frames;
    }

    private List<FrameDescription> analyzeFramesParallel(List<byte[]> frames, VideoMetadata metadata) {
        if (frames.isEmpty()) {
            return Collections.emptyList();
        }

        double timeStep = metadata.duration() > 0 
                ? Math.min(metadata.duration(), maxDuration) / frames.size() 
                : frameInterval;

        List<CompletableFuture<FrameDescription>> futures = new ArrayList<>();

        for (int i = 0; i < frames.size(); i++) {
            final int index = i;
            final double timestamp = index * timeStep;
            final byte[] frameData = frames.get(i);

            futures.add(CompletableFuture.supplyAsync(() -> {
                try {
                    String prompt = String.format(
                            "这是视频的第 %d 帧（时间 %.1f 秒）。请描述这一帧的内容，" +
                            "包括场景、人物、文字、教学相关元素等。如果有重要的知识点或演示内容，请详细说明。",
                            index + 1, timestamp
                    );
                    String description = multimodalParseService.describeImage(frameData, prompt);
                    return new FrameDescription(index, timestamp, description);
                } catch (Exception e) {
                    log.warn("帧 {} 分析失败: {}", index, e.getMessage());
                    return new FrameDescription(index, timestamp, "[分析失败: " + e.getMessage() + "]");
                }
            }, executor));
        }

        List<FrameDescription> results = new ArrayList<>();
        for (CompletableFuture<FrameDescription> future : futures) {
            try {
                results.add(future.get(60, TimeUnit.SECONDS));
            } catch (Exception e) {
                log.warn("获取帧分析结果失败: {}", e.getMessage());
            }
        }

        results.sort(Comparator.comparingDouble(FrameDescription::timestamp));
        return results;
    }

    private String generateVideoSummary(List<FrameDescription> frames, VideoMetadata metadata) {
        if (frames.isEmpty()) {
            return "视频内容无法解析";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("【视频信息】\n");
        sb.append(String.format("时长: %.1f 秒, 分辨率: %dx%d\n\n", 
                metadata.duration(), metadata.width(), metadata.height()));
        sb.append("【关键帧内容】\n");

        for (FrameDescription frame : frames) {
            sb.append(String.format("\n[%.1fs] %s\n", frame.timestamp(), frame.description()));
        }

        return sb.toString();
    }

    @Override
    public boolean isFFmpegAvailable() {
        try {
            ProcessBuilder pb = new ProcessBuilder(ffmpegPath, "-version");
            pb.redirectErrorStream(true);
            Process process = pb.start();
            boolean finished = process.waitFor(5, TimeUnit.SECONDS);
            return finished && process.exitValue() == 0;
        } catch (Exception e) {
            log.warn("FFmpeg 不可用: {}", e.getMessage());
            return false;
        }
    }

    private String getExtension(String fileName) {
        if (fileName == null) return ".mp4";
        int dotIndex = fileName.lastIndexOf('.');
        return dotIndex > 0 ? fileName.substring(dotIndex) : ".mp4";
    }

    private void cleanupTempFiles(Path... paths) {
        for (Path path : paths) {
            if (path == null) continue;
            try {
                if (Files.isDirectory(path)) {
                    try (DirectoryStream<Path> stream = Files.newDirectoryStream(path)) {
                        for (Path file : stream) {
                            Files.deleteIfExists(file);
                        }
                    }
                }
                Files.deleteIfExists(path);
            } catch (IOException e) {
                log.warn("清理临时文件失败: {}", path);
            }
        }
    }
}
