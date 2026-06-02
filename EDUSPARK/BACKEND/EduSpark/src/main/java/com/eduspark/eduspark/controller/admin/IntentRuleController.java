package com.eduspark.eduspark.controller.admin;

import com.eduspark.eduspark.dto.common.Result;
import com.eduspark.eduspark.pojo.entity.IntentType;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * 意图规则管理接口（管理端）
 * <p>
 * 提供意图识别规则的增删改查功能
 */
@Slf4j
@Validated
@RestController
@RequestMapping("/admin/intent-rules")
public class IntentRuleController {

    // ========== 内存存储（生产环境应替换为数据库） ==========
    private final Map<Long, IntentRule> ruleStore = new ConcurrentHashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(1);

    public IntentRuleController() {
        // 初始化默认规则
        initDefaultRules();
    }

    private void initDefaultRules() {
        // TEACHING 关键词
        addRuleInternal(new IntentRule(null, "方程", IntentType.TEACHING, 1, true, "数学方程"));
        addRuleInternal(new IntentRule(null, "函数", IntentType.TEACHING, 1, true, "数学函数"));
        addRuleInternal(new IntentRule(null, "定理", IntentType.TEACHING, 1, true, "定理定义"));
        addRuleInternal(new IntentRule(null, "公式", IntentType.TEACHING, 1, true, "公式应用"));
        addRuleInternal(new IntentRule(null, "解题", IntentType.TEACHING, 2, true, "解题方法"));
        addRuleInternal(new IntentRule(null, "作业", IntentType.TEACHING, 1, true, "作业相关"));
        addRuleInternal(new IntentRule(null, "考试", IntentType.TEACHING, 1, true, "考试相关"));
        addRuleInternal(new IntentRule(null, "知识点", IntentType.TEACHING, 2, true, "知识点讲解"));

        // CASUAL 关键词
        addRuleInternal(new IntentRule(null, "你好", IntentType.CASUAL, 1, true, "问候语"));
        addRuleInternal(new IntentRule(null, "谢谢", IntentType.CASUAL, 1, true, "感谢语"));
        addRuleInternal(new IntentRule(null, "再见", IntentType.CASUAL, 1, true, "告别语"));
        addRuleInternal(new IntentRule(null, "哈哈", IntentType.CASUAL, 1, true, "笑声"));
        addRuleInternal(new IntentRule(null, "好的", IntentType.CASUAL, 1, true, "确认"));

        // TOOL 关键词
        addRuleInternal(new IntentRule(null, "上传", IntentType.TOOL, 1, true, "上传操作"));
        addRuleInternal(new IntentRule(null, "下载", IntentType.TOOL, 1, true, "下载操作"));
        addRuleInternal(new IntentRule(null, "登录", IntentType.TOOL, 1, true, "登录操作"));
        addRuleInternal(new IntentRule(null, "注册", IntentType.TOOL, 1, true, "注册操作"));

        // SENSITIVE 关键词（黑名单）
        addRuleInternal(new IntentRule(null, "暴力", IntentType.SENSITIVE, 1, true, "敏感词"));
        addRuleInternal(new IntentRule(null, "色情", IntentType.SENSITIVE, 1, true, "敏感词"));
        addRuleInternal(new IntentRule(null, "赌博", IntentType.SENSITIVE, 1, true, "敏感词"));
    }

    private void addRuleInternal(IntentRule rule) {
        long id = idGenerator.getAndIncrement();
        rule.setId(id);
        rule.setCreatedAt(LocalDateTime.now());
        rule.setUpdatedAt(LocalDateTime.now());
        ruleStore.put(id, rule);
    }

    // ==================== 接口实现 ====================

    /**
     * 查询所有规则
     */
    @GetMapping
    public Result<List<IntentRule>> list(
            @RequestParam(required = false) IntentType category,
            @RequestParam(required = false) Boolean enabled
    ) {
        log.info("查询意图规则: category={}, enabled={}", category, enabled);

        List<IntentRule> result = ruleStore.values().stream()
                .filter(r -> category == null || r.getCategory() == category)
                .filter(r -> enabled == null || r.getEnabled() == enabled)
                .sorted((a, b) -> {
                    int catComp = a.getCategory().name().compareTo(b.getCategory().name());
                    if (catComp != 0) return catComp;
                    return Integer.compare(b.getPriority(), a.getPriority());
                })
                .collect(Collectors.toList());

        return Result.success(result);
    }

    /**
     * 新增规则
     */
    @PostMapping
    public Result<IntentRule> create(@RequestBody @Validated IntentRule rule) {
        log.info("新增意图规则: keyword={}, category={}", rule.getKeyword(), rule.getCategory());

        // 检查关键词是否已存在
        boolean exists = ruleStore.values().stream()
                .anyMatch(r -> r.getKeyword().equals(rule.getKeyword()) && r.getEnabled());
        if (exists) {
            return Result.fail("关键词已存在");
        }

        addRuleInternal(rule);

        return Result.success(rule);
    }

    /**
     * 修改规则
     */
    @PutMapping("/{id}")
    public Result<IntentRule> update(@PathVariable Long id, @RequestBody @Validated IntentRule rule) {
        log.info("修改意图规则: id={}, keyword={}, category={}", id, rule.getKeyword(), rule.getCategory());

        IntentRule existing = ruleStore.get(id);
        if (existing == null) {
            return Result.fail("规则不存在");
        }

        existing.setKeyword(rule.getKeyword());
        existing.setCategory(rule.getCategory());
        existing.setPriority(rule.getPriority());
        existing.setEnabled(rule.getEnabled());
        existing.setRemark(rule.getRemark());
        existing.setUpdatedAt(LocalDateTime.now());

        return Result.success(existing);
    }

    /**
     * 删除规则
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        log.info("删除意图规则: id={}", id);

        if (!ruleStore.containsKey(id)) {
            return Result.fail("规则不存在");
        }

        ruleStore.remove(id);
        return Result.success(null);
    }

    /**
     * 启用/禁用规则
     */
    @PatchMapping("/{id}/toggle")
    public Result<IntentRule> toggle(@PathVariable Long id) {
        log.info("切换规则状态: id={}", id);

        IntentRule rule = ruleStore.get(id);
        if (rule == null) {
            return Result.fail("规则不存在");
        }

        rule.setEnabled(!rule.getEnabled());
        rule.setUpdatedAt(LocalDateTime.now());

        log.info("规则状态切换: id={}, enabled={}", id, rule.getEnabled());

        return Result.success(rule);
    }

    /**
     * 批量导入规则
     */
    @PostMapping("/batch")
    public Result<Map<String, Object>> batchImport(@RequestBody List<IntentRule> rules) {
        log.info("批量导入规则: count={}", rules.size());

        int success = 0;
        int skip = 0;
        List<String> errors = new ArrayList<>();

        for (IntentRule rule : rules) {
            if (rule.getKeyword() == null || rule.getKeyword().isBlank()) {
                errors.add("关键词为空");
                skip++;
                continue;
            }

            boolean exists = ruleStore.values().stream()
                    .anyMatch(r -> r.getKeyword().equals(rule.getKeyword()) && r.getEnabled());
            if (exists) {
                skip++;
                continue;
            }

            addRuleInternal(rule);
            success++;
        }

        log.info("批量导入完成: success={}, skip={}", success, skip);

        return Result.success(Map.of(
                "success", success,
                "skip", skip,
                "errors", errors
        ));
    }

    // ==================== 内部类 ====================

    @Data
    public static class IntentRule {
        private Long id;
        private String keyword;          // 关键词
        private IntentType category;     // 分类
        private Integer priority;         // 优先级
        private Boolean enabled;          // 是否启用
        private String remark;            // 备注
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        public IntentRule(Long id, String keyword, IntentType category,
                          Integer priority, Boolean enabled, String remark) {
            this.id = id;
            this.keyword = keyword;
            this.category = category;
            this.priority = priority;
            this.enabled = enabled;
            this.remark = remark;
        }
    }
}
