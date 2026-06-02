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
 * Best-effort initializer for the interactive workspace schema so the feature
 * does not fail on environments where the SQL has not been applied yet.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "interactive.schema.auto-init", havingValue = "true", matchIfMissing = true)
public class InteractiveWorkspaceSchemaInitializer {

    private static final String TABLE_NAME = "interactive_document";

    private final JdbcTemplate jdbcTemplate;
    private final DataSource dataSource;

    public InteractiveWorkspaceSchemaInitializer(JdbcTemplate jdbcTemplate, DataSource dataSource) {
        this.jdbcTemplate = jdbcTemplate;
        this.dataSource = dataSource;
    }

    @PostConstruct
    public void initialize() {
        try {
            Integer count = jdbcTemplate.queryForObject(
                    """
                    SELECT COUNT(*)
                    FROM information_schema.tables
                    WHERE table_schema = current_schema()
                      AND table_name = ?
                    """,
                    Integer.class,
                    TABLE_NAME
            );
            if (count != null && count > 0) {
                return;
            }

            ResourceDatabasePopulator populator =
                    new ResourceDatabasePopulator(new ClassPathResource("sql/interactive_workspace.sql"));
            populator.setContinueOnError(false);
            populator.execute(dataSource);
            log.info("Initialized interactive workspace schema");
        } catch (Exception e) {
            log.error("Failed to initialize interactive workspace schema", e);
        }
    }
}
