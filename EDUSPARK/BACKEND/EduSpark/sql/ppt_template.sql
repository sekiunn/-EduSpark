CREATE TABLE IF NOT EXISTS ppt_template_scene (
    id          BIGSERIAL PRIMARY KEY,
    scene_code  VARCHAR(100) NOT NULL,
    scene_name  VARCHAR(100) NOT NULL,
    sort        INTEGER NOT NULL DEFAULT 0,
    enabled     SMALLINT NOT NULL DEFAULT 1,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    is_deleted  SMALLINT DEFAULT 0,
    CONSTRAINT uk_ppt_template_scene_code UNIQUE (scene_code),
    CONSTRAINT uk_ppt_template_scene_name UNIQUE (scene_name)
);

CREATE TABLE IF NOT EXISTS ppt_template_style (
    id          BIGSERIAL PRIMARY KEY,
    scene_id    BIGINT NOT NULL,
    style_code  VARCHAR(100) NOT NULL,
    style_name  VARCHAR(100) NOT NULL,
    sort        INTEGER NOT NULL DEFAULT 0,
    enabled     SMALLINT NOT NULL DEFAULT 1,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    is_deleted  SMALLINT DEFAULT 0,
    CONSTRAINT fk_ppt_template_style_scene
        FOREIGN KEY (scene_id) REFERENCES ppt_template_scene(id),
    CONSTRAINT uk_ppt_template_style_scene_code UNIQUE (scene_id, style_code),
    CONSTRAINT uk_ppt_template_style_scene_name UNIQUE (scene_id, style_name)
);

CREATE TABLE IF NOT EXISTS ppt_template (
    id                    BIGSERIAL PRIMARY KEY,
    template_code         VARCHAR(100) NOT NULL,
    name                  VARCHAR(120) NOT NULL,
    scene_id              BIGINT NOT NULL,
    style_id              BIGINT NOT NULL,
    cover_url             VARCHAR(500),
    preview_images_json   TEXT,
    description           VARCHAR(500),
    engine_template_key   VARCHAR(100) NOT NULL,
    prompt_hint           TEXT,
    blueprint_config_json TEXT,
    render_config_json    TEXT,
    enabled               SMALLINT NOT NULL DEFAULT 1,
    is_default            SMALLINT NOT NULL DEFAULT 0,
    sort                  INTEGER NOT NULL DEFAULT 0,
    version               INTEGER NOT NULL DEFAULT 1,
    create_time           TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time           TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    is_deleted            SMALLINT DEFAULT 0,
    CONSTRAINT fk_ppt_template_scene
        FOREIGN KEY (scene_id) REFERENCES ppt_template_scene(id),
    CONSTRAINT fk_ppt_template_style
        FOREIGN KEY (style_id) REFERENCES ppt_template_style(id),
    CONSTRAINT uk_ppt_template_code UNIQUE (template_code)
);

CREATE INDEX IF NOT EXISTS idx_ppt_template_scene_enabled_sort
    ON ppt_template_scene(enabled, sort);

CREATE INDEX IF NOT EXISTS idx_ppt_template_style_scene_id
    ON ppt_template_style(scene_id);

CREATE INDEX IF NOT EXISTS idx_ppt_template_style_scene_enabled_sort
    ON ppt_template_style(scene_id, enabled, sort);

CREATE INDEX IF NOT EXISTS idx_ppt_template_scene_id
    ON ppt_template(scene_id);

CREATE INDEX IF NOT EXISTS idx_ppt_template_style_id
    ON ppt_template(style_id);

CREATE INDEX IF NOT EXISTS idx_ppt_template_scene_style
    ON ppt_template(scene_id, style_id);

CREATE INDEX IF NOT EXISTS idx_ppt_template_engine_template_key
    ON ppt_template(engine_template_key);

CREATE INDEX IF NOT EXISTS idx_ppt_template_enabled_sort
    ON ppt_template(enabled, sort);

CREATE INDEX IF NOT EXISTS idx_ppt_template_is_default
    ON ppt_template(is_default);
