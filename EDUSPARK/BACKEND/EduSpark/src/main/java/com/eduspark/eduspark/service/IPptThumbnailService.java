package com.eduspark.eduspark.service;

import java.nio.file.Path;
import java.util.List;

/**
 * 把生成好的 pptx 用 LibreOffice 转 PDF，再用 PDFBox 逐页 render 成 PNG 上传 OSS。
 * 调用方拿到每页对应的公开 URL 列表后，写回 PptSlidePlan.backgroundImageUrl，
 * 通过 SSE 推送给前端，缩略图条与主区域即可显示真实页面预览。
 */
public interface IPptThumbnailService {

    /**
     * 同步渲染所有页的缩略图（建议放到 pptThumbnailExecutor 线程池调用）。
     *
     * @param pptxPath   已生成的 pptx 文件绝对路径
     * @param documentId PPT 工作区文档 ID，用于 OSS key 命名
     * @return 每页的公开 URL（顺序 = 页号），任一环节失败返回空列表（不抛异常）
     */
    List<String> renderSlideThumbnails(Path pptxPath, Long documentId);

    /**
     * 是否启用（受 ppt.thumbnail.enabled 开关 + LibreOffice 可用性影响）。
     */
    boolean isEnabled();
}
