package com.vernflow.common.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;

/**
 * Patches legacy parse_task tables so local persisted databases remain compatible
 * with the latest queue management fields.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ParseTaskSchemaCompatibilityInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ParseTaskSchemaCompatibilityInitializer.class);

    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;

    public ParseTaskSchemaCompatibilityInitializer(DataSource dataSource) {
        this.dataSource = dataSource;
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    @Override
    public void run(ApplicationArguments args) {
        ensureLegacyParseTaskColumns();
    }

    void ensureLegacyParseTaskColumns() {
        try (Connection connection = dataSource.getConnection()) {
            if (!isH2Database(connection)) {
                return;
            }

            if (!tableExists(connection, "parse_task")) {
                return;
            }

            boolean patched = false;
            if (!columnExists(connection, "parse_task", "queue_order")) {
                jdbcTemplate.execute("alter table parse_task add column queue_order bigint");
                patched = true;
            }

            if (!columnExists(connection, "parse_task", "started_at")) {
                jdbcTemplate.execute("alter table parse_task add column started_at timestamp");
                patched = true;
            }

            if (!columnExists(connection, "parse_task", "finished_at")) {
                jdbcTemplate.execute("alter table parse_task add column finished_at timestamp");
                patched = true;
            }

            if (!columnExists(connection, "parse_task", "updated_at")) {
                jdbcTemplate.execute("alter table parse_task add column updated_at timestamp");
                patched = true;
            }

            jdbcTemplate.update("update parse_task set queue_order = id where queue_order is null");
            jdbcTemplate.update("""
                    update parse_task
                    set updated_at = coalesce(updated_at, finished_at, started_at, created_at, current_timestamp)
                    where updated_at is null
                    """);
            jdbcTemplate.execute("alter table parse_task alter column queue_order set not null");
            jdbcTemplate.execute("alter table parse_task alter column updated_at set not null");
            patched |= ensureStatusColumnAllowsPaused(connection);
            patched |= ensureStatusConstraintAllowsPaused();

            if (patched) {
                log.info("Applied legacy parse_task schema compatibility patch.");
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to patch legacy parse_task schema", exception);
        }
    }

    private boolean isH2Database(Connection connection) throws SQLException {
        String databaseProductName = connection.getMetaData().getDatabaseProductName();
        return databaseProductName != null && databaseProductName.toUpperCase(Locale.ROOT).contains("H2");
    }

    private boolean ensureStatusColumnAllowsPaused(Connection connection) throws SQLException {
        String typeName = columnTypeName(connection, "parse_task", "status");
        if (typeName == null) {
            return false;
        }

        String normalizedType = normalize(typeName);
        if (!normalizedType.startsWith("ENUM(") || normalizedType.contains("'PAUSED'")) {
            return false;
        }

        jdbcTemplate.execute("""
                alter table parse_task
                alter column status enum('FAILED', 'PENDING', 'PROCESSING', 'PAUSED', 'SUCCESS')
                """);
        return true;
    }

    private boolean ensureStatusConstraintAllowsPaused() {
        List<String> legacyConstraintNames = jdbcTemplate.query(
                """
                        select tc.constraint_name
                        from information_schema.table_constraints tc
                        join information_schema.check_constraints cc
                          on tc.constraint_catalog = cc.constraint_catalog
                         and tc.constraint_schema = cc.constraint_schema
                         and tc.constraint_name = cc.constraint_name
                        where upper(tc.table_name) = 'PARSE_TASK'
                          and upper(tc.constraint_type) = 'CHECK'
                          and upper(cc.check_clause) like '%STATUS%'
                          and upper(cc.check_clause) not like '%''PAUSED''%'
                        """,
                (resultSet, rowNum) -> resultSet.getString(1)
        );

        if (legacyConstraintNames.isEmpty()) {
            return false;
        }

        for (String constraintName : legacyConstraintNames) {
            jdbcTemplate.execute("alter table parse_task drop constraint " + constraintName);
        }

        jdbcTemplate.execute("""
                alter table parse_task
                add constraint parse_task_status_check
                check (status in ('PENDING', 'PROCESSING', 'PAUSED', 'SUCCESS', 'FAILED'))
                """);
        return true;
    }

    private String columnTypeName(Connection connection, String tableName, String columnName) throws SQLException {
        DatabaseMetaData metaData = connection.getMetaData();
        try (ResultSet resultSet = metaData.getColumns(connection.getCatalog(), null, tableName, columnName)) {
            if (resultSet.next()) {
                return resultSet.getString("TYPE_NAME");
            }
        }
        try (ResultSet resultSet = metaData.getColumns(connection.getCatalog(), null, normalize(tableName), normalize(columnName))) {
            if (resultSet.next()) {
                return resultSet.getString("TYPE_NAME");
            }
        }
        return null;
    }

    private boolean tableExists(Connection connection, String tableName) throws SQLException {
        DatabaseMetaData metaData = connection.getMetaData();
        return objectExists(metaData.getTables(connection.getCatalog(), null, tableName, new String[]{"TABLE"}))
                || objectExists(metaData.getTables(connection.getCatalog(), null, normalize(tableName), new String[]{"TABLE"}));
    }

    private boolean columnExists(Connection connection, String tableName, String columnName) throws SQLException {
        DatabaseMetaData metaData = connection.getMetaData();
        return objectExists(metaData.getColumns(connection.getCatalog(), null, tableName, columnName))
                || objectExists(metaData.getColumns(connection.getCatalog(), null, normalize(tableName), normalize(columnName)));
    }

    private boolean objectExists(ResultSet resultSet) throws SQLException {
        try (ResultSet rs = resultSet) {
            return rs.next();
        }
    }

    private String normalize(String identifier) {
        return identifier.toUpperCase(Locale.ROOT);
    }
}
