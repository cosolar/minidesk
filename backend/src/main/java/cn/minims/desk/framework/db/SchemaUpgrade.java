package cn.minims.desk.framework.db;

import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 字段增量升级。
 *
 * <p>建表脚本用 {@code IF NOT EXISTS} 保证幂等，但 {@code ALTER TABLE ADD COLUMN} 在 SQLite 上
 * 重复执行会报 duplicate column，因此不能放进 schema 脚本。这里在启动时按列是否存在决定是否执行，
 * 兼作「库结构自检」，缺列会补齐而不是让应用起不来。
 */
@Component
@Order(1)
public class SchemaUpgrade implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SchemaUpgrade.class);

    private final JdbcTemplate jdbcTemplate;

    public SchemaUpgrade(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!isSqlite()) {
            return;
        }
        ensureColumn("task", "start_date", "DATE");
        ensureColumn("task", "start_time", "VARCHAR(8)");
        ensureColumn("job_exec_log", "message", "VARCHAR(500)");
    }

    private boolean isSqlite() {
        try {
            jdbcTemplate.queryForObject("select sqlite_version()", String.class);
            return true;
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private void ensureColumn(String table, String column, String definition) {
        List<String> columns = new ArrayList<>();
        try {
            // 注意：带返回值的 lambda 会被解析成 ResultSetExtractor（只回调一次），
            // 必须自己 while(rs.next())，否则只能读到第一列 id。
            jdbcTemplate.query("pragma table_info(" + table + ")", rs -> {
                while (rs.next()) {
                    columns.add(rs.getString("name"));
                }
                return null;
            });
        } catch (RuntimeException ex) {
            log.warn("schema upgrade: read columns of {} failed, skip", table, ex);
            return;
        }
        if (columns.isEmpty() || columns.contains(column)) {
            return;
        }
        try {
            jdbcTemplate.execute("alter table " + table + " add column " + column + " " + definition);
            log.info("schema upgrade: {}.{} added", table, column);
        } catch (RuntimeException ex) {
            // 并发启动或列已存在：幂等，忽略即可
            log.debug("schema upgrade: {}.{} skipped ({})", table, column, ex.getMessage());
        }
    }
}
