-- ============================================================
-- 清理历史"配色种子模板"（32 套）
-- ============================================================
-- 背景：早期 PptTemplateSchemaInitializer.seedBuiltinTemplatesIfEmpty 会自动灌入
-- 32 套配色种子（4 场景 × 8 配色）让 fallback 渲染器开箱可用。后续决策已纯走
-- pptx 模板路线，这些种子不再有用 —— 老师看到它们会困惑，配色字典也变成死代码。
--
-- 该文件**不是**自动执行的，需要你手动决定何时跑。
--
-- 执行：psql -d eduspark -f sql/cleanup_seed_templates.sql
-- ============================================================

-- 1. 逻辑删除所有"无 pptx 文件"的模板（这正是配色种子的特征）。
--    保留 enabled / scene_id / style_id 等元数据，万一要恢复可以 UPDATE is_deleted=0。
UPDATE ppt_template
SET is_deleted = 1,
    update_time = CURRENT_TIMESTAMP
WHERE is_deleted = 0
  AND (template_file_path IS NULL OR template_file_path = '');

-- 2. 如果你需要物理删除（不可恢复，请确认无业务依赖）：
-- DELETE FROM ppt_template WHERE is_deleted = 1 AND (template_file_path IS NULL OR template_file_path = '');

-- 3. 可选：清掉孤立的 ppt_template_style / ppt_template_scene（如果它们只被种子模板引用过）。
--    保留默认是 OK 的 —— 这些是分类元数据，管理员后续上传 pptx 时可以复用。

-- 4. 校验：跑完后看一眼还有多少启用的模板
-- SELECT id, name, template_file_path, enabled FROM ppt_template WHERE is_deleted = 0;
