-- ============================================================
-- EduSpark 系统数据库初始化脚本
-- 数据库: PostgreSQL 16+ with pgvector 扩展
-- ============================================================
--
-- 使用说明：
--   1. 确保已安装 PostgreSQL 16+ 和 pgvector 扩展
--   2. 创建数据库: CREATE DATABASE eduspark;
--   3. 执行本脚本: psql -d eduspark -f init.sql
-- ============================================================

-- ==================== 1. 安装 pgvector 扩展 ====================

CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS unaccent;

-- 验证扩展安装
SELECT extname, extversion FROM pg_extension WHERE extname = 'vector';


-- ==================== 2. 用户表 (sys_user) ====================

DROP TABLE IF EXISTS sys_user CASCADE;

CREATE TABLE sys_user (
    id              BIGSERIAL PRIMARY KEY,
    username        VARCHAR(50),
    phone           VARCHAR(20) NOT NULL UNIQUE,
    email           VARCHAR(100),
    password        VARCHAR(255) NOT NULL,
    avatar          VARCHAR(500),
    role            VARCHAR(20) DEFAULT 'teacher',    -- student/teacher/admin
    status          SMALLINT DEFAULT 1,               -- 0-禁用 1-启用
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
CREATE INDEX idx_sys_user_status ON sys_user(status);
CREATE INDEX idx_sys_user_deleted ON sys_user(is_deleted);

COMMENT ON TABLE sys_user IS '系统用户表';


-- ==================== 3. 知识库文件表 (knowledge_file) ====================
DROP TABLE IF EXISTS knowledge_chunk CASCADE;
DROP TABLE IF EXISTS knowledge_file CASCADE;

CREATE TABLE knowledge_file (
    -- 主键（自增）
    id              BIGSERIAL PRIMARY KEY,

    -- 文件基本信息
    file_name       VARCHAR(255) NOT NULL,
    file_type       VARCHAR(50) NOT NULL,
    file_size       BIGINT NOT NULL,
    file_path       VARCHAR(500) NOT NULL,
    file_hash       VARCHAR(64) NOT NULL,
    user_id         BIGINT NOT NULL,

    -- 处理状态
    status          SMALLINT DEFAULT 0,          -- 0:处理中 1:成功 2:失败
    chunk_count     INT DEFAULT 0,
    error_message   TEXT,

    -- 扩展信息
    category        VARCHAR(100),
    description     VARCHAR(500),
    metadata        JSONB,                        -- 扩展属性（页数、字数等）

    -- 审计字段
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    is_deleted      SMALLINT DEFAULT 0,          -- 逻辑删除标记

    -- 约束
    CONSTRAINT uk_file_hash UNIQUE (file_hash)
);

-- 索引
CREATE INDEX idx_file_user_id ON knowledge_file(user_id);
CREATE INDEX idx_file_user_status ON knowledge_file(user_id, status);
CREATE INDEX idx_file_create_time ON knowledge_file(create_time DESC);
CREATE INDEX idx_file_deleted ON knowledge_file(is_deleted);

COMMENT ON TABLE knowledge_file IS '知识库文件表';
COMMENT ON COLUMN knowledge_file.file_hash IS '文件MD5哈希，用于去重检测';
COMMENT ON COLUMN knowledge_file.status IS '0-处理中 1-成功 2-失败';


-- ==================== 4. 文本分块表 (knowledge_chunk) ====================
CREATE TABLE knowledge_chunk (
    -- 主键（自增）
    id              BIGSERIAL PRIMARY KEY,

    -- 关联信息
    file_id         BIGINT NOT NULL,

    -- 分块信息
    chunk_index     INT NOT NULL,
    chunk_text      TEXT NOT NULL,
    chunk_hash      VARCHAR(64) NOT NULL,
    token_count     INT,

    -- 向量嵌入（1024维，维度根据Embedding模型调整）
    embedding       VECTOR(1024),

    -- 全文检索向量（PostgreSQL全文搜索）
    search_vector   TSVECTOR,

    -- 审计字段
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    -- 约束
    CONSTRAINT uk_chunk_hash UNIQUE (chunk_hash),
    CONSTRAINT fk_chunk_file FOREIGN KEY (file_id)
        REFERENCES knowledge_file(id) ON DELETE CASCADE
);

-- 索引
CREATE INDEX idx_chunk_file_id ON knowledge_chunk(file_id);
CREATE INDEX idx_chunk_hash ON knowledge_chunk(chunk_hash);
CREATE INDEX idx_chunk_embedding ON knowledge_chunk
    USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 64);  -- HNSW向量索引，高召回率
CREATE INDEX idx_chunk_search_vector ON knowledge_chunk USING GIN(search_vector);

COMMENT ON TABLE knowledge_chunk IS '文本分块表，含向量嵌入和全文检索能力';


-- ==================== 5. 对话会话表 (chat_session) ====================

-- 自动更新 search_vector 触发器函数
CREATE OR REPLACE FUNCTION chunk_search_vector_trigger()
RETURNS TRIGGER AS $$
BEGIN
    NEW.search_vector := to_tsvector('simple', NEW.chunk_text);
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- 触发器：插入/更新时自动更新全文检索向量
DROP TRIGGER IF EXISTS trg_chunk_search_vector ON knowledge_chunk;
CREATE TRIGGER trg_chunk_search_vector
    BEFORE INSERT OR UPDATE OF chunk_text ON knowledge_chunk
    FOR EACH ROW EXECUTE FUNCTION chunk_search_vector_trigger();

COMMENT ON FUNCTION chunk_search_vector_trigger() IS
    '自动更新全文检索向量触发器函数';


-- ==================== 6. 创建向量维度检查函数（验证） ====================

-- 验证向量维度是否匹配（1024维）
CREATE OR REPLACE FUNCTION validate_embedding_dimension()
RETURNS TABLE (
    id          BIGINT,
    file_id     BIGINT,
    dimension   INT
) AS $$
BEGIN
    RETURN QUERY
    SELECT
        c.id,
        c.file_id,
        array_length(c.embedding::real[], 1) AS dimension
    FROM knowledge_chunk c
    WHERE c.embedding IS NOT NULL
      AND array_length(c.embedding::real[], 1) != 1024;
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION validate_embedding_dimension() IS
    '验证向量维度是否匹配1024';


-- ==================== 7. 初始化配置（可选） ====================

-- 创建配置表（如果后续需要存储系统配置）
-- CREATE TABLE IF NOT EXISTS sys_config (
--     config_key   VARCHAR(100) PRIMARY KEY,
--     config_value TEXT,
--     description  VARCHAR(500),
--     create_time  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
--     update_time  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
-- );


-- ==================== 8. 验证脚本 ====================

-- 验证表创建成功
SELECT
    '表创建验证' AS check_item,
    COUNT(*) AS count
FROM information_schema.tables
WHERE table_schema = 'public'
  AND table_name IN ('knowledge_file', 'knowledge_chunk');

-- 验证索引创建成功
SELECT
    '索引创建验证' AS check_item,
    indexname,
    indexdef
FROM pg_indexes
WHERE schemaname = 'public'
  AND tablename IN ('knowledge_file', 'knowledge_chunk')
ORDER BY tablename, indexname;

-- 验证触发器创建成功
SELECT
    '触发器创建验证' AS check_item,
    trigger_name,
    event_manipulation,
    action_statement
FROM information_schema.triggers
WHERE event_object_schema = 'public'
  AND event_object_table IN ('knowledge_file', 'knowledge_chunk');


-- ==================== 5. 对话会话表 (chat_session) ====================
DROP TABLE IF EXISTS chat_message CASCADE;
DROP TABLE IF EXISTS chat_session CASCADE;

CREATE TABLE chat_session (
    id                  BIGSERIAL PRIMARY KEY,
    user_id             BIGINT NOT NULL,
    title               VARCHAR(200),
    status              SMALLINT DEFAULT 1,
    last_message        VARCHAR(500),
    message_count       INT DEFAULT 0,

    -- 教学模式相关字段
    mode                VARCHAR(20),
    stage               VARCHAR(20) DEFAULT 'idle',
    teaching_elements    JSONB,

    -- 生成结果相关字段
    generation_status   VARCHAR(20) DEFAULT 'pending',
    generation_result   TEXT,

    create_time         TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time         TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    is_deleted          SMALLINT DEFAULT 0
);

CREATE INDEX idx_session_user_id ON chat_session(user_id);
CREATE INDEX idx_session_user_status ON chat_session(user_id, status);
CREATE INDEX idx_session_create_time ON chat_session(create_time DESC);
CREATE INDEX idx_session_mode ON chat_session(user_id, mode);

COMMENT ON TABLE chat_session IS '对话会话表';


-- ==================== 6. 对话消息表 (chat_message) ====================
DROP TABLE IF EXISTS chat_message CASCADE;

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

    CONSTRAINT fk_message_session FOREIGN KEY (session_id)
        REFERENCES chat_session(id) ON DELETE CASCADE
);

CREATE INDEX idx_message_session_id ON chat_message(session_id);
CREATE INDEX idx_message_create_time ON chat_message(create_time);

COMMENT ON TABLE chat_message IS '对话消息表';


-- ==================== 9. 清理脚本（慎用！） ====================

-- 清理所有数据（测试用）
-- TRUNCATE TABLE knowledge_chunk, knowledge_file RESTART IDENTITY CASCADE;

-- 删除扩展和表（重建用）
-- DROP TRIGGER IF EXISTS trg_chunk_search_vector ON knowledge_chunk;
-- DROP FUNCTION IF EXISTS chunk_search_vector_trigger();
-- DROP TABLE IF EXISTS knowledge_chunk CASCADE;
-- DROP TABLE IF EXISTS knowledge_file CASCADE;
-- DROP EXTENSION IF EXISTS vector;


-- ============================================================
-- 执行完成
-- ============================================================
DO $$
BEGIN
    RAISE NOTICE '========================================';
    RAISE NOTICE '  EduSpark RAG 数据库初始化完成!';
    RAISE NOTICE '========================================';
    RAISE NOTICE '创建内容:';
    RAISE NOTICE '  - 扩展: vector, unaccent';
    RAISE NOTICE '  - 表: sys_user, knowledge_file, knowledge_chunk, chat_session, chat_message';
    RAISE NOTICE '  - 索引: 向量索引(HNSW), 全文索引(GIN)';
    RAISE NOTICE '  - 触发器: search_vector自动维护';
    RAISE NOTICE '';
    RAISE NOTICE '下一步:';
    RAISE NOTICE '  1. 启动 Ollama 服务并下载 Embedding 模型';
    RAISE NOTICE '  2. 修改 application.yml 中的数据库连接配置';
    RAISE NOTICE '  3. 运行 Spring Boot 应用';
    RAISE NOTICE '========================================';
END $$;
