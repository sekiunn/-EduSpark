package com.eduspark.eduspark.service.impl;

import com.eduspark.eduspark.service.IFileStorageService;
import com.eduspark.eduspark.service.IPptThumbnailService;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class PptThumbnailServiceImpl implements IPptThumbnailService {

    @Value("${ppt.thumbnail.enabled:true}")
    private boolean enabled;

    @Value("${ppt.thumbnail.libreoffice-path:soffice}")
    private String libreofficePath;

    @Value("${ppt.thumbnail.dpi:144}")
    private int dpi;

    @Value("${ppt.thumbnail.timeout-seconds:90}")
    private long timeoutSeconds;

    @Value("${ppt.thumbnail.temp-dir:./temp/ppt-thumbnails}")
    private String tempDir;

    @Value("${ppt.thumbnail.oss-key-prefix:ppt-thumbnails}")
    private String ossKeyPrefix;

    private final IFileStorageService fileStorageService;

    public PptThumbnailServiceImpl(IFileStorageService fileStorageService) {
        this.fileStorageService = fileStorageService;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public List<String> renderSlideThumbnails(Path pptxPath, Long documentId) {
        if (!enabled) {
            return List.of();
        }
        if (pptxPath == null || !Files.exists(pptxPath)) {
            log.warn("Thumbnail skipped: pptx 不存在 path={}", pptxPath);
            return List.of();
        }
        if (documentId == null) {
            log.warn("Thumbnail skipped: documentId 为空");
            return List.of();
        }

        Path workDir = null;
        try {
            workDir = prepareWorkDir(documentId);
            Path pdfPath = convertPptxToPdf(pptxPath, workDir);
            if (pdfPath == null) {
                return List.of();
            }
            return renderPdfPagesToOss(pdfPath, documentId);
        } catch (Exception e) {
            log.error("Render thumbnails failed: documentId={}, pptx={}", documentId, pptxPath, e);
            return List.of();
        } finally {
            if (workDir != null) {
                deleteQuietly(workDir);
            }
        }
    }

    private Path prepareWorkDir(Long documentId) throws IOException {
        Path base = Paths.get(tempDir).toAbsolutePath();
        Files.createDirectories(base);
        Path work = base.resolve(documentId + "-" + UUID.randomUUID().toString().substring(0, 8));
        Files.createDirectories(work);
        return work;
    }

    /**
     * 调 LibreOffice 把 pptx 转 PDF。LibreOffice 的 png 过滤器只导第 1 页，
     * 所以这里走 PDF 中转，再由 PDFBox 逐页 render。
     */
    private Path convertPptxToPdf(Path pptxPath, Path workDir) {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    libreofficePath,
                    "--headless",
                    "--norestore",
                    "--nofirststartwizard",
                    "--convert-to", "pdf",
                    "--outdir", workDir.toString(),
                    pptxPath.toString()
            );
            pb.redirectErrorStream(true);
            Process process = pb.start();
            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                log.warn("LibreOffice 转 PDF 超时（>{}s），pptx={}", timeoutSeconds, pptxPath);
                return null;
            }
            int exitCode = process.exitValue();
            if (exitCode != 0) {
                String output = new String(process.getInputStream().readAllBytes());
                log.warn("LibreOffice 转 PDF 失败: exitCode={}, output={}", exitCode, output);
                return null;
            }

            String baseName = pptxPath.getFileName().toString();
            int dot = baseName.lastIndexOf('.');
            String pdfName = (dot > 0 ? baseName.substring(0, dot) : baseName) + ".pdf";
            Path pdfPath = workDir.resolve(pdfName);
            if (!Files.exists(pdfPath)) {
                log.warn("LibreOffice 转 PDF 后找不到产物: expected={}", pdfPath);
                return null;
            }
            return pdfPath;
        } catch (Exception e) {
            log.warn("调 LibreOffice 失败: path={}", libreofficePath, e);
            return null;
        }
    }

    /**
     * PDFBox 逐页 render 成 PNG，并行上传 OSS。返回每页的公开 URL（顺序 = 页号）。
     * 某一页失败时该位置返回 null，调用方按需过滤。
     */
    private List<String> renderPdfPagesToOss(Path pdfPath, Long documentId) {
        List<String> urls = new ArrayList<>();
        try (PDDocument document = Loader.loadPDF(pdfPath.toFile())) {
            PDFRenderer renderer = new PDFRenderer(document);
            int pages = document.getNumberOfPages();
            for (int i = 0; i < pages; i++) {
                String url = renderAndUploadSinglePage(renderer, i, documentId);
                urls.add(url);
            }
            log.info("Thumbnails rendered: documentId={}, totalPages={}, succeeded={}",
                    documentId, pages, urls.stream().filter(java.util.Objects::nonNull).count());
        } catch (IOException e) {
            log.error("PDFBox 加载 PDF 失败: pdfPath={}", pdfPath, e);
        }
        return urls;
    }

    private String renderAndUploadSinglePage(PDFRenderer renderer, int pageIndex, Long documentId) {
        try {
            BufferedImage image = renderer.renderImageWithDPI(pageIndex, dpi, ImageType.RGB);
            ByteArrayOutputStream baos = new ByteArrayOutputStream(256 * 1024);
            ImageIO.write(image, "png", baos);
            byte[] png = baos.toByteArray();

            int slideNo = pageIndex + 1;
            String ossKey = String.format("%s/%d/slide-%d.png", ossKeyPrefix, documentId, slideNo);
            String savedKey = fileStorageService.uploadAtKey(ossKey, png, "image/png");
            return fileStorageService.getUrl(savedKey);
        } catch (Exception e) {
            log.warn("Render or upload page failed: documentId={}, pageIndex={}",
                    documentId, pageIndex, e);
            return null;
        }
    }

    private void deleteQuietly(Path dir) {
        try {
            if (!Files.exists(dir)) return;
            Files.walk(dir)
                    .sorted((a, b) -> b.getNameCount() - a.getNameCount())
                    .map(Path::toFile)
                    .forEach(File::delete);
        } catch (Exception ignored) {
            // 清理失败不影响主流程
        }
    }
}
