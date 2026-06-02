package com.eduspark.eduspark.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;

@Slf4j
@Component
@ConditionalOnProperty(name = "ppt.template.schema.auto-init", havingValue = "true", matchIfMissing = true)
public class PptTemplateSchemaInitializer {

    private static final String TABLE_NAME = "ppt_template";

    private final JdbcTemplate jdbcTemplate;
    private final DataSource dataSource;

    public PptTemplateSchemaInitializer(JdbcTemplate jdbcTemplate, DataSource dataSource) {
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

            if (count == null || count == 0) {
                ResourceDatabasePopulator populator =
                        new ResourceDatabasePopulator(new ClassPathResource("sql/ppt_template.sql"));
                populator.setContinueOnError(false);
                populator.execute(dataSource);
                log.info("Initialized ppt template schema");
            } else {
                ensureRequiredColumns();
                log.info("Ppt template table exists, checked required columns");
            }
            // 历史上这里会调 seedBuiltinTemplatesIfEmpty() 灌入 32 套配色种子，让"模板选择器开箱可用"。
            // 但配色种子靠程序化渲染 + 配色字典出图，跟"pptx-as-template"心智冲突：
            //   - 老师选不同种子看到的版式一样，只有颜色不同；
            //   - render_config_json 同字段承载两种语义（标记结构 vs 配色字典）变成设计债。
            // 决策已统一走 pptx 模板路线 → 不再灌入种子。已有种子可执行 sql/cleanup_seed_templates.sql 清理。
        } catch (Exception e) {
            log.error("Failed to initialize ppt template schema", e);
        }
    }

    private void ensureRequiredColumns() {
        try {
            boolean hasColumn = checkColumnExists("template_file_path");
            if (!hasColumn) {
                log.info("Adding missing column template_file_path to ppt_template");
                jdbcTemplate.execute("ALTER TABLE ppt_template ADD COLUMN template_file_path VARCHAR(500)");
                log.info("Successfully added column template_file_path");
            }
        } catch (Exception e) {
            log.error("Failed to ensure required columns", e);
        }
    }

    private boolean checkColumnExists(String columnName) {
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData metaData = conn.getMetaData();
            try (ResultSet rs = metaData.getColumns(null, null, TABLE_NAME, columnName)) {
                return rs.next();
            }
        } catch (Exception e) {
            log.warn("Failed to check column existence: {}", columnName, e);
            return false;
        }
    }
}
