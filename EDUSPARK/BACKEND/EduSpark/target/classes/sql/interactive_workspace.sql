CREATE TABLE IF NOT EXISTS interactive_document (
    id                    BIGSERIAL PRIMARY KEY,
    session_id            BIGINT NOT NULL,
    user_id               BIGINT NOT NULL,
    title                 VARCHAR(200),
    status                VARCHAR(30) NOT NULL DEFAULT 'preparing',
    summary               VARCHAR(500),
    source_context_json   TEXT,
    enriched_context_json TEXT,
    html_content          TEXT,
    download_url          VARCHAR(500),
    export_file_path      VARCHAR(500),
    error_message         TEXT,
    create_time           TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time           TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    is_deleted            SMALLINT DEFAULT 0,
    CONSTRAINT fk_interactive_document_session
        FOREIGN KEY (session_id) REFERENCES chat_session(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_interactive_document_session_id
    ON interactive_document(session_id);

CREATE INDEX IF NOT EXISTS idx_interactive_document_user_id
    ON interactive_document(user_id);
