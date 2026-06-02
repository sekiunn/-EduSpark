-- ============================================================
-- EduSpark 中文分词配置脚本（zhparser 方案）
-- ============================================================
-- 
-- 前置条件：需要先安装 zhparser 扩展
-- 
-- Windows 安装步骤：
-- 1. 下载 zhparser: https://github.com/amutu/zhparser/releases
--    或使用预编译版本: https://github.com/bedefaced/zhparser-windows
-- 2. 将 zhparser.dll 放到 PostgreSQL 的 lib 目录
--    例如: C:\Program Files\PostgreSQL\16\lib\
-- 3. 将 zhparser.control 和 zhparser--*.sql 放到 share/extension 目录
--    例如: C:\Program Files\PostgreSQL\16\share\extension\
-- 4. 重启 PostgreSQL 服务
-- 5. 执行本脚本
--
-- Linux 安装步骤：
-- 1. 安装 scws: 
--    CentOS: yum install scws scws-devel
--    Ubuntu: apt-get install libscws-dev
--    Mac: brew install scws
-- 2. 编译安装 zhparser:
--    git clone https://github.com/amutu/zhparser.git
--    cd zhparser
--    make
--    make install
-- 3. 重启 PostgreSQL
-- 4. 执行本脚本
-- ============================================================

-- ==================== 1. 安装 zhparser 扩展 ====================

CREATE EXTENSION IF NOT EXISTS zhparser;

-- 验证安装
SELECT extname, extversion FROM pg_extension WHERE extname = 'zhparser';

-- ==================== 2. 创建中文分词配置 ====================

-- 删除旧配置（如果存在）
DROP TEXT SEARCH CONFIGURATION IF EXISTS chinese CASCADE;

-- 创建中文分词配置
CREATE TEXT SEARCH CONFIGURATION chinese (PARSER = zhparser);

-- 添加 token 映射
-- n: 名词, v: 动词, a: 形容词, i: 成语, e: 叹词, l: 习语
ALTER TEXT SEARCH CONFIGURATION chinese ADD MAPPING FOR n,v,a,i,e,l WITH simple;

-- ==================== 3. 更新触发器函数 ====================

CREATE OR REPLACE FUNCTION chunk_search_vector_trigger()
RETURNS TRIGGER AS $$
BEGIN
    NEW.search_vector := to_tsvector('chinese', NEW.chunk_text);
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION chunk_search_vector_trigger() IS '自动更新全文检索向量（使用zhparser中文分词）';

-- ==================== 4. 重建现有数据的索引 ====================

UPDATE knowledge_chunk 
SET search_vector = to_tsvector('chinese', chunk_text);

-- 验证更新结果
SELECT 
    '索引重建完成' AS status,
    COUNT(*) AS total_chunks,
    COUNT(search_vector) AS indexed_chunks
FROM knowledge_chunk;

-- ==================== 5. 测试分词效果 ====================

-- 测试分词
SELECT to_tsvector('chinese', '铝的氢氧化物制备方法');
-- 预期输出: '铝':1 '氢氧化物':2 '制备':3 '方法':4

-- 测试查询
SELECT plainto_tsquery('chinese', '铝的氢氧化物怎么制备');
-- 预期输出: '铝' & '氢氧化物' & '制备'

-- 测试检索
SELECT 
    c.id,
    c.chunk_index,
    c.chunk_text,
    ts_rank(
        to_tsvector('chinese', c.chunk_text),
        plainto_tsquery('chinese', '铝的氢氧化物制备')
    ) AS rank_score
FROM knowledge_chunk c
JOIN knowledge_file f ON c.file_id = f.id
WHERE f.user_id = 1
  AND f.status = 1
  AND to_tsvector('chinese', c.chunk_text) @@ plainto_tsquery('chinese', '铝的氢氧化物制备')
ORDER BY rank_score DESC
LIMIT 5;


-- ============================================================
-- 执行完成
-- ============================================================
DO $$
BEGIN
    RAISE NOTICE '========================================';
    RAISE NOTICE '  zhparser 中文分词配置完成!';
    RAISE NOTICE '========================================';
    RAISE NOTICE '已安装:';
    RAISE NOTICE '  - zhparser 扩展';
    RAISE NOTICE '  - chinese 分词配置';
    RAISE NOTICE '';
    RAISE NOTICE '下一步:';
    RAISE NOTICE '  1. 重启后端服务';
    RAISE NOTICE '  2. 测试检索效果';
    RAISE NOTICE '========================================';
END $$;
