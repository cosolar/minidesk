package cn.minims.desk.framework.db;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.sql.DataSource;
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
 * <p>建表脚本用 {@code IF NOT EXISTS} 保证幂等，但老库可能缺后加的列；
 * 这里在启动时按 JDBC 元数据判断列是否存在，缺列补齐而不是让应用起不来。
 * 列探测走 {@link DatabaseMetaData}，SQLite 与 MySQL 通用，不再依赖 PRAGMA table_info。
 */
@Component
@Order(1)
public class SchemaUpgrade implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SchemaUpgrade.class);

    private final JdbcTemplate jdbcTemplate;
    private final DbDialect dialect;

    public SchemaUpgrade(JdbcTemplate jdbcTemplate, DbDialect dialect) {
        this.jdbcTemplate = jdbcTemplate;
        this.dialect = dialect;
    }

    @Override
    public void run(ApplicationArguments args) {
        ensureColumn("task", "start_date", "DATE");
        ensureColumn("task", "start_time", "VARCHAR(8)");
        ensureColumn("job_exec_log", "message", "VARCHAR(500)");
        ensureColumn("note", "deleted_at", "DATETIME");
        ensureColumn("note", "folder_id", "BIGINT");
        // 纪念日新增封面与「重要」标记，老库靠这里补齐
        ensureColumn("anniversary", "cover", "VARCHAR(500)");
        ensureColumn("anniversary", "important", "TINYINT DEFAULT 0");
        ensureIndex("note", "idx_note_recycle", "deleted, deleted_at");
        ensureIndex("note", "idx_note_folder", "folder_id");
        // 本次改动之前软删除的笔记没有时间戳，用最后修改时间兜底，否则永远不会被清理
        jdbcTemplate.update("update note set deleted_at = updated_at where deleted = 1 and deleted_at is null");
        migrateLegacyBookmarks();
    }

    /**
     * 索引必须在补列之后建：建表脚本执行时老库还没有 deleted_at，
     * 把 CREATE INDEX 写在 schema 里会让应用起不来。
     */
    private void ensureIndex(String table, String index, String columns) {
        DataSource dataSource = jdbcTemplate.getDataSource();
        if (dataSource == null) {
            return;
        }
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData meta = connection.getMetaData();
            try (ResultSet rs = meta.getIndexInfo(connection.getCatalog(), null, table, false, true)) {
                while (rs.next()) {
                    String name = rs.getString("INDEX_NAME");
                    if (name != null && name.equalsIgnoreCase(index)) {
                        return;
                    }
                }
            }
            jdbcTemplate.execute("create index " + dialect.quote(index)
                    + " on " + dialect.quote(table) + " (" + columns + ")");
            log.info("schema upgrade: index {} added", index);
        } catch (Exception ex) {
            log.debug("schema upgrade: index {} skipped ({})", index, ex.getMessage());
        }
    }

    /**
     * 旧「工作 → 书签」的 nav_link 行迁到新的网站导航表。
     * 用 sys_config 打一次性标记，避免用户清空分类后又被重新灌回来。
     * 这里走 JdbcTemplate 原生 SQL，不经过租户插件，才能跨用户搬运。
     */
    private void migrateLegacyBookmarks() {
        try {
            Integer marked = jdbcTemplate.queryForObject(
                    "select count(*) from sys_config where config_key = 'bookmark.migration.v1'",
                    Integer.class);
            if (marked != null && marked > 0) {
                return;
            }
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "select user_id, group_name, title, url, description, icon, click_count, sort_order "
                            + "from nav_link where module = 'work' and deleted = 0 "
                            + "order by user_id, group_name, sort_order, id");
            String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            Map<String, Long> categoryIds = new HashMap<>();
            int categorySort = 0;
            int siteSort = 0;
            String[] palette = {"#0B7D77", "#4C6FFF", "#8B7CF6", "#FF9F43", "#F25F5C", "#2AA6C9"};
            for (Map<String, Object> row : rows) {
                long userId = ((Number) row.get("user_id")).longValue();
                String group = row.get("group_name") == null ? "常用" : String.valueOf(row.get("group_name"));
                String key = userId + "/" + group;
                Long categoryId = categoryIds.get(key);
                if (categoryId == null) {
                    jdbcTemplate.update("insert into bookmark_category "
                                    + "(user_id, name, icon, color, sort_order, created_at, updated_at, deleted) "
                                    + "values (?,?,?,?,?,?,?,0)",
                            userId, group, "folder", palette[Math.abs(key.hashCode()) % palette.length],
                            categorySort++, now, now);
                    categoryId = jdbcTemplate.queryForObject(
                            "select id from bookmark_category where user_id = ? and name = ? order by id desc limit 1",
                            Long.class, userId, group);
                    categoryIds.put(key, categoryId);
                }
                Object clicks = row.get("click_count");
                jdbcTemplate.update("insert into bookmark_site "
                                + "(user_id, category_id, name, url, description, icon, color, click_count, "
                                + "pinned, sort_order, created_at, updated_at, deleted) "
                                + "values (?,?,?,?,?,?,?,?,0,?,?,?,0)",
                        userId, categoryId, row.get("title"), row.get("url"), row.get("description"),
                        row.get("icon"), palette[Math.abs(key.hashCode()) % palette.length],
                        clicks == null ? 0 : ((Number) clicks).intValue(), siteSort++, now, now);
            }
            jdbcTemplate.update(dialect.insertIgnore("sys_config",
                            "(config_key, config_value, remark, updated_at)")
                    + " values ('bookmark.migration.v1', 'done', '旧工作书签已迁移', ?)", now);
            if (!rows.isEmpty()) {
                log.info("schema upgrade: migrated {} legacy work bookmarks", rows.size());
            }
        } catch (RuntimeException ex) {
            // 老库缺表/字段时不影响启动，新功能仍可用
            log.warn("schema upgrade: legacy bookmark migration skipped ({})", ex.getMessage());
        }
    }

    /** 用 JDBC 元数据判列，避免 SQLite / MySQL 各自的 information_schema 差异。 */
    private boolean hasColumn(String table, String column) {
        DataSource dataSource = jdbcTemplate.getDataSource();
        if (dataSource == null) {
            return false;
        }
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData meta = connection.getMetaData();
            try (ResultSet rs = meta.getColumns(connection.getCatalog(), null, table, column)) {
                while (rs.next()) {
                    if (column.equalsIgnoreCase(rs.getString("COLUMN_NAME"))
                            && table.equalsIgnoreCase(rs.getString("TABLE_NAME"))) {
                        return true;
                    }
                }
            }
            // 少数驱动对 getColumns 的大小写敏感，兜底再扫一遍该表全部列
            try (ResultSet rs = meta.getColumns(connection.getCatalog(), null, table, null)) {
                while (rs.next()) {
                    if (column.equalsIgnoreCase(rs.getString("COLUMN_NAME"))) {
                        return true;
                    }
                }
            }
            return false;
        } catch (Exception ex) {
            log.warn("schema upgrade: read columns of {} failed, skip ({})",
                    table.toLowerCase(Locale.ROOT), ex.getMessage());
            return false;
        }
    }

    private void ensureColumn(String table, String column, String definition) {
        if (hasColumn(table, column)) {
            return;
        }
        try {
            jdbcTemplate.execute("alter table " + dialect.quote(table)
                    + " add column " + dialect.quote(column) + " " + definition);
            log.info("schema upgrade: {}.{} added", table, column);
        } catch (RuntimeException ex) {
            // 并发启动或列已存在：幂等，忽略即可
            log.debug("schema upgrade: {}.{} skipped ({})", table, column, ex.getMessage());
        }
    }
}
