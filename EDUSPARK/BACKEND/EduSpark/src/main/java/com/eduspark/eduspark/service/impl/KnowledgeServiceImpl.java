package com.eduspark.eduspark.service.impl;

import cn.hutool.crypto.digest.DigestUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.eduspark.eduspark.dto.common.PageResponse;
import com.eduspark.eduspark.dto.knowledge.KnowledgeChunkPreviewResponse;
import com.eduspark.eduspark.dto.knowledge.KnowledgeFilePageRequest;
import com.eduspark.eduspark.dto.knowledge.KnowledgeFilePreviewResponse;
import com.eduspark.eduspark.dto.knowledge.KnowledgeFileResponse;
import com.eduspark.eduspark.dto.knowledge.KnowledgeSearchRequest;
import com.eduspark.eduspark.dto.knowledge.KnowledgeSearchResponse;
import com.eduspark.eduspark.dto.knowledge.KnowledgeSearchTestRequest;
import com.eduspark.eduspark.dto.knowledge.KnowledgeSearchTestResponse;
import com.eduspark.eduspark.exception.BusinessException;
import com.eduspark.eduspark.exception.ErrorCode;
import com.eduspark.eduspark.mapper.knowledge.KnowledgeChunkMapper;
import com.eduspark.eduspark.mapper.knowledge.KnowledgeFileMapper;
import com.eduspark.eduspark.pojo.entity.KnowledgeChunk;
import com.eduspark.eduspark.pojo.entity.KnowledgeFile;
import com.eduspark.eduspark.service.IContextBuilderService;
import com.eduspark.eduspark.service.IEmbeddingService;
import com.eduspark.eduspark.service.IFileStorageService;
import com.eduspark.eduspark.service.IKnowledgeService;
import com.eduspark.eduspark.service.IRagProcessService;
import com.eduspark.eduspark.service.ISearchService;
import com.eduspark.eduspark.service.ITextExtractorService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 知识库服务实现
 */
@Slf4j
@Service
public class KnowledgeServiceImpl implements IKnowledgeService {

    private final KnowledgeFileMapper fileMapper;
    private final KnowledgeChunkMapper chunkMapper;
    private final IRagProcessService processService;
    private final ISearchService searchService;
    private final IEmbeddingService embeddingService;
    private final IFileStorageService fileStorageService;
    private final ITextExtractorService textExtractorService;
    private final IContextBuilderService contextBuilderService;

    @Value("${rag.storage.allowed-types:pdf,docx,doc,txt,md}")
    private String allowedTypes;

    public KnowledgeServiceImpl(
            KnowledgeFileMapper fileMapper,
            KnowledgeChunkMapper chunkMapper,
            IRagProcessService processService,
            ISearchService searchService,
            IEmbeddingService embeddingService,
            IFileStorageService fileStorageService,
            ITextExtractorService textExtractorService,
            IContextBuilderService contextBuilderService
    ) {
        this.fileMapper = fileMapper;
        this.chunkMapper = chunkMapper;
        this.processService = processService;
        this.searchService = searchService;
        this.embeddingService = embeddingService;
        this.fileStorageService = fileStorageService;
        this.textExtractorService = textExtractorService;
        this.contextBuilderService = contextBuilderService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public KnowledgeFileResponse uploadFile(
            String fileName,
            String fileType,
            byte[] fileBytes,
            Long userId,
            Long workspaceId,
            String category,
            String description
    ) {
        Set<String> allowed = new HashSet<>(Arrays.asList(allowedTypes.split(",")));
        if (!allowed.contains(fileType.toLowerCase())) {
            throw new BusinessException(ErrorCode.FILE_TYPE_NOT_SUPPORTED, "不支持的文件类型: " + fileType);
        }

        String fileHash = DigestUtil.md5Hex(fileBytes);
        KnowledgeFile existing = fileMapper.selectByFileHash(fileHash);
        if (existing != null) {
            log.info("检测到重复文件: hash={}, existingFileId={}", fileHash, existing.getId());
            return toFileResponse(existing);
        }

        String filePath = fileStorageService.upload(fileName, fileBytes, userId);

        KnowledgeFile file = KnowledgeFile.builder()
                .fileName(fileName)
                .fileType(fileType)
                .fileSize((long) fileBytes.length)
                .filePath(filePath)
                .fileHash(fileHash)
                .userId(userId)
                .workspaceId(workspaceId)
                .status(KnowledgeFile.Status.PROCESSING)
                .chunkCount(0)
                .category(category)
                .description(description)
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .isDeleted(0)
                .build();

        fileMapper.insert(file);
        log.info("文件记录创建: fileId={}, fileName={}", file.getId(), fileName);

        Long fileId = file.getId();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                processService.processFileAsync(fileId);
            }
        });

        return toFileResponse(file);
    }

    @Override
    public PageResponse<KnowledgeFileResponse> listFiles(KnowledgeFilePageRequest request) {
        Page<KnowledgeFile> page = new Page<>(request.getPage(), request.getSize());

        LambdaQueryWrapper<KnowledgeFile> wrapper = Wrappers.lambdaQuery();
        wrapper.eq(KnowledgeFile::getUserId, request.getUserId())
                .eq(request.getWorkspaceId() != null, KnowledgeFile::getWorkspaceId, request.getWorkspaceId())
                .eq(request.getStatus() != null, KnowledgeFile::getStatus, request.getStatus())
                .eq(request.getFileType() != null, KnowledgeFile::getFileType, request.getFileType())
                .like(request.getKeyword() != null, KnowledgeFile::getFileName, request.getKeyword())
                .orderByDesc(KnowledgeFile::getCreateTime);

        IPage<KnowledgeFile> result = fileMapper.selectPage(page, wrapper);

        List<KnowledgeFileResponse> records = result.getRecords().stream()
                .map(this::toFileResponse)
                .toList();

        return PageResponse.of(
                result.getTotal(),
                (int) result.getCurrent(),
                (int) result.getSize(),
                records
        );
    }

    @Override
    public KnowledgeFileResponse getFileById(Long fileId, Long userId) {
        return toFileResponse(getOwnedFile(fileId, userId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteFile(Long fileId, Long userId) {
        KnowledgeFile file = getOwnedFile(fileId, userId);
        processService.deleteFile(file.getId());
    }

    @Override
    public void reprocessFile(Long fileId, Long userId) {
        KnowledgeFile file = getOwnedFile(fileId, userId);
        log.info("重新处理文件: fileId={}, fileName={}", fileId, file.getFileName());

        chunkMapper.deleteByFileId(fileId);

        file.setStatus(KnowledgeFile.Status.PROCESSING);
        file.setChunkCount(0);
        file.setErrorMessage(null);
        file.setUpdateTime(LocalDateTime.now());
        fileMapper.updateById(file);

        processService.processFileAsync(fileId);
        log.info("文件重新处理已启动: fileId={}", fileId);
    }

    @Override
    public KnowledgeSearchResponse search(KnowledgeSearchRequest request) {
        if (request.getQuery() == null || request.getQuery().isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "检索关键词不能为空");
        }
        return searchService.hybridSearch(
                request.getQuery(),
                request.getTopK(),
                request.getUserId(),
                request.getWorkspaceId(),
                request.getVectorWeight(),
                request.getBm25Weight()
        );
    }

    @Override
    public KnowledgeSearchTestResponse testSearch(KnowledgeSearchTestRequest request) {
        long start = System.currentTimeMillis();
        KnowledgeSearchResponse searchResponse = search(
                KnowledgeSearchRequest.builder()
                        .query(request.getQuery())
                        .topK(request.getTopK())
                        .userId(request.getUserId())
                        .workspaceId(request.getWorkspaceId())
                        .vectorWeight(request.getVectorWeight())
                        .bm25Weight(request.getBm25Weight())
                        .build()
        );

        String context = contextBuilderService.buildKnowledgeContext(searchResponse, request.getMaxTokens());

        return KnowledgeSearchTestResponse.builder()
                .query(searchResponse.getQuery())
                .topK(request.getTopK())
                .maxTokens(request.getMaxTokens())
                .vectorWeight(request.getVectorWeight())
                .bm25Weight(request.getBm25Weight())
                .costMs(System.currentTimeMillis() - start)
                .total(searchResponse.getTotal())
                .usedChunkCount(countUsedChunks(searchResponse, request.getMaxTokens()))
                .contextLength(context == null ? 0 : context.length())
                .context(context)
                .results(searchResponse.getResults())
                .build();
    }

    @Override
    public KnowledgeFileResponse getFileStatus(Long fileId, Long userId) {
        return toFileResponse(getOwnedFile(fileId, userId));
    }

    @Override
    public KnowledgeFilePreviewResponse getFilePreview(Long fileId, Long userId, Integer chunkLimit) {
        KnowledgeFile file = getOwnedFile(fileId, userId);
        int safeChunkLimit = chunkLimit == null || chunkLimit < 1 ? 6 : Math.min(chunkLimit, 20);
        List<KnowledgeChunkPreviewResponse> chunks = getFileChunks(fileId, userId, safeChunkLimit);

        String previewText = null;
        Integer contentLength = null;
        boolean truncated = false;

        try (InputStream inputStream = fileStorageService.getInputStream(file.getFilePath())) {
            byte[] data = inputStream.readAllBytes();
            String extracted = textExtractorService.extractText(data, file.getFileName(), file.getFileType());
            contentLength = extracted.length();
            previewText = truncateText(extracted, 2000);
            truncated = extracted.length() > 2000;
        } catch (Exception e) {
            log.warn("文件预览提取失败: fileId={}", fileId, e);
        }

        if ((previewText == null || previewText.isBlank()) && !chunks.isEmpty()) {
            String joined = chunks.stream()
                    .map(KnowledgeChunkPreviewResponse::getText)
                    .filter(Objects::nonNull)
                    .collect(Collectors.joining("\n\n"));
            contentLength = joined.length();
            previewText = truncateText(joined, 2000);
            truncated = joined.length() > 2000;
        }

        return KnowledgeFilePreviewResponse.builder()
                .fileId(file.getId())
                .fileName(file.getFileName())
                .fileType(file.getFileType())
                .category(file.getCategory())
                .description(file.getDescription())
                .status(file.getStatus())
                .statusText(file.getStatusText())
                .errorMessage(file.getErrorMessage())
                .chunkCount(file.getChunkCount())
                .contentLength(contentLength)
                .contentPreview(previewText)
                .truncated(truncated)
                .chunks(chunks)
                .build();
    }

    @Override
    public List<KnowledgeChunkPreviewResponse> getFileChunks(Long fileId, Long userId, Integer limit) {
        KnowledgeFile file = getOwnedFile(fileId, userId);
        if (!Objects.equals(file.getStatus(), KnowledgeFile.Status.SUCCESS)) {
            return List.of();
        }

        int safeLimit = limit == null || limit < 1 ? 20 : Math.min(limit, 100);
        return chunkMapper.selectByFileIdLimit(fileId, safeLimit).stream()
                .map(this::toChunkPreview)
                .toList();
    }

    @Override
    public String getFileUrl(Long fileId) {
        KnowledgeFile file = fileMapper.selectById(fileId);
        if (file == null) {
            throw new BusinessException(ErrorCode.KNOWLEDGE_FILE_NOT_FOUND);
        }
        return fileStorageService.getUrl(file.getFilePath());
    }

    public InputStream getFileInputStream(Long fileId) {
        KnowledgeFile file = fileMapper.selectById(fileId);
        if (file == null) {
            throw new BusinessException(ErrorCode.KNOWLEDGE_FILE_NOT_FOUND);
        }
        return fileStorageService.getInputStream(file.getFilePath());
    }

    private KnowledgeFileResponse toFileResponse(KnowledgeFile file) {
        return KnowledgeFileResponse.builder()
                .fileId(file.getId())
                .fileName(file.getFileName())
                .fileType(file.getFileType())
                .fileSize(file.getFileSize())
                .fileSizeText(formatFileSize(file.getFileSize()))
                .chunkCount(file.getChunkCount())
                .status(file.getStatus())
                .statusText(file.getStatusText())
                .errorMessage(file.getErrorMessage())
                .category(file.getCategory())
                .workspaceId(file.getWorkspaceId())
                .description(file.getDescription())
                .createTime(file.getCreateTime())
                .updateTime(file.getUpdateTime())
                .build();
    }

    private KnowledgeFile getOwnedFile(Long fileId, Long userId) {
        KnowledgeFile file = fileMapper.selectById(fileId);
        if (file == null || !file.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.KNOWLEDGE_FILE_NOT_FOUND);
        }
        return file;
    }

    private KnowledgeChunkPreviewResponse toChunkPreview(KnowledgeChunk chunk) {
        return KnowledgeChunkPreviewResponse.builder()
                .chunkId(chunk.getId())
                .chunkIndex(chunk.getChunkIndex())
                .text(truncateText(chunk.getChunkText(), 500))
                .tokenCount(chunk.getTokenCount())
                .build();
    }

    private int countUsedChunks(KnowledgeSearchResponse result, int maxTokens) {
        if (result == null || result.getResults() == null) {
            return 0;
        }

        int usedTokens = 0;
        int count = 0;
        for (KnowledgeSearchResponse.KnowledgeSearchResult item : result.getResults()) {
            if (item.getText() == null) {
                continue;
            }
            int tokens = item.getText().length() / 2;
            if (usedTokens + tokens > maxTokens) {
                break;
            }
            usedTokens += tokens;
            count++;
        }
        return count;
    }

    private String truncateText(String text, int maxLength) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String normalized = text.replace("\r\n", "\n")
                .replace('\r', '\n')
                .replace("\u0000", "")
                .trim();
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, maxLength) + "\n\n...";
    }

    private String formatFileSize(Long size) {
        if (size == null) return "0 B";
        if (size < 1024) return size + " B";
        if (size < 1024 * 1024) return String.format("%.1f KB", size / 1024.0);
        if (size < 1024 * 1024 * 1024) return String.format("%.1f MB", size / (1024.0 * 1024));
        return String.format("%.1f GB", size / (1024.0 * 1024 * 1024));
    }
}
