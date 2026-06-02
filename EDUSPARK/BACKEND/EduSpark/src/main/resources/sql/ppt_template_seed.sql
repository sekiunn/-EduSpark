-- ============================================================
-- PPT 模板内置库 —— 4 场景 × 8 配色，覆盖高职工科 / 文科 / 信息技术 / 通识
-- 当 ppt_template 表为空 / 没有 enabled 模板时插入这一批，使 fallback 渲染器
-- 立刻具备 32 套可选主题，无需上传任何 .pptx 文件。
-- ============================================================

INSERT INTO ppt_template_scene (scene_code, scene_name, sort, enabled)
SELECT v.scene_code, v.scene_name, v.sort, 1 FROM (
    VALUES
        ('engineering',   '工科课程',     10),
        ('humanities',    '人文社科',     20),
        ('information',   '信息技术',     30),
        ('general',       '通识课堂',     40)
) AS v(scene_code, scene_name, sort)
WHERE NOT EXISTS (
    SELECT 1 FROM ppt_template_scene s WHERE s.scene_code = v.scene_code
);

INSERT INTO ppt_template_style (scene_id, style_code, style_name, sort, enabled)
SELECT s.id, v.style_code, v.style_name, v.sort, 1
FROM ppt_template_scene s
CROSS JOIN (
    VALUES
        ('classic_blue',   '经典蓝',     10),
        ('tech_indigo',    '深空靛',     20),
        ('forest_green',   '森林绿',     30),
        ('warm_orange',    '暖橙',       40),
        ('rose_pink',      '粉樱',       50),
        ('graphite_gray',  '石墨灰',     60),
        ('crimson_red',    '朱砂红',     70),
        ('sand_yellow',    '沙金黄',     80)
) AS v(style_code, style_name, sort)
WHERE NOT EXISTS (
    SELECT 1 FROM ppt_template_style x
    WHERE x.scene_id = s.id AND x.style_code = v.style_code
);

-- 32 套模板：场景 × 配色，共享渲染配色字典；fallback 渲染器读 render_config_json
INSERT INTO ppt_template (
    template_code, name, scene_id, style_id, description,
    engine_template_key, prompt_hint,
    render_config_json,
    enabled, is_default, sort
)
SELECT
    s.scene_code || '_' || st.style_code AS template_code,
    s.scene_name || ' · ' || st.style_name AS name,
    s.id  AS scene_id,
    st.id AS style_id,
    s.scene_name || '场景下的 ' || st.style_name || ' 配色，适合常规课堂。' AS description,
    'fallback_' || st.style_code AS engine_template_key,
    CASE st.style_code
        WHEN 'classic_blue'  THEN '保持稳重学院风，正文深灰，强调蓝色高亮。'
        WHEN 'tech_indigo'   THEN '强调科技感，靛蓝主色，建议配合代码 / 数据图。'
        WHEN 'forest_green'  THEN '清新自然，绿色主色，适合环保 / 生命科学话题。'
        WHEN 'warm_orange'   THEN '活力暖橙，适合面向新生 / 社团活动。'
        WHEN 'rose_pink'     THEN '柔和粉樱，适合人文艺术、心理学话题。'
        WHEN 'graphite_gray' THEN '克制石墨灰，强调内容本身，适合学术汇报。'
        WHEN 'crimson_red'   THEN '醒目朱砂红，适合主题班会、思政课堂。'
        WHEN 'sand_yellow'   THEN '低饱和沙金，适合传统文化、国学话题。'
    END AS prompt_hint,
    -- 注意：JSON 字段值会作为字符串入库，键名与 CoursewareServiceImpl#resolveThemeSpec 对齐
    CASE st.style_code
        WHEN 'classic_blue'  THEN '{"coverBackground":"#1E3A8A","accentColor":"#2563EB","accentSoftColor":"#DBEAFE","coverTextColor":"#FFFFFF","surfaceBackground":"#F8FAFC","titleColor":"#0F172A","bodyColor":"#334155","panelBorder":"#E2E8F0"}'
        WHEN 'tech_indigo'   THEN '{"coverBackground":"#1E1B4B","accentColor":"#6366F1","accentSoftColor":"#E0E7FF","coverTextColor":"#FFFFFF","surfaceBackground":"#FAFAFB","titleColor":"#1E1B4B","bodyColor":"#3F3F5F","panelBorder":"#E0E7FF"}'
        WHEN 'forest_green'  THEN '{"coverBackground":"#14532D","accentColor":"#16A34A","accentSoftColor":"#DCFCE7","coverTextColor":"#FFFFFF","surfaceBackground":"#FAFEFA","titleColor":"#14532D","bodyColor":"#374151","panelBorder":"#DCFCE7"}'
        WHEN 'warm_orange'   THEN '{"coverBackground":"#7C2D12","accentColor":"#EA580C","accentSoftColor":"#FFEDD5","coverTextColor":"#FFFFFF","surfaceBackground":"#FFFBF5","titleColor":"#7C2D12","bodyColor":"#44403C","panelBorder":"#FFEDD5"}'
        WHEN 'rose_pink'     THEN '{"coverBackground":"#831843","accentColor":"#DB2777","accentSoftColor":"#FCE7F3","coverTextColor":"#FFFFFF","surfaceBackground":"#FFF7FB","titleColor":"#500724","bodyColor":"#52525B","panelBorder":"#FCE7F3"}'
        WHEN 'graphite_gray' THEN '{"coverBackground":"#111827","accentColor":"#475569","accentSoftColor":"#E5E7EB","coverTextColor":"#FFFFFF","surfaceBackground":"#FFFFFF","titleColor":"#111827","bodyColor":"#374151","panelBorder":"#E5E7EB"}'
        WHEN 'crimson_red'   THEN '{"coverBackground":"#7F1D1D","accentColor":"#DC2626","accentSoftColor":"#FEE2E2","coverTextColor":"#FFFFFF","surfaceBackground":"#FEFAFA","titleColor":"#7F1D1D","bodyColor":"#44403C","panelBorder":"#FEE2E2"}'
        WHEN 'sand_yellow'   THEN '{"coverBackground":"#78350F","accentColor":"#CA8A04","accentSoftColor":"#FEF3C7","coverTextColor":"#FFFFFF","surfaceBackground":"#FFFCF2","titleColor":"#78350F","bodyColor":"#44403C","panelBorder":"#FEF3C7"}'
    END AS render_config_json,
    1 AS enabled,
    CASE WHEN s.scene_code = 'engineering' AND st.style_code = 'classic_blue' THEN 1 ELSE 0 END AS is_default,
    (s.sort * 100 + st.sort) AS sort
FROM ppt_template_scene s
CROSS JOIN ppt_template_style st
WHERE st.scene_id = s.id
  AND NOT EXISTS (
      SELECT 1 FROM ppt_template t
      WHERE t.template_code = s.scene_code || '_' || st.style_code
  );
