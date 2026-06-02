package com.eduspark.eduspark.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.eduspark.eduspark.dto.knowledge.KnowledgeWorkspaceRequest;
import com.eduspark.eduspark.dto.knowledge.KnowledgeWorkspaceResponse;
import com.eduspark.eduspark.exception.BusinessException;
import com.eduspark.eduspark.exception.ErrorCode;
import com.eduspark.eduspark.mapper.knowledge.KnowledgeFileMapper;
import com.eduspark.eduspark.mapper.knowledge.KnowledgeWorkspaceMapper;
import com.eduspark.eduspark.pojo.entity.KnowledgeFile;
import com.eduspark.eduspark.pojo.entity.KnowledgeWorkspace;
import com.eduspark.eduspark.service.IKnowledgeWorkspaceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 课程空间服务实现：每位老师独享 namespace，按课程隔离上传与检索。
 */
@Slf4j
@Service
public class KnowledgeWorkspaceServiceImpl implements IKnowledgeWorkspaceService {

    private final KnowledgeWorkspaceMapper workspaceMapper;
    private final KnowledgeFileMapper fileMapper;

    public KnowledgeWorkspaceServiceImpl(
            KnowledgeWorkspaceMapper workspaceMapper,
            KnowledgeFileMapper fileMapper
    ) {
        this.workspaceMapper = workspaceMapper;
        this.fileMapper = fileMapper;
    }

    @Override
    public List<KnowledgeWorkspaceResponse> listByUser(Long userId) {
        List<KnowledgeWorkspace> spaces = workspaceMapper.selectByUser(userId);
        Map<Long, Integer> counts = collectFileCounts(userId);
        return spaces.stream()
                .map(space -> toResponse(space, counts.getOrDefault(space.getId(), 0)))
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public KnowledgeWorkspaceResponse create(KnowledgeWorkspaceRequest request) {
        ensureUniqueName(request.getUserId(), null, request.getName());

        KnowledgeWorkspace space = KnowledgeWorkspace.builder()
                .userId(request.getUserId())
                .name(request.getName().trim())
                .description(request.getDescription())
                .coverColor(request.getCoverColor())
                .sort(request.getSort() != null ? request.getSort() : 0)
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .isDeleted(0)
                .build();
        workspaceMapper.insert(space);
        log.info("课程空间创建: id={}, userId={}, name={}", space.getId(), space.getUserId(), space.getName());
        return toResponse(space, 0);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public KnowledgeWorkspaceResponse update(Long workspaceId, KnowledgeWorkspaceRequest request) {
        KnowledgeWorkspace space = getOwned(workspaceId, request.getUserId());
        if (!Objects.equals(space.getName(), request.getName())) {
            ensureUniqueName(request.getUserId(), workspaceId, request.getName());
        }

        space.setName(request.getName().trim());
        space.setDescription(request.getDescription());
        if (request.getCoverColor() != null) {
            space.setCoverColor(request.getCoverColor());
        }
        if (request.getSort() != null) {
            space.setSort(request.getSort());
        }
        space.setUpdateTime(LocalDateTime.now());
        workspaceMapper.updateById(space);

        Map<Long, Integer> counts = collectFileCounts(space.getUserId());
        return toResponse(space, counts.getOrDefault(space.getId(), 0));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long workspaceId, Long userId) {
        KnowledgeWorkspace space = getOwned(workspaceId, userId);
        // 仅软删空间本身：被归类的文件改为未归类，不级联删除文件，避免误删教学资料
        workspaceMapper.deleteById(space.getId());
        moveFilesToUngrouped(workspaceId, userId);
        log.info("课程空间删除: id={}, userId={}", workspaceId, userId);
    }

    @Override
    public void verifyOwnership(Long workspaceId, Long userId) {
        if (workspaceId == null) {
            return;
        }
        getOwned(workspaceId, userId);
    }

    private void moveFilesToUngrouped(Long workspaceId, Long userId) {
        // MyBatis Plus 默认忽略 null 字段，所以走 UpdateWrapper.set 显式置空
        com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<KnowledgeFile> update =
                Wrappers.lambdaUpdate();
        update.eq(KnowledgeFile::getUserId, userId)
                .eq(KnowledgeFile::getWorkspaceId, workspaceId)
                .set(KnowledgeFile::getWorkspaceId, null)
                .set(KnowledgeFile::getUpdateTime, LocalDateTime.now());
        fileMapper.update(null, update);
    }

    private KnowledgeWorkspace getOwned(Long workspaceId, Long userId) {
        KnowledgeWorkspace space = workspaceMapper.selectById(workspaceId);
        if (space == null
                || (space.getIsDeleted() != null && space.getIsDeleted() == 1)
                || !Objects.equals(space.getUserId(), userId)) {
            throw new BusinessException(ErrorCode.KNOWLEDGE_WORKSPACE_NOT_FOUND);
        }
        return space;
    }

    private void ensureUniqueName(Long userId, Long excludeId, String name) {
        if (name == null || name.isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "课程名称不能为空");
        }
        LambdaQueryWrapper<KnowledgeWorkspace> wrapper = Wrappers.lambdaQuery();
        wrapper.eq(KnowledgeWorkspace::getUserId, userId)
                .eq(KnowledgeWorkspace::getName, name.trim())
                .ne(excludeId != null, KnowledgeWorkspace::getId, excludeId);
        Long count = workspaceMapper.selectCount(wrapper);
        if (count != null && count > 0) {
            throw new BusinessException(ErrorCode.KNOWLEDGE_WORKSPACE_DUPLICATE);
        }
    }

    private Map<Long, Integer> collectFileCounts(Long userId) {
        Map<Long, Integer> result = new HashMap<>();
        for (Map<String, Object> row : workspaceMapper.countFilesByWorkspace(userId)) {
            Object key = row.get("workspace_id");
            if (key == null) {
                continue;
            }
            Number cnt = (Number) row.get("cnt");
            result.put(((Number) key).longValue(), cnt == null ? 0 : cnt.intValue());
        }
        return result;
    }

    private KnowledgeWorkspaceResponse toResponse(KnowledgeWorkspace space, int fileCount) {
        return KnowledgeWorkspaceResponse.builder()
                .id(space.getId())
                .name(space.getName())
                .description(space.getDescription())
                .coverColor(space.getCoverColor())
                .sort(space.getSort())
                .fileCount(fileCount)
                .createTime(space.getCreateTime())
                .updateTime(space.getUpdateTime())
                .build();
    }
}
