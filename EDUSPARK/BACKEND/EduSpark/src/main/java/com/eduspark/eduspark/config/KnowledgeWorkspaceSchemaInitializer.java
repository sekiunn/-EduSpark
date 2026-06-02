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
 * 启动时按需建表 / 给 knowledge_file 补 workspace_id 列。
 * 全脚本使用 IF NOT EXISTS / ADD COLUMN IF NOT EXISTS，重复执行幂等。
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "knowledge.workspace.schema.auto-init", havingValue = "true", matchIfMissing = true)
public class KnowledgeWorkspaceSchemaInitializer {

    private final JdbcTemplate jdbcTemplate;
    private final DataSource dataSource;

    public KnowledgeWorkspaceSchemaInitializer(JdbcTemplate jdbcTemplate, DataSource dataSource) {
        this.jdbcTemplate = jdbcTemplate;
        this.dataSource = dataSource;
    }

    @PostConstruct
    public void initialize() {
        try {
            // 即便表已存在，也允许脚本跑一遍 ALTER ADD COLUMN IF NOT EXISTS，方便老库平滑升级
            ResourceDatabasePopulator populator =
                    new ResourceDatabasePopulator(new ClassPathResource("sql/knowledge_workspace.sql"));
            populator.setContinueOnError(false);
            populator.execute(dataSource);
            log.info("Knowledge workspace schema ensured");
        } catch (Exception e) {
            log.error("Failed to initialize knowledge workspace schema", e);
        }
        // 防御性：即使 populator 出错（脚本被破坏等），也尝试补列
        ensureWorkspaceColumn();
    }

    private void ensureWorkspaceColumn() {
        try {
            Integer columnCount = jdbcTemplate.queryForObject(
                    """
                    SELECT COUNT(*)
                    FROM information_schema.columns
                    WHERE table_schema = current_schema()
                      AND table_name = 'knowledge_file'
                      AND column_name = 'workspace_id'
                    """,
                    Integer.class
            );
            if (columnCount == null || columnCount == 0) {
                jdbcTemplate.execute("ALTER TABLE knowledge_file ADD COLUMN workspace_id BIGINT");
                log.info("Added workspace_id column to knowledge_file");
            }
        } catch (Exception e) {
            log.warn("Failed to ensure knowledge_file.workspace_id column", e);
        }
    }
}
