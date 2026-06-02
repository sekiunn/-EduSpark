CREATE TABLE IF NOT EXISTS ppt_document (
    id                      BIGSERIAL PRIMARY KEY,
    session_id              BIGINT NOT NULL,
    user_id                 BIGINT NOT NULL,
    title                   VARCHAR(200),
    status                  VARCHAR(30) NOT NULL DEFAULT 'preparing',
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
    create_time             TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time             TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    is_deleted              SMALLINT DEFAULT 0,
    CONSTRAINT fk_ppt_document_session
        FOREIGN KEY (session_id) REFERENCES chat_session(id) ON DELETE CASCADE
);

-- 老库补漂移：早期版本没有 slides_progress_json 列
ALTER TABLE ppt_document ADD COLUMN IF NOT EXISTS slides_progress_json TEXT;

CREATE INDEX IF NOT EXISTS idx_ppt_document_session_id
    ON ppt_document(session_id);

CREATE INDEX IF NOT EXISTS idx_ppt_document_user_id
    ON ppt_document(user_id);
