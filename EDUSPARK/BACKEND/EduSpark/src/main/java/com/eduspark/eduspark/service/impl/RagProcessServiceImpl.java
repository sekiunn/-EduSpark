package com.eduspark.eduspark.service.impl;

import cn.hutool.crypto.digest.DigestUtil;
import com.eduspark.eduspark.exception.BusinessException;
import com.eduspark.eduspark.exception.ErrorCode;
import com.eduspark.eduspark.mapper.knowledge.KnowledgeChunkMapper;
import com.eduspark.eduspark.mapper.knowledge.KnowledgeFileMapper;
import com.eduspark.eduspark.pojo.entity.KnowledgeChunk;
import com.eduspark.eduspark.pojo.entity.KnowledgeFile;
import com.eduspark.eduspark.service.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * RAG 异步文件处理服务实现
 * 
 * <p>本服务负责将用户上传的知识库文件转换为可检索的向量数据，是RAG（检索增强生成）的核心处理流程。</p>
 * 
 * <h2>处理流程概览</h2>
 * <pre>
 * ┌─────────────┐    ┌─────────────┐    ┌─────────────┐    ┌─────────────┐    ┌─────────────┐
 * │  文件上传   │ → │  文本提取   │ → │  智能分块   │ → │  向量化     │ → │  存储入库   │
 * │  (PDF/Word) │    │  (纯文本)   │    │  (语义块)   │    │  (768维)    │    │  (pgvector) │
 * └─────────────┘    └─────────────┘    └─────────────┘    └─────────────┘    └─────────────┘
 * </pre>
 * 
 * <h2>各步骤详解</h2>
 * <ol>
 *   <li><b>文件读取</b>：从OSS/本地存储读取原始文件字节流</li>
 *   <li><b>文本提取</b>：
 *       <ul>
 *         <li>PDF → Apache PDFBox 解析文字层（扫描件需OCR，暂不支持）</li>
 *         <li>DOCX → Apache POI 解析Word XML结构</li>
 *         <li>TXT/MD → 直接读取UTF-8文本</li>
 *       </ul>
 *   </li>
 *   <li><b>智能分块</b>：
 *       <ul>
 *         <li>按语义边界（段落、章节）切割，保持内容完整性</li>
 *         <li>每块 300-500 tokens，相邻块有 50 tokens 重叠</li>
 *         <li>代码块、表格等特殊内容单独处理</li>
 *       </ul>
 *   </li>
 *   <li><b>向量化</b>：
 *       <ul>
 *         <li>调用 Ollama Embedding API（模型：nomic-embed-text / bge-m3）</li>
 *         <li>将文本转换为 768/1024 维浮点向量</li>
 *         <li>语义相近的文本，向量在空间中距离相近</li>
 *       </ul>
 *   </li>
 *   <li><b>存储入库</b>：
 *       <ul>
 *         <li>使用 PostgreSQL pgvector 扩展存储向量</li>
 *         <li>支持向量相似度检索（余弦距离/欧氏距离）</li>
 *         <li>通过 chunk_hash 去重，避免重复存储</li>
 *       </ul>
 *   </li>
 * </ol>
 * 
 * <h2>异步处理机制</h2>
 * <p>使用 Spring @Async 注解，在独立线程池中执行，避免阻塞HTTP请求线程。
 * 前端通过轮询 getFileDetail API 获取处理状态。</p>
 * 
 * <h2>状态流转</h2>
 * <pre>
 * PROCESSING(0) ──成功──→ SUCCESS(1) ──→ 可用于检索
 *      │
 *      └──失败──→ FAILED(2) ──→ 显示错误信息，支持重试
 * </pre>
 * 
 * @see ITextExtractorService 文本提取服务
 * @see ISmartChunkService 智能分块服务
 * @see IEmbeddingService 向量化服务
 * @see IFileStorageService 文件存储服务
 */
@Slf4j
@Service
public class RagProcessServiceImpl implements IRagProcessService {

    private final KnowledgeFileMapper fileMapper;
    private final KnowledgeChunkMapper chunkMapper;
    private final ITextExtractorService textExtractor;
    private final ISmartChunkService chunkService;
    private final IEmbeddingService embeddingService;
    private final JdbcTemplate jdbcTemplate;
    private final IFileStorageService fileStorageService;

    public RagProcessServiceImpl(
            KnowledgeFileMapper fileMapper,
            KnowledgeChunkMapper chunkMapper,
            ITextExtractorService textExtractor,
            ISmartChunkService chunkService,
            IEmbeddingService embeddingService,
            JdbcTemplate jdbcTemplate,
            IFileStorageService fileStorageService
    ) {
        this.fileMapper = fileMapper;
        this.chunkMapper = chunkMapper;
        this.textExtractor = textExtractor;
        this.chunkService = chunkService;
        this.embeddingService = embeddingService;
        this.jdbcTemplate = jdbcTemplate;
        this.fileStorageService = fileStorageService;
    }

    @Override
    @Async("ragTaskExecutor")
    @Transactional(rollbackFor = Exception.class)
    public void processFileAsync(Long fileId) {
        long startTime = System.currentTimeMillis();
        KnowledgeFile file = null;

        try {
            file = fileMapper.selectById(fileId);
            if (file == null) {
                log.error("文件不存在: fileId={}", fileId);
                return;
            }

            log.info("开始处理文件: fileId={}, fileName={}", fileId, file.getFileName());

            // ═══════════════════════════════════════════════════════════════
            // 步骤1: 从OSS/本地存储读取原始文件字节
            // ═══════════════════════════════════════════════════════════════
            byte[] fileBytes;
            try (InputStream inputStream = fileStorageService.getInputStream(file.getFilePath())) {
                fileBytes = inputStream.readAllBytes();
            }
            log.debug("文件读取完成: fileId={}, size={}KB", fileId, fileBytes.length / 1024);

            // ═══════════════════════════════════════════════════════════════
            // 步骤2: 文本提取 - 根据文件类型调用不同的提取器
            // ═══════════════════════════════════════════════════════════════
            // PDF  → Apache PDFBox 解析文字层（扫描件无法提取）
            // DOCX → Apache POI 解析Word XML结构
            // TXT  → 直接读取UTF-8文本
            // MD   → 直接读取Markdown源码
            // 输出: 纯文本字符串，可能包含几万到几十万字符
            MultipartFile multipartFile = new MockMultipartFile(
                    file.getFileName(), file.getFileName(), file.getFileType(), fileBytes);
            String text = textExtractor.extractText(multipartFile);
            log.debug("文本提取完成: fileId={}, textLength={}", fileId, text.length());

            // ═══════════════════════════════════════════════════════════════
            // 步骤3: 智能分块 - 将长文本切割成语义完整的小块
            // ═══════════════════════════════════════════════════════════════
            // 分块策略：
            // - 优先按段落/章节边界切割，保持语义完整性
            // - 每块目标 300-500 tokens（约 500-800 汉字）
            // - 相邻块之间有 50 tokens 重叠，避免边界信息丢失
            // - 代码块、表格等特殊内容单独成块
            // 输出: 15~50 个分块（取决于原文长度）
            List<ISmartChunkService.ChunkResult> chunkResults = chunkService.chunkText(text);
            if (chunkResults.isEmpty()) {
                throw new BusinessException(ErrorCode.KNOWLEDGE_CHUNK_ERROR, "分块结果为空");
            }
            log.debug("智能分块完成: fileId={}, chunkCount={}", fileId, chunkResults.size());

            // ═══════════════════════════════════════════════════════════════
            // 步骤4: 批量向量化 + 存储准备
            // ═══════════════════════════════════════════════════════════════
            // 向量化是将文本转换为高维向量（如 768 维浮点数组）
            // 语义相近的文本，向量在空间中距离也相近
            // 
            // 批量处理：每 20 个分块调用一次 Ollama Embedding API
            // 模型: nomic-embed-text 或 bge-m3
            // 
            // 存储内容：
            // - chunk_text: 原文（用于检索后展示）
            // - embedding: 向量（用于相似度计算）
            // - token_count: token数（用于上下文长度控制）
            // - chunk_hash: MD5哈希（用于去重）
            List<KnowledgeChunk> entities = new ArrayList<>();
            int batchSize = 20;

            for (int i = 0; i < chunkResults.size(); i += batchSize) {
                int end = Math.min(i + batchSize, chunkResults.size());
                List<String> batchTexts = chunkResults.subList(i, end).stream()
                        .map(ISmartChunkService.ChunkResult::text)
                        .toList();

                // 调用 Ollama Embedding API，返回向量列表
                List<float[]> embeddings = embeddingService.batchEmbed(batchTexts);

                // 构建数据库实体
                for (int j = 0; j < batchTexts.size(); j++) {
                    ISmartChunkService.ChunkResult cr = chunkResults.get(i + j);
                    KnowledgeChunk entity = KnowledgeChunk.builder()
                            .fileId(fileId)
                            .chunkIndex(i / batchSize * batchSize + j)
                            .chunkText(cr.text())
                            .chunkHash(md5(cr.text()))
                            .tokenCount(cr.tokenCount())
                            .embedding(embeddings.get(j))
                            .createTime(LocalDateTime.now())
                            .build();
                    entities.add(entity);
                }

                log.info("处理进度: fileId={}, {}/{}", fileId, end, chunkResults.size());
            }

            // ═══════════════════════════════════════════════════════════════
            // 步骤5: 批量插入 PostgreSQL pgvector
            // ═══════════════════════════════════════════════════════════════
            // 使用 PostgreSQL 的 pgvector 扩展存储向量
            // SQL: INSERT INTO knowledge_chunk (..., embedding) VALUES (..., ?::vector)
            // ON CONFLICT (chunk_hash) DO NOTHING - 相同内容不重复插入
            batchInsertChunks(entities);

            // ═══════════════════════════════════════════════════════════════
            // 步骤6: 更新文件状态为成功
            // ═══════════════════════════════════════════════════════════════
            // 前端轮询 getFileDetail API 检测到 status=1 后显示"已就绪"
            file.setStatus(KnowledgeFile.Status.SUCCESS);
            file.setChunkCount(entities.size());
            file.setUpdateTime(LocalDateTime.now());
            fileMapper.updateById(file);

            long cost = System.currentTimeMillis() - startTime;
            log.info("文件处理完成: fileId={}, chunks={}, cost={}ms", fileId, entities.size(), cost);

        } catch (Exception e) {
            log.error("文件处理失败: fileId={}", fileId, e);
            if (file != null) {
                // 更新状态为失败，记录错误信息
                file.setStatus(KnowledgeFile.Status.FAILED);
                file.setErrorMessage(truncate(e.getMessage(), 500));
                file.setUpdateTime(LocalDateTime.now());
                fileMapper.updateById(file);
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteFile(Long fileId) {
        KnowledgeFile file = fileMapper.selectById(fileId);
        if (file == null) {
            throw new BusinessException(ErrorCode.KNOWLEDGE_FILE_NOT_FOUND);
        }

        // 删除OSS文件（忽略失败）
        try {
            fileStorageService.delete(file.getFilePath());
        } catch (Exception e) {
            log.warn("OSS文件删除失败: {}", file.getFilePath(), e);
        }

        // 删除分块
        chunkMapper.deleteByFileId(fileId);

        // 逻辑删除文件
        file.setIsDeleted(1);
        file.setUpdateTime(LocalDateTime.now());
        fileMapper.updateById(file);

        log.info("文件删除完成: fileId={}", fileId);
    }

    private void batchInsertChunks(List<KnowledgeChunk> entities) {
        String sql = """
            INSERT INTO knowledge_chunk
                (file_id, chunk_index, chunk_text, chunk_hash,
                 token_count, embedding, create_time)
            VALUES (?, ?, ?, ?, ?, ?::vector, ?)
            ON CONFLICT (chunk_hash) DO NOTHING
            """;

        jdbcTemplate.batchUpdate(sql, entities, 50, (ps, entity) -> {
            ps.setObject(1, entity.getFileId());
            ps.setInt(2, entity.getChunkIndex());
            ps.setString(3, entity.getChunkText());
            ps.setString(4, entity.getChunkHash());
            ps.setInt(5, entity.getTokenCount());
            ps.setObject(6, arrayToPostgresVector(entity.getEmbedding()));
            ps.setObject(7, entity.getCreateTime());
        });
    }

    private String arrayToPostgresVector(float[] arr) {
        if (arr == null || arr.length == 0) return "[]";
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < arr.length; i++) {
            sb.append(arr[i]);
            if (i < arr.length - 1) sb.append(",");
        }
        return sb.append("]").toString();
    }

    private String md5(String text) {
        return text == null ? java.util.UUID.randomUUID().toString() : DigestUtil.md5Hex(text);
    }

    private String truncate(String str, int maxLen) {
        return str == null ? "" : str.length() <= maxLen ? str : str.substring(0, maxLen);
    }
}
