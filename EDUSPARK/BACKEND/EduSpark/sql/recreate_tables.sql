-- ============================================================
-- 重建表结构：使用 BIGSERIAL 自增
-- ============================================================

-- 删除所有表
DROP TABLE IF EXISTS chat_message CASCADE;
DROP TABLE IF EXISTS chat_session CASCADE;
DROP TABLE IF EXISTS knowledge_chunk CASCADE;
DROP TABLE IF EXISTS knowledge_file CASCADE;
DROP TABLE IF EXISTS sys_user CASCADE;

-- sys_user 表
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

-- knowledge_file 表
CREATE TABLE knowledge_file (
    id              BIGSERIAL PRIMARY KEY,
    file_name       VARCHAR(255) NOT NULL,
    file_type       VARCHAR(50) NOT NULL,
    file_size       BIGINT NOT NULL,
    file_path       VARCHAR(500) NOT NULL,
    file_hash       VARCHAR(64) NOT NULL UNIQUE,
    user_id         BIGINT NOT NULL,
    status          SMALLINT DEFAULT 0,
    chunk_count     INT DEFAULT 0,
    error_message   TEXT,
    category        VARCHAR(100),
    description     VARCHAR(500),
    metadata        JSONB,
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    is_deleted      SMALLINT DEFAULT 0
);

-- knowledge_chunk 表
CREATE TABLE knowledge_chunk (
    id              BIGSERIAL PRIMARY KEY,
    file_id         BIGINT NOT NULL,
    chunk_index     INT NOT NULL,
    chunk_text      TEXT NOT NULL,
    chunk_hash      VARCHAR(64) NOT NULL UNIQUE,
    token_count     INT,
    embedding       VECTOR(1024),
    search_vector   TSVECTOR,
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_chunk_file FOREIGN KEY (file_id) REFERENCES knowledge_file(id) ON DELETE CASCADE
);

-- chat_session 表
CREATE TABLE chat_session (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT NOT NULL,
    title           VARCHAR(200),
    status          SMALLINT DEFAULT 1,
    last_message    VARCHAR(500),
    message_count   INT DEFAULT 0,
    mode            VARCHAR(20),
    stage           VARCHAR(20) DEFAULT 'idle',
    teaching_elements JSONB,
    generation_status VARCHAR(20) DEFAULT 'pending',
    generation_result TEXT,
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    is_deleted      SMALLINT DEFAULT 0
);

-- chat_message 表
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

-- 创建索引
CREATE INDEX idx_sys_user_phone ON sys_user(phone);
CREATE INDEX idx_file_user_id ON knowledge_file(user_id);
CREATE INDEX idx_chunk_file_id ON knowledge_chunk(file_id);
CREATE INDEX idx_chunk_embedding ON knowledge_chunk USING hnsw (embedding vector_cosine_ops) WITH (m = 16, ef_construction = 64);
CREATE INDEX idx_session_user_id ON chat_session(user_id);
CREATE INDEX idx_message_session_id ON chat_message(session_id);

-- 创建触发器
CREATE OR REPLACE FUNCTION chunk_search_vector_trigger() RETURNS TRIGGER AS $$
BEGIN
    NEW.search_vector := to_tsvector('simple', NEW.chunk_text);
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_chunk_search_vector BEFORE INSERT OR UPDATE OF chunk_text ON knowledge_chunk FOR EACH ROW EXECUTE FUNCTION chunk_search_vector_trigger();

SELECT 'Tables recreated successfully!' AS message;
