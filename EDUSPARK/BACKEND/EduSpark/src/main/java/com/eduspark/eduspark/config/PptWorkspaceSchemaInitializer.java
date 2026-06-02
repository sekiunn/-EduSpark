package com.eduspark.eduspark.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

/**
 * 启动时按需建 ppt_document 表 / 给老库补漂移列。
 * 全脚本使用 IF NOT EXISTS / ADD COLUMN IF NOT EXISTS，重复执行幂等。
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "ppt.workspace.schema.auto-init", havingValue = "true", matchIfMissing = true)
public class PptWorkspaceSchemaInitializer {

    private final JdbcTemplate jdbcTemplate;
    private final DataSource dataSource;

    public PptWorkspaceSchemaInitializer(JdbcTemplate jdbcTemplate, DataSource dataSource) {
        this.jdbcTemplate = jdbcTemplate;
        this.dataSource = dataSource;
    }

    @PostConstruct
    public void initialize() {
        try {
            // 即便表已存在，也允许脚本跑一遍 ALTER ADD COLUMN IF NOT EXISTS，方便老库平滑升级
            ResourceDatabasePopulator populator =
                    new ResourceDatabasePopulator(new ClassPathResource("sql/ppt_workspace.sql"));
            populator.setContinueOnError(false);
            populator.execute(dataSource);
            log.info("PPT workspace schema ensured");
        } catch (Exception e) {
            log.error("Failed to initialize PPT workspace schema", e);
        }
        // 防御性：即使 populator 出错（脚本被破坏等），也尝试补列
        ensureSlidesProgressColumn();
    }

    private void ensureSlidesProgressColumn() {
        try {
            Integer columnCount = jdbcTemplate.queryForObject(
                    """
                    SELECT COUNT(*)
                    FROM information_schema.columns
                    WHERE table_schema = current_schema()
                      AND table_name = 'ppt_document'
                      AND column_name = 'slides_progress_json'
                    """,
                    Integer.class
            );
            if (columnCount == null || columnCount == 0) {
                jdbcTemplate.execute("ALTER TABLE ppt_document ADD COLUMN slides_progress_json TEXT");
                log.info("Added slides_progress_json column to ppt_document");
            }
        } catch (Exception e) {
            log.warn("Failed to ensure ppt_document.slides_progress_json column", e);
        }
    }
}
