package cn.minims.desk.framework.db;

import com.baomidou.mybatisplus.annotation.DbType;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 运行时数据库方言。
 *
 * <p>从 JDBC 的 {@link DatabaseMetaData} 判定，而不是读配置文件：这样只改数据源 URL
 * 也能得到正确的分页方言、幂等插入写法与日期函数。SQLite 仍是本地开发默认，
 * MySQL 是生产库，PostgreSQL 只保留最小兼容。
 *
 * <p>所有返回 SQL 片段的方法只拼接本类内部的常量或调用方传入的标识符，不接收用户输入。
 */
@Component
public class DbDialect {

    private static final Logger log = LoggerFactory.getLogger(DbDialect.class);

    public enum Kind {
        SQLITE, MYSQL, POSTGRESQL, OTHER
    }

    private final Kind kind;
    private final String product;

    public DbDialect(DataSource dataSource) {
        Kind detected = Kind.OTHER;
        String name = "unknown";
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData meta = connection.getMetaData();
            name = meta.getDatabaseProductName();
            String lower = name == null ? "" : name.toLowerCase(Locale.ROOT);
            if (lower.contains("sqlite")) {
                detected = Kind.SQLITE;
            } else if (lower.contains("mysql") || lower.contains("mariadb")) {
                detected = Kind.MYSQL;
            } else if (lower.contains("postgresql")) {
                detected = Kind.POSTGRESQL;
            }
        } catch (SQLException ex) {
            log.warn("cannot read database metadata, falling back to the sqlite dialect: {}", ex.getMessage());
            detected = Kind.SQLITE;
        }
        this.kind = detected;
        this.product = name;
        log.info("database dialect: {} ({})", kind, product);
    }

    public Kind kind() {
        return kind;
    }

    public String product() {
        return product;
    }

    public boolean isSqlite() {
        return kind == Kind.SQLITE;
    }

    public boolean isMySql() {
        return kind == Kind.MYSQL;
    }

    public DbType dbType() {
        return switch (kind) {
            case MYSQL -> DbType.MYSQL;
            case POSTGRESQL -> DbType.POSTGRE_SQL;
            default -> DbType.SQLITE;
        };
    }

    /**
     * 幂等插入前缀，调用方再拼 {@code values (...)}。
     * 依赖表上的唯一键：sys_role.code / sys_config.config_key / sys_schedule_job.job_code
     * 与 SQLite 的 INSERT OR IGNORE 语义一致。
     */
    public String insertIgnore(String table, String columns) {
        return switch (kind) {
            case MYSQL -> "insert ignore into " + table + " " + columns;
            case POSTGRESQL -> "insert into " + table + " " + columns + " on conflict do nothing";
            default -> "insert or ignore into " + table + " " + columns;
        };
    }

    /** N 天前的时间表达式，用于「最近 7 天活跃」一类统计。 */
    public String daysAgo(int days) {
        return switch (kind) {
            case MYSQL -> "date_sub(now(), interval " + days + " day)";
            case POSTGRESQL -> "now() - interval '" + days + " days'";
            default -> "datetime('now','-" + days + " days')";
        };
    }

    /** 今天的日期表达式。 */
    public String today() {
        return switch (kind) {
            case MYSQL -> "curdate()";
            case POSTGRESQL -> "current_date";
            default -> "date('now')";
        };
    }

    /** MySQL 的 {@code ||} 是逻辑或，拼接必须走 concat()。 */
    public String concat(String... expressions) {
        if (kind == Kind.MYSQL) {
            return "concat(" + String.join(", ", Arrays.asList(expressions)) + ")";
        }
        return Arrays.stream(expressions).collect(Collectors.joining(" || "));
    }

    /** 标识符引用：MySQL 用反引号，PG 用双引号，SQLite 裸写即可。 */
    public String quote(String identifier) {
        return switch (kind) {
            case MYSQL -> "`" + identifier + "`";
            case POSTGRESQL -> "\"" + identifier + "\"";
            default -> identifier;
        };
    }

    /** 库占用空间（MB）。SQLite 返回 null，由调用方读文件大小。 */
    public String databaseSizeMbSql() {
        if (kind != Kind.MYSQL) {
            return null;
        }
        return "select round(coalesce(sum(data_length + index_length), 0) / 1048576, 1) "
                + "from information_schema.tables where table_schema = database()";
    }

    /** 当前库里的业务表清单，逻辑备份用。 */
    public String baseTablesSql() {
        if (kind == Kind.MYSQL) {
            return "select table_name from information_schema.tables "
                    + "where table_schema = database() and table_type = 'BASE TABLE' order by table_name";
        }
        return "select name from sqlite_master where type = 'table' and name not like 'sqlite_%' order by name";
    }
}
