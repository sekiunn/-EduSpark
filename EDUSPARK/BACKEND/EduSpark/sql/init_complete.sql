-- ============================================================
-- EduSpark 完整初始化脚本（使用 PGroonga 中文分词）
-- ============================================================

-- ==================== 1. 安装扩展 ====================

CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS pgroonga;
CREATE EXTENSION IF NOT EXISTS unaccent;

-- 验证扩展安装
SELECT extname, extversion FROM pg_extension 
WHERE extname IN ('vector', 'pgroonga', 'unaccent');


-- ==================== 2. 用户表 ====================

DROP TABLE IF EXISTS sys_user CASCADE;

CREATE TABLE sys_user (
    id              BIGSERIAL PRIMARY KEY,
    username        VARCHAR(50),
    phone           VARCHAR(20) NOT NULL UNIQUE,
    email           VARCHAR(100),
    password        VARCHAR(255) NOT NULL,
    avatar          VARCHAR(500),
    role            VARCHAR(20) DEFAULT 'teacher',
    status          SMALLINT DEFAULT 1,
    bio             VARCHAR(500),
    metadata        JSONB,
    last_login_ip   VARCHAR(50),
    last_login_time TIMESTAMP,
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    is_deleted      SMALLINT DEFAULT 0
);

CREATE INDEX idx_sys_user_phone ON sys_user(phone);
CREATE INDEX idx_sys_user_role ON sys_user(role);

COMMENT ON TABLE sys_user IS '系统用户表';


-- ==================== 3. 知识库文件表 ====================

DROP TABLE IF EXISTS knowledge_chunk CASCADE;
DROP TABLE IF EXISTS knowledge_file CASCADE;

CREATE TABLE knowledge_file (
    id              BIGSERIAL PRIMARY KEY,
    file_name       VARCHAR(255) NOT NULL,
    file_type       VARCHAR(50) NOT NULL,
    file_size       BIGINT NOT NULL,
    file_path       VARCHAR(500) NOT NULL,
    file_hash       VARCHAR(64) NOT NULL,
    user_id         BIGINT NOT NULL,
    status          SMALLINT DEFAULT 0,
    chunk_count     INT DEFAULT 0,
    error_message   TEXT,
    category        VARCHAR(100),
    description     VARCHAR(500),
    metadata        JSONB,
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    is_deleted      SMALLINT DEFAULT 0,
    CONSTRAINT uk_file_hash UNIQUE (file_hash)
);

CREATE INDEX idx_file_user_id ON knowledge_file(user_id);
CREATE INDEX idx_file_user_status ON knowledge_file(user_id, status);

COMMENT ON TABLE knowledge_file IS '知识库文件表';


-- ==================== 4. 文本分块表 ====================

CREATE TABLE knowledge_chunk (
    id              BIGSERIAL PRIMARY KEY,
    file_id         BIGINT NOT NULL,
    chunk_index     INT NOT NULL,
    chunk_text      TEXT NOT NULL,
    chunk_hash      VARCHAR(64) NOT NULL,
    token_count     INT,
    embedding       VECTOR(1024),
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_chunk_hash UNIQUE (chunk_hash),
    CONSTRAINT fk_chunk_file FOREIGN KEY (file_id) REFERENCES knowledge_file(id) ON DELETE CASCADE
);

-- 向量索引
CREATE INDEX idx_chunk_embedding ON knowledge_chunk
    USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 64);

-- PGroonga 全文检索索引（支持中文分词）
CREATE INDEX idx_chunk_text_pgroonga ON knowledge_chunk USING pgroonga (chunk_text);

COMMENT ON TABLE knowledge_chunk IS '文本分块表';


-- ==================== 5. 对话会话表 ====================

DROP TABLE IF EXISTS chat_message CASCADE;
DROP TABLE IF EXISTS chat_session CASCADE;

CREATE TABLE chat_session (
    id                  BIGSERIAL PRIMARY KEY,
    user_id             BIGINT NOT NULL,
    title               VARCHAR(200),
    status              SMALLINT DEFAULT 1,
    last_message        VARCHAR(500),
    message_count       INT DEFAULT 0,
    mode                VARCHAR(20),
    stage               VARCHAR(20) DEFAULT 'idle',
    teaching_elements   JSONB,
    generation_status   VARCHAR(20) DEFAULT 'pending',
    generation_result   TEXT,
    create_time         TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time         TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    is_deleted          SMALLINT DEFAULT 0
);

CREATE INDEX idx_session_user_id ON chat_session(user_id);
CREATE INDEX idx_session_user_status ON chat_session(user_id, status);

COMMENT ON TABLE chat_session IS '对话会话表';


-- ==================== 6. 对话消息表 ====================

CREATE TABLE chat_message (
    id              BIGSERIAL PRIMARY KEY,
    session_id      BIGINT NOT NULL,
    role            VARCHAR(20) NOT NULL,
    content         TEXT NOT NULL,
    intent_type     VARCHAR(30),
    layer           SMALLINT,
    layer_desc      VARCHAR(50),
    cost_ms         INT,
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_message_session FOREIGN KEY (session_id) REFERENCES chat_session(id) ON DELETE CASCADE
);

CREATE INDEX idx_message_session_id ON chat_message(session_id);

COMMENT ON TABLE chat_message IS '对话消息表';


-- ==================== 7. 测试 PGroonga 全文检索 ====================

-- 插入测试数据
INSERT INTO knowledge_file (file_name, file_type, file_size, file_path, file_hash, user_id, status, chunk_count)
VALUES ('test.txt', 'txt', 100, '/test/test.txt', 'test_hash_001', 1, 1, 1);

INSERT INTO knowledge_chunk (file_id, chunk_index, chunk_text, chunk_hash, token_count)
VALUES (1, 1, '铝的氢氧化物制备方法：可以用铝盐与氨水反应制取氢氧化铝。', 'chunk_hash_001', 20);

-- 测试全文检索（使用 &@~ 操作符）
SELECT c.id, c.chunk_text, c.chunk_text &@~ '氢氧化物制备' AS match_score
FROM knowledge_chunk c
WHERE c.chunk_text &@~ '氢氧化物制备';

-- 清理测试数据
DELETE FROM knowledge_chunk WHERE file_id = 1;
DELETE FROM knowledge_file WHERE id = 1;


-- ==================== 8. 验证 ====================

SELECT '表创建验证' AS check_item, COUNT(*) AS count
FROM information_schema.tables
WHERE table_schema = 'public'
  AND table_name IN ('sys_user', 'knowledge_file', 'knowledge_chunk', 'chat_session', 'chat_message');

SELECT '扩展验证' AS check_item, extname, extversion
FROM pg_extension
WHERE extname IN ('vector', 'pgroonga', 'unaccent');

SELECT '索引验证' AS check_item, indexname 
FROM pg_indexes 
WHERE tablename = 'knowledge_chunk' AND indexname LIKE '%pgroonga%';


-- ============================================================
-- 执行完成
-- ============================================================
DO $$
BEGIN
    RAISE NOTICE '========================================';
    RAISE NOTICE '  EduSpark 数据库初始化完成!';
    RAISE NOTICE '========================================';
    RAISE NOTICE '已安装扩展:';
    RAISE NOTICE '  - vector (向量检索)';
    RAISE NOTICE '  - pgroonga (中文全文检索)';
    RAISE NOTICE '  - unaccent';
    RAISE NOTICE '';
    RAISE NOTICE 'PGroonga 使用方法:';
    RAISE NOTICE '  创建索引: CREATE INDEX idx ON table USING pgroonga (column)';
    RAISE NOTICE '  全文搜索: SELECT * FROM table WHERE column &@~ ''关键词''';
    RAISE NOTICE '========================================';
END $$;
