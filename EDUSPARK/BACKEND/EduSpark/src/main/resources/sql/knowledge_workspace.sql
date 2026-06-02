-- ============================================================
-- 知识库 · 课程空间表（每位老师可创建多个课程，分别上传该课程的资料）
-- knowledge_file 表追加 workspace_id 外键，原 category 字段保留为可选子标签。
-- 由 KnowledgeWorkspaceSchemaInitializer 在启动时按需执行（建表 + 列补齐）。
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

-- knowledge_file 增加 workspace_id（旧记录为 NULL，表示尚未归类）
ALTER TABLE knowledge_file
    ADD COLUMN IF NOT EXISTS workspace_id BIGINT;

CREATE INDEX IF NOT EXISTS idx_file_workspace ON knowledge_file(workspace_id);
CREATE INDEX IF NOT EXISTS idx_file_user_workspace
    ON knowledge_file(user_id, workspace_id);

COMMENT ON TABLE knowledge_workspace IS '知识库课程空间：教师按课程隔离上传的教学资料';
COMMENT ON COLUMN knowledge_file.workspace_id IS '所属课程空间 ID，NULL 表示未归类';
