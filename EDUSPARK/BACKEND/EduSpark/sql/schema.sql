-- ============================================================
-- EduSpark 系统数据库完整 Schema（权威版本）
-- 数据库：PostgreSQL 16+ with pgvector
-- 截止：2026-05-27，跟当前 entity 代码完全对齐
-- ============================================================
--
-- 本文件汇总了所有 12 张业务表的 DDL（含应用启动时各 SchemaInitializer
-- 自动 ALTER 补的列），是新机器初始化 / 老数据库升级的单一来源。
--
-- 使用：
--   psql -d eduspark -f sql/schema.sql
--
-- 幂等设计：所有 CREATE / ALTER / INDEX 都用 IF NOT EXISTS，
-- 重复执行不会报错，也不会丢数据。
--
-- 表清单：
--   1. sys_user                  系统用户
--   2. knowledge_workspace       知识库课程空间
--   3. knowledge_file            知识库文件
--   4. knowledge_chunk           文本分块（向量 + 全文检索）
--   5. chat_session              对话会话
--   6. chat_message              对话消息
--   7. ppt_template_scene        PPT 模板场景
--   8. ppt_template_style        PPT 模板风格
--   9. ppt_template              PPT 模板主表
--  10. ppt_document              PPT 工作区文档
--  11. lesson_plan_document      教案工作区文档
--  12. interactive_document      互动内容工作区文档
-- ============================================================


-- ============================================================
-- 0. 必需扩展
-- ============================================================
CREATE EXTENSION IF NOT EXISTS vector;     -- 向量检索（pgvector）
CREATE EXTENSION IF NOT EXISTS pgroonga;   -- 中文全文检索（KnowledgeChunkMapper 用 &@~ 操作符 + pgroonga_score）
CREATE EXTENSION IF NOT EXISTS unaccent;


-- ============================================================
-- 1. sys_user · 系统用户表
-- ============================================================
CREATE TABLE IF NOT EXISTS sys_user (
    id              BIGSERIAL PRIMARY KEY,
    username        VARCHAR(50),
    phone           VARCHAR(20) NOT NULL UNIQUE,
    email           VARCHAR(100),
    password        VARCHAR(255) NOT NULL,
    avatar          VARCHAR(500),
    role            VARCHAR(20) DEFAULT 'teacher',         -- student / teacher / admin
    status          SMALLINT     DEFAULT 1,                -- 0:禁用 1:启用
    bio             VARCHAR(500),
    metadata        JSONB,
    last_login_ip   VARCHAR(50),
    last_login_time TIMESTAMP,
    create_time     TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    is_deleted      SMALLINT     DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_sys_user_phone   ON sys_user(phone);
CREATE INDEX IF NOT EXISTS idx_sys_user_role    ON sys_user(role);
CREATE INDEX IF NOT EXISTS idx_sys_user_status  ON sys_user(status);
CREATE INDEX IF NOT EXISTS idx_sys_user_deleted ON sys_user(is_deleted);
COMMENT ON TABLE sys_user IS '系统用户表';


-- ============================================================
-- 2. knowledge_workspace · 知识库课程空间
--    教师按课程隔离上传的资料容器，对应 KnowledgeWorkspace entity
-- ============================================================
CREATE TABLE IF NOT EXISTS knowledge_workspace (
    id           BIGSERIAL PRIMARY KEY,
    user_id      BIGINT       NOT NULL,
    name         VARCHAR(80)  NOT NULL,
    description  VARCHAR(300),
    cover_color  VARCHAR(20),
    sort         INTEGER      NOT NULL DEFAULT 0,
    create_time  TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    update_time  TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    is_deleted   SMALLINT     NOT NULL DEFAULT 0,
    CONSTRAINT uk_kw_user_name UNIQUE (user_id, name, is_deleted)
);
CREATE INDEX IF NOT EXISTS idx_kw_user_sort ON knowledge_workspace(user_id, sort);
COMMENT ON TABLE knowledge_workspace IS '知识库课程空间：教师按课程隔离上传的教学资料';


-- ============================================================
-- 3. knowledge_file · 知识库文件表
-- ============================================================
CREATE TABLE IF NOT EXISTS knowledge_file (
    id              BIGSERIAL PRIMARY KEY,
    file_name       VARCHAR(255) NOT NULL,
    file_type       VARCHAR(50)  NOT NULL,
    file_size       BIGINT       NOT NULL,
    file_path       VARCHAR(500) NOT NULL,
    file_hash       VARCHAR(64)  NOT NULL,
    user_id         BIGINT       NOT NULL,
    workspace_id    BIGINT,                                -- 所属课程空间 ID，NULL 表示未归类
    status          SMALLINT     DEFAULT 0,                -- 0:处理中 1:成功 2:失败
    chunk_count     INT          DEFAULT 0,
    error_message   TEXT,
    category        VARCHAR(100),
    description     VARCHAR(500),
    metadata        JSONB,
    create_time     TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    is_deleted      SMALLINT     DEFAULT 0,
    CONSTRAINT uk_file_hash UNIQUE (file_hash)
);
-- 老库补漂移：早期版本没有 workspace_id 列
ALTER TABLE knowledge_file ADD COLUMN IF NOT EXISTS workspace_id BIGINT;

CREATE INDEX IF NOT EXISTS idx_file_user_id        ON knowledge_file(user_id);
CREATE INDEX IF NOT EXISTS idx_file_user_status    ON knowledge_file(user_id, status);
CREATE INDEX IF NOT EXISTS idx_file_create_time    ON knowledge_file(create_time DESC);
CREATE INDEX IF NOT EXISTS idx_file_deleted        ON knowledge_file(is_deleted);
CREATE INDEX IF NOT EXISTS idx_file_workspace      ON knowledge_file(workspace_id);
CREATE INDEX IF NOT EXISTS idx_file_user_workspace ON knowledge_file(user_id, workspace_id);
COMMENT ON TABLE knowledge_file IS '知识库文件表';
COMMENT ON COLUMN knowledge_file.file_hash    IS '文件MD5哈希，用于去重检测';
COMMENT ON COLUMN knowledge_file.status       IS '0-处理中 1-成功 2-失败';
COMMENT ON COLUMN knowledge_file.workspace_id IS '所属课程空间 ID，NULL 表示未归类';


-- ============================================================
-- 4. knowledge_chunk · 文本分块（含向量 + PGroonga 全文检索）
--    全文检索方案与 KnowledgeChunkMapper 绑定：
--      chunk_text &@~ keywords      （PGroonga 中文分词 + OR 查询）
--      pgroonga_score(tableoid, ctid)（命中评分）
--    所以**不需要** search_vector / TSVECTOR / GIN 触发器那一套。
-- ============================================================
CREATE TABLE IF NOT EXISTS knowledge_chunk (
    id            BIGSERIAL PRIMARY KEY,
    file_id       BIGINT       NOT NULL,
    chunk_index   INT          NOT NULL,
    chunk_text    TEXT         NOT NULL,
    chunk_hash    VARCHAR(64)  NOT NULL,
    token_count   INT,
    embedding     VECTOR(1024),                            -- 维度跟 mxbai-embed-large 对齐
    create_time   TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_chunk_hash UNIQUE (chunk_hash),
    CONSTRAINT fk_chunk_file FOREIGN KEY (file_id)
        REFERENCES knowledge_file(id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_chunk_file_id ON knowledge_chunk(file_id);
CREATE INDEX IF NOT EXISTS idx_chunk_hash    ON knowledge_chunk(chunk_hash);
-- HNSW 向量索引（高召回率）
CREATE INDEX IF NOT EXISTS idx_chunk_embedding ON knowledge_chunk
    USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 64);
-- PGroonga 全文检索索引（中文分词 + &@~ 操作符）
CREATE INDEX IF NOT EXISTS idx_chunk_text_pgroonga ON knowledge_chunk
    USING pgroonga (chunk_text);
COMMENT ON TABLE knowledge_chunk IS '文本分块表，含向量嵌入和 PGroonga 全文检索能力';


-- ============================================================
-- 5. chat_session · 对话会话表
-- ============================================================
CREATE TABLE IF NOT EXISTS chat_session (
    id                BIGSERIAL PRIMARY KEY,
    session_id        VARCHAR(64),                          -- 业务 UUID（对外暴露的会话 ID）
    user_id           BIGINT       NOT NULL,
    title             VARCHAR(200),
    status            SMALLINT     DEFAULT 1,               -- 0:已结束 1:活跃
    last_message      VARCHAR(500),
    message_count     INT          DEFAULT 0,
    mode              VARCHAR(20),                          -- ppt / lesson_plan / interactive
    stage             VARCHAR(20)  DEFAULT 'idle',          -- idle/clarifying/confirming/generating/completed
    teaching_elements JSONB,
    generation_status VARCHAR(20)  DEFAULT 'pending',       -- pending/processing/completed/failed
    generation_result TEXT,
    create_time       TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    update_time       TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    is_deleted        SMALLINT     DEFAULT 0
);
-- 老库补漂移：早期 init.sql 没有 session_id 列
ALTER TABLE chat_session ADD COLUMN IF NOT EXISTS session_id VARCHAR(64);

CREATE UNIQUE INDEX IF NOT EXISTS uk_chat_session_session_id ON chat_session(session_id);
CREATE INDEX IF NOT EXISTS idx_session_user_id     ON chat_session(user_id);
CREATE INDEX IF NOT EXISTS idx_session_user_status ON chat_session(user_id, status);
CREATE INDEX IF NOT EXISTS idx_session_create_time ON chat_session(create_time DESC);
CREATE INDEX IF NOT EXISTS idx_session_mode        ON chat_session(user_id, mode);
COMMENT ON TABLE chat_session IS '对话会话表';
COMMENT ON COLUMN chat_session.session_id IS '业务 UUID，对外暴露给前端 / API 用';


-- ============================================================
-- 6. chat_message · 对话消息表
-- ============================================================
CREATE TABLE IF NOT EXISTS chat_message (
    id          BIGSERIAL PRIMARY KEY,
    session_id  BIGINT       NOT NULL,
    role        VARCHAR(20)  NOT NULL,                     -- user / assistant
    content     TEXT         NOT NULL,
    intent_type VARCHAR(30),
    layer       SMALLINT,
    layer_desc  VARCHAR(50),
    cost_ms     INT,
    card_type   VARCHAR(50),                                -- blueprint_confirm / ppt_stage_entry / ...
    card_data   TEXT,                                       -- JSON 数据，供前端渲染特殊卡片
    create_time TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_message_session FOREIGN KEY (session_id)
        REFERENCES chat_session(id) ON DELETE CASCADE
);
-- 老库补漂移：早期 init.sql 没有 card_type / card_data 列
ALTER TABLE chat_message ADD COLUMN IF NOT EXISTS card_type VARCHAR(50);
ALTER TABLE chat_message ADD COLUMN IF NOT EXISTS card_data TEXT;

CREATE INDEX IF NOT EXISTS idx_message_session_id  ON chat_message(session_id);
CREATE INDEX IF NOT EXISTS idx_message_create_time ON chat_message(create_time);
COMMENT ON TABLE chat_message IS '对话消息表';


-- ============================================================
-- 7. ppt_template_scene · PPT 模板场景分类
-- ============================================================
CREATE TABLE IF NOT EXISTS ppt_template_scene (
    id          BIGSERIAL PRIMARY KEY,
    scene_code  VARCHAR(100) NOT NULL,
    scene_name  VARCHAR(100) NOT NULL,
    sort        INTEGER      NOT NULL DEFAULT 0,
    enabled     SMALLINT     NOT NULL DEFAULT 1,
    create_time TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    is_deleted  SMALLINT     DEFAULT 0,
    CONSTRAINT uk_ppt_template_scene_code UNIQUE (scene_code),
    CONSTRAINT uk_ppt_template_scene_name UNIQUE (scene_name)
);
CREATE INDEX IF NOT EXISTS idx_ppt_template_scene_enabled_sort
    ON ppt_template_scene(enabled, sort);


-- ============================================================
-- 8. ppt_template_style · PPT 模板风格分类
-- ============================================================
CREATE TABLE IF NOT EXISTS ppt_template_style (
    id          BIGSERIAL PRIMARY KEY,
    scene_id    BIGINT       NOT NULL,
    style_code  VARCHAR(100) NOT NULL,
    style_name  VARCHAR(100) NOT NULL,
    sort        INTEGER      NOT NULL DEFAULT 0,
    enabled     SMALLINT     NOT NULL DEFAULT 1,
    create_time TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    is_deleted  SMALLINT     DEFAULT 0,
    CONSTRAINT fk_ppt_template_style_scene
        FOREIGN KEY (scene_id) REFERENCES ppt_template_scene(id),
    CONSTRAINT uk_ppt_template_style_scene_code UNIQUE (scene_id, style_code),
    CONSTRAINT uk_ppt_template_style_scene_name UNIQUE (scene_id, style_name)
);
CREATE INDEX IF NOT EXISTS idx_ppt_template_style_scene_id
    ON ppt_template_style(scene_id);
CREATE INDEX IF NOT EXISTS idx_ppt_template_style_scene_enabled_sort
    ON ppt_template_style(scene_id, enabled, sort);


-- ============================================================
-- 9. ppt_template · PPT 模板主表
-- ============================================================
CREATE TABLE IF NOT EXISTS ppt_template (
    id                    BIGSERIAL PRIMARY KEY,
    template_code         VARCHAR(100) NOT NULL,
    name                  VARCHAR(120) NOT NULL,
    scene_id              BIGINT       NOT NULL,
    style_id              BIGINT       NOT NULL,
    cover_url             VARCHAR(500),
    preview_images_json   TEXT,
    description           VARCHAR(500),
    engine_template_key   VARCHAR(100) NOT NULL,            -- 可由 templateCode 自动派生
    prompt_hint           TEXT,
    blueprint_config_json TEXT,
    render_config_json    TEXT,                             -- pptx 解析出的 TemplateStructure JSON
    template_file_path    VARCHAR(500),                     -- 相对于 ${courseware.storage.local-path}/templates 的路径
    enabled               SMALLINT     NOT NULL DEFAULT 1,
    is_default            SMALLINT     NOT NULL DEFAULT 0,
    sort                  INTEGER      NOT NULL DEFAULT 0,
    version               INTEGER      NOT NULL DEFAULT 1,
    create_time           TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    update_time           TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    is_deleted            SMALLINT     DEFAULT 0,
    CONSTRAINT fk_ppt_template_scene FOREIGN KEY (scene_id)
        REFERENCES ppt_template_scene(id),
    CONSTRAINT fk_ppt_template_style FOREIGN KEY (style_id)
        REFERENCES ppt_template_style(id),
    CONSTRAINT uk_ppt_template_code  UNIQUE (template_code)
);
-- 老库补漂移：早期版本没有 template_file_path 列
ALTER TABLE ppt_template ADD COLUMN IF NOT EXISTS template_file_path VARCHAR(500);

CREATE INDEX IF NOT EXISTS idx_ppt_template_scene_id            ON ppt_template(scene_id);
CREATE INDEX IF NOT EXISTS idx_ppt_template_style_id            ON ppt_template(style_id);
CREATE INDEX IF NOT EXISTS idx_ppt_template_scene_style         ON ppt_template(scene_id, style_id);
CREATE INDEX IF NOT EXISTS idx_ppt_template_engine_template_key ON ppt_template(engine_template_key);
CREATE INDEX IF NOT EXISTS idx_ppt_template_enabled_sort        ON ppt_template(enabled, sort);
CREATE INDEX IF NOT EXISTS idx_ppt_template_is_default          ON ppt_template(is_default);
COMMENT ON COLUMN ppt_template.template_file_path IS '上传的 pptx 文件相对路径，必须有值才在教师端可见';
COMMENT ON COLUMN ppt_template.render_config_json IS 'pptx 解析出的标记结构（TemplateStructure JSON）';


-- ============================================================
-- 10. ppt_document · PPT 工作区文档
--     每个 chat_session 一份，存教师生成 PPT 过程的中间态和最终输出
-- ============================================================
CREATE TABLE IF NOT EXISTS ppt_document (
    id                      BIGSERIAL PRIMARY KEY,
    session_id              BIGINT       NOT NULL,
    user_id                 BIGINT       NOT NULL,
    title                   VARCHAR(200),
    status                  VARCHAR(30)  NOT NULL DEFAULT 'preparing',
    summary                 VARCHAR(500),
    template_id             VARCHAR(100),
    template_name           VARCHAR(200),
    source_blueprint_json   TEXT,
    enriched_blueprint_json TEXT,
    planning_markdown       TEXT,
    plan_json               TEXT,
    slides_progress_json    TEXT,
    download_url            VARCHAR(500),
    export_file_path        VARCHAR(500),
    file_name               VARCHAR(255),
    error_message           TEXT,
    create_time             TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    update_time             TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    is_deleted              SMALLINT     DEFAULT 0,
    CONSTRAINT fk_ppt_document_session
        FOREIGN KEY (session_id) REFERENCES chat_session(id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_ppt_document_session_id ON ppt_document(session_id);
CREATE INDEX IF NOT EXISTS idx_ppt_document_user_id    ON ppt_document(user_id);
ALTER TABLE ppt_document
    ADD COLUMN IF NOT EXISTS slides_progress_json TEXT;


-- ============================================================
-- 11. lesson_plan_document · 教案工作区文档
-- ============================================================
CREATE TABLE IF NOT EXISTS lesson_plan_document (
    id                      BIGSERIAL PRIMARY KEY,
    session_id              BIGINT       NOT NULL,
    user_id                 BIGINT       NOT NULL,
    title                   VARCHAR(200),
    status                  VARCHAR(30)  NOT NULL DEFAULT 'preparing',
    summary                 VARCHAR(500),
    source_blueprint_json   TEXT,
    enriched_blueprint_json TEXT,
    content                 TEXT,
    preview                 TEXT,
    download_url            VARCHAR(500),
    export_file_path        VARCHAR(500),
    error_message           TEXT,
    create_time             TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    update_time             TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    is_deleted              SMALLINT     DEFAULT 0,
    CONSTRAINT fk_lesson_plan_document_session
        FOREIGN KEY (session_id) REFERENCES chat_session(id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_lesson_plan_document_session_id ON lesson_plan_document(session_id);
CREATE INDEX IF NOT EXISTS idx_lesson_plan_document_user_id    ON lesson_plan_document(user_id);


-- ============================================================
-- 12. interactive_document · 互动内容工作区文档
-- ============================================================
CREATE TABLE IF NOT EXISTS interactive_document (
    id                    BIGSERIAL PRIMARY KEY,
    session_id            BIGINT       NOT NULL,
    user_id               BIGINT       NOT NULL,
    title                 VARCHAR(200),
    status                VARCHAR(30)  NOT NULL DEFAULT 'preparing',
    summary               VARCHAR(500),
    source_context_json   TEXT,
    enriched_context_json TEXT,
    html_content          TEXT,
    download_url          VARCHAR(500),
    export_file_path      VARCHAR(500),
    error_message         TEXT,
    create_time           TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    update_time           TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    is_deleted            SMALLINT     DEFAULT 0,
    CONSTRAINT fk_interactive_document_session
        FOREIGN KEY (session_id) REFERENCES chat_session(id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_interactive_document_session_id ON interactive_document(session_id);
CREATE INDEX IF NOT EXISTS idx_interactive_document_user_id    ON interactive_document(user_id);


-- ============================================================
-- 完成提示
-- ============================================================
DO $$
BEGIN
    RAISE NOTICE '====================================================';
    RAISE NOTICE '  EduSpark schema 已就绪（12 张业务表 + 索引 + 触发器）';
    RAISE NOTICE '====================================================';
    RAISE NOTICE '提示：';
    RAISE NOTICE '  - 已自动 ALTER 补齐 3 个历史漂移列：';
    RAISE NOTICE '      chat_session.session_id';
    RAISE NOTICE '      chat_message.card_type / card_data';
    RAISE NOTICE '      knowledge_file.workspace_id';
    RAISE NOTICE '      ppt_template.template_file_path';
    RAISE NOTICE '  - 如需创建 admin 账户：';
    RAISE NOTICE '      UPDATE sys_user SET role=''admin'' WHERE phone=''<your_phone>'';';
    RAISE NOTICE '====================================================';
END $$;
