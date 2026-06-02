-- ============================================================
-- EduSpark 中文分词配置脚本
-- ============================================================
-- 
-- 方案选择：
--   方案A：pg_trgm（简单，PostgreSQL自带，适合快速测试）
--   方案B：zhparser（推荐，真正的中文分词，需要安装扩展）
--
-- 使用说明：
--   1. 先执行方案A（快速验证效果）
--   2. 如需更好效果，再安装zhparser并执行方案B
-- ============================================================

-- ==================== 方案A：pg_trgm 模糊匹配 ====================
-- PostgreSQL 自带，无需额外安装

-- 1. 安装 pg_trgm 扩展
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- 2. 创建中文分词配置（使用 simple 但添加 trigram 支持）
-- 注意：这里我们创建一个自定义的分词器函数

-- 3. 创建分词函数：将中文文本按字符分割（简单方案）
-- 注意：PostgreSQL 的 simple 配置需要空格来分词
-- 所以我们需要在每个中文字符之间添加空格
-- 同时过滤掉停用词（只过滤真正的无意义虚词）
CREATE OR REPLACE FUNCTION chinese_tokenize(text TEXT) RETURNS TEXT AS $$
DECLARE
    result TEXT := '';
    char TEXT;
    stop_words TEXT[] := ARRAY['的', '了', '是', '在', '有', '和', '与', '或', '等', '这', '那', '个', '些', '怎', '么', '什', '哪', '里', '吗', '呢', '吧', '啊', '呀', '哦', '嗯', '着', '过', '把', '被', '让', '给', '向', '从', '对', '为', '以', '及', '但', '而', '却', '又', '也', '都', '就', '才', '还', '只', '不', '没', '很', '太', '于', '到', '能', '会', '可', '要', '得', '说', '去', '来'];
BEGIN
    FOR i IN 1..length(text) LOOP
        char := substring(text, i, 1);
        IF ascii(char) > 127 THEN
            IF NOT (char = ANY(stop_words)) THEN
                result := result || char || ' ';
            END IF;
        ELSIF char ~ '[a-zA-Z0-9]' THEN
            result := result || char;
        ELSE
            result := result || ' ';
        END IF;
    END LOOP;
    RETURN trim(regexp_replace(result, '\s+', ' ', 'g'));
END;
$$ LANGUAGE plpgsql IMMUTABLE;

-- 4. 创建新的全文检索配置
DROP TEXT SEARCH CONFIGURATION IF EXISTS chinese CASCADE;
CREATE TEXT SEARCH CONFIGURATION chinese (COPY = simple);

COMMENT ON FUNCTION chinese_tokenize(TEXT) IS '中文简单分词：移除标点，保留中英文数字';

-- 5. 更新触发器函数，使用新的分词方式
CREATE OR REPLACE FUNCTION chunk_search_vector_trigger()
RETURNS TRIGGER AS $$
BEGIN
    -- 使用自定义分词函数处理文本
    NEW.search_vector := to_tsvector('simple', chinese_tokenize(NEW.chunk_text));
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION chunk_search_vector_trigger() IS '自动更新全文检索向量（支持中文）';

-- 6. 创建 trigram 索引（用于模糊匹配）
DROP INDEX IF EXISTS idx_chunk_text_trgm;
CREATE INDEX idx_chunk_text_trgm ON knowledge_chunk USING GIN (chunk_text gin_trgm_ops);

-- 7. 验证
SELECT 'pg_trgm 扩展安装成功' AS status;
SELECT extname, extversion FROM pg_extension WHERE extname = 'pg_trgm';


-- ==================== 方案B：zhparser 中文分词（需要先安装扩展）====================
-- 
-- 安装步骤（Windows）：
-- 1. 下载 zhparser: https://github.com/amutu/zhparser/releases
-- 2. 将 zhparser.dll 放到 PostgreSQL 的 lib 目录
-- 3. 将 zhparser.control 和 zhparser--*.sql 放到 share/extension 目录
-- 4. 重启 PostgreSQL
-- 5. 执行以下 SQL
--
-- 安装步骤（Linux/Mac）：
-- 1. 安装 scws: yum install scws 或 brew install scws
-- 2. 编译安装 zhparser:
--    git clone https://github.com/amutu/zhparser.git
--    cd zhparser && make && make install
-- 3. 重启 PostgreSQL
-- 4. 执行以下 SQL

-- 取消注释以下代码来启用 zhparser：
/*
-- 安装 zhparser 扩展
CREATE EXTENSION IF NOT EXISTS zhparser;

-- 创建中文分词配置
DROP TEXT SEARCH CONFIGURATION IF EXISTS chinese_zh CASCADE;
CREATE TEXT SEARCH CONFIGURATION chinese_zh (PARSER = zhparser);
ALTER TEXT SEARCH CONFIGURATION chinese_zh ADD MAPPING FOR n,v,a,i,e,l WITH simple;

-- 更新触发器使用 zhparser
CREATE OR REPLACE FUNCTION chunk_search_vector_trigger()
RETURNS TRIGGER AS $$
BEGIN
    NEW.search_vector := to_tsvector('chinese_zh', NEW.chunk_text);
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- 验证
SELECT 'zhparser 中文分词配置成功' AS status;
SELECT to_tsvector('chinese_zh', '铝的氢氧化物制备方法');
*/


-- ==================== 重建现有数据的索引 ====================
-- 执行此命令更新所有现有分块的 search_vector

UPDATE knowledge_chunk 
SET search_vector = to_tsvector('simple', chinese_tokenize(chunk_text))
WHERE search_vector IS NULL 
   OR search_vector = to_tsvector('simple', chunk_text);

-- 验证更新结果
SELECT 
    '索引重建完成' AS status,
    COUNT(*) AS total_chunks,
    COUNT(search_vector) AS indexed_chunks
FROM knowledge_chunk;


-- ==================== 测试分词效果 ====================

-- 测试分词函数
SELECT chinese_tokenize('铝的氢氧化物怎么在实验室制备呢？') AS tokenized;

-- 测试全文检索
SELECT 
    to_tsvector('simple', chinese_tokenize('铝的氢氧化物制备方法')) AS search_vector,
    plainto_tsquery('simple', chinese_tokenize('制备方法')) AS query;

-- 测试检索效果
SELECT 
    c.id,
    c.chunk_text,
    ts_rank(
        to_tsvector('simple', chinese_tokenize(c.chunk_text)),
        plainto_tsquery('simple', chinese_tokenize('铝的氢氧化物制备'))
    ) AS rank_score
FROM knowledge_chunk c
JOIN knowledge_file f ON c.file_id = f.id
WHERE f.user_id = 1
  AND f.status = 1
  AND to_tsvector('simple', chinese_tokenize(c.chunk_text)) @@ plainto_tsquery('simple', chinese_tokenize('铝的氢氧化物制备'))
ORDER BY rank_score DESC
LIMIT 5;


-- ============================================================
-- 执行完成
-- ============================================================
DO $$
BEGIN
    RAISE NOTICE '========================================';
    RAISE NOTICE '  中文分词配置完成!';
    RAISE NOTICE '========================================';
    RAISE NOTICE '已安装:';
    RAISE NOTICE '  - pg_trgm 扩展（模糊匹配）';
    RAISE NOTICE '  - chinese_tokenize 函数（简单分词）';
    RAISE NOTICE '';
    RAISE NOTICE '下一步:';
    RAISE NOTICE '  1. 重启后端服务';
    RAISE NOTICE '  2. 测试检索效果';
    RAISE NOTICE '  3. 如需更好效果，安装 zhparser';
    RAISE NOTICE '========================================';
END $$;
