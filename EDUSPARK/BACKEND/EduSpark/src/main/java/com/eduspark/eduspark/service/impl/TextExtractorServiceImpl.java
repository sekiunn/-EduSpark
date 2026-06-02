package com.eduspark.eduspark.service.impl;

import com.eduspark.eduspark.exception.BusinessException;
import com.eduspark.eduspark.exception.ErrorCode;
import com.eduspark.eduspark.service.IMultimodalParseService;
import com.eduspark.eduspark.service.ITextExtractorService;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 文本提取服务实现
 * 支持: PDF, Word, TXT, MD, PPT, PPTX, 图片
 */
@Slf4j
@Service
public class TextExtractorServiceImpl implements ITextExtractorService {

    private final IMultimodalParseService multimodalParseService;

    public TextExtractorServiceImpl(IMultimodalParseService multimodalParseService) {
        this.multimodalParseService = multimodalParseService;
    }

    private static final Set<String> TEXT_TYPES = Set.of("pdf", "docx", "doc", "txt", "md");
    private static final Set<String> PPT_TYPES = Set.of("pptx", "ppt");
    private static final Set<String> IMAGE_TYPES = Set.of("png", "jpg", "jpeg", "gif", "bmp", "webp");
    private static final Pattern CONTROL_CHARS = Pattern.compile("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]");
    private static final Pattern EXTRA_WHITESPACE = Pattern.compile("[ \\t]+");
    private static final Pattern EXTRA_NEWLINES = Pattern.compile("\\n{3,}");

    @Override
    public String extractText(MultipartFile file) {
        String filename = file.getOriginalFilename();
        if (filename == null || !filename.contains(".")) {
            throw new BusinessException(ErrorCode.FILE_TYPE_NOT_SUPPORTED, "无法确定文件类型");
        }

        String fileType = filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
        try {
            return extractText(file.getBytes(), filename, fileType);
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.FILE_READ_ERROR, "文件读取失败: " + e.getMessage(), e);
        }
    }

    @Override
    public String extractText(byte[] data, String fileName, String fileType) {
        log.info("提取文本: fileName={}, fileType={}, size={}",
                fileName, fileType, data.length);

        if (TEXT_TYPES.contains(fileType)) {
            return extractTextType(data, fileType);
        } else if (PPT_TYPES.contains(fileType)) {
            return extractPptType(data, fileName);
        } else if (IMAGE_TYPES.contains(fileType)) {
            return extractImageType(data);
        } else {
            throw new BusinessException(ErrorCode.FILE_TYPE_NOT_SUPPORTED,
                    "不支持的文件类型: " + fileType);
        }
    }

    private String extractTextType(byte[] data, String fileType) {
        String text = switch (fileType) {
            case "pdf" -> extractPdf(data);
            case "docx" -> extractDocx(data);
            case "doc" -> extractDoc(data);
            case "txt", "md" -> extractTxt(data);
            default -> throw new BusinessException(ErrorCode.FILE_TYPE_NOT_SUPPORTED);
        };
        return cleanText(text);
    }

    private String extractPptType(byte[] data, String fileName) {
        try {
            return multimodalParseService.extractTextFromPpt(data, fileName);
        } catch (Exception e) {
            log.error("PPT 解析失败，尝试备用方案: {}", e.getMessage());
            // PPT 解析失败时返回提示
            return "【PPT文件】" + fileName + " - 内容解析失败，请转换为 PDF 后重试。错误: " + e.getMessage();
        }
    }

    private String extractImageType(byte[] data) {
        try {
            return multimodalParseService.extractTextFromImage(data);
        } catch (Exception e) {
            log.error("图片文字识别失败: {}", e.getMessage());
            return "【图片文件】内容解析失败，请确保 Ollama 视觉模型（llava）已安装并运行。错误: " + e.getMessage();
        }
    }

    private String extractPdf(byte[] data) {
        try (PDDocument doc = Loader.loadPDF(data)) {
            PDFTextStripper stripper = new PDFTextStripper();
            int total = doc.getNumberOfPages();
            StringBuilder sb = new StringBuilder();
            for (int i = 1; i <= total; i++) {
                stripper.setStartPage(i);
                stripper.setEndPage(i);
                String page = stripper.getText(doc).trim();
                if (!page.isEmpty()) {
                    sb.append("【第").append(i).append("/").append(total).append("页】\n")
                      .append(page).append("\n\n");
                }
            }
            return sb.toString();
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.PDF_PARSING_ERROR, "PDF解析失败: " + e.getMessage(), e);
        }
    }

    private String extractDocx(byte[] data) {
        try (java.io.ByteArrayInputStream bais = new java.io.ByteArrayInputStream(data);
             XWPFDocument doc = new XWPFDocument(bais)) {
            return new XWPFWordExtractor(doc).getText();
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.WORD_PARSING_ERROR, "Word解析失败: " + e.getMessage(), e);
        }
    }

    private String extractDoc(byte[] data) {
        try (java.io.ByteArrayInputStream bais = new java.io.ByteArrayInputStream(data);
             HWPFDocument doc = new HWPFDocument(bais)) {
            org.apache.poi.hwpf.usermodel.Range range = doc.getRange();
            String text = range.text();
            if (text == null || text.isBlank()) {
                WordExtractor extractor = new WordExtractor(doc);
                text = extractor.getText();
                extractor.close();
            }
            return text;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.WORD_PARSING_ERROR, "Word解析失败: " + e.getMessage(), e);
        }
    }

    private String extractTxt(byte[] data) {
        try {
            String text = new String(data, StandardCharsets.UTF_8);
            if (text.contains("�")) {
                text = new String(data, java.nio.charset.Charset.forName("GBK"));
            }
            return text;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.TEXT_EXTRACTION_ERROR, "文本文件读取失败", e);
        }
    }

    private String cleanText(String text) {
        if (text == null || text.isBlank()) return "";
        String cleaned = text.transform(CONTROL_CHARS::matcher).replaceAll("")
                .transform(EXTRA_WHITESPACE::matcher).replaceAll(" ")
                .trim()
                .transform(EXTRA_NEWLINES::matcher).replaceAll("\n\n");
        if (cleaned.isBlank()) {
            throw new BusinessException(ErrorCode.FILE_EMPTY, "文件内容为空");
        }
        return cleaned;
    }
}

