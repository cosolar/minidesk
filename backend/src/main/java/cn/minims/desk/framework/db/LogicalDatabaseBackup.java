package cn.minims.desk.framework.db;

import cn.minims.desk.common.api.ErrorCode;
import cn.minims.desk.common.exception.BizException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.PreparedStatement;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 外部数据库（MySQL）的逻辑备份与恢复。
 *
 * <p>SQLite 用 {@code VACUUM INTO} 直接复制文件；MySQL 没有等价的在线文件快照，
 * 改成按表导出 JSONL（每行一张表：表名 / 列名 / 全部行值），恢复时用 PreparedStatement
 * 批量写回。值全部以字符串承载，交给 JDBC 做类型转换，避免手写 SQL 字面量转义；
 * 表名与列名在恢复前用数据库元数据做白名单校验，防止被篡改的备份包注入 SQL。
 *
 * <p>恢复整体放在一个事务里：任何一张表失败都会回滚到恢复前的数据。
 */
@Component
public class LogicalDatabaseBackup {

    /** 二进制列在 JSONL 里的标记；当前 64 张表均为文本/数值/日期，但保留兼容。 */
    private static final String BINARY_PREFIX = "__b64__:";

    public record TableDump(String table, List<String> columns, List<List<String>> rows) {
    }

    private final JdbcTemplate jdbc;
    private final DbDialect dialect;
    private final PlatformTransactionManager transactionManager;
    private final ObjectMapper mapper = new ObjectMapper();

    public LogicalDatabaseBackup(JdbcTemplate jdbc, DbDialect dialect,
                                 PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.dialect = dialect;
        this.transactionManager = transactionManager;
    }

    /** 导出为 JSONL 文本，一行一张表。 */
    public String dump() {
        List<String> tables = jdbc.queryForList(dialect.baseTablesSql(), String.class);
        StringBuilder out = new StringBuilder();
        for (String table : tables) {
            TableDump data = jdbc.query("select * from " + dialect.quote(table), rs -> {
                ResultSetMetaData meta = rs.getMetaData();
                List<String> columns = new ArrayList<>();
                for (int i = 1; i <= meta.getColumnCount(); i++) {
                    columns.add(meta.getColumnLabel(i));
                }
                List<List<String>> rows = new ArrayList<>();
                while (rs.next()) {
                    List<String> row = new ArrayList<>(columns.size());
                    for (int i = 1; i <= meta.getColumnCount(); i++) {
                        Object value = rs.getObject(i);
                        if (value == null) {
                            row.add(null);
                        } else if (value instanceof byte[] bytes) {
                            row.add(BINARY_PREFIX + Base64.getEncoder().encodeToString(bytes));
                        } else {
                            row.add(rs.getString(i));
                        }
                    }
                    rows.add(row);
                }
                return new TableDump(table, columns, rows);
            });
            if (data == null) {
                continue;
            }
            try {
                out.append(mapper.writeValueAsString(data)).append('\n');
            } catch (Exception ex) {
                throw new BizException(ErrorCode.BACKUP_FAILED, "备份序列化失败：" + ex.getMessage(), ex);
            }
        }
        return out.toString();
    }

    /** 从 JSONL 恢复，返回处理的表数。 */
    public int restore(String jsonl) {
        List<TableDump> dumps = new ArrayList<>();
        for (String line : jsonl.split("\\R")) {
            if (line.isBlank()) {
                continue;
            }
            try {
                dumps.add(mapper.readValue(line, TableDump.class));
            } catch (Exception ex) {
                throw new BizException(ErrorCode.RESTORE_INVALID, "恢复包里的 database.jsonl 无法解析", ex);
            }
        }
        if (dumps.isEmpty()) {
            throw new BizException(ErrorCode.RESTORE_INVALID, "恢复包里的 database.jsonl 为空");
        }
        Set<String> tables = new HashSet<>(jdbc.queryForList(dialect.baseTablesSql(), String.class));
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.executeWithoutResult(status -> {
            for (TableDump data : dumps) {
                if (data.table() == null || !tables.contains(data.table())) {
                    throw new BizException(ErrorCode.RESTORE_INVALID, "恢复包包含未知数据表：" + data.table());
                }
                restoreTable(data);
            }
        });
        return dumps.size();
    }

    private void restoreTable(TableDump data) {
        String quotedTable = dialect.quote(data.table());
        Set<String> validColumns = new LinkedHashSet<>();
        jdbc.query("select * from " + quotedTable + " where 1 = 0", rs -> {
            ResultSetMetaData meta = rs.getMetaData();
            for (int i = 1; i <= meta.getColumnCount(); i++) {
                validColumns.add(meta.getColumnLabel(i));
            }
            return null;
        });
        List<String> columns = new ArrayList<>();
        List<Integer> sourceIndexes = new ArrayList<>();
        for (int i = 0; i < data.columns().size(); i++) {
            String column = data.columns().get(i);
            if (column != null && validColumns.contains(column)) {
                columns.add(column);
                sourceIndexes.add(i);
            }
        }
        if (columns.isEmpty()) {
            throw new BizException(ErrorCode.RESTORE_INVALID, "恢复包里的表结构与当前库不匹配：" + data.table());
        }
        String columnSql = String.join(", ", columns.stream().map(dialect::quote).toList());
        String placeholders = String.join(", ", columns.stream().map(c -> "?").toList());
        String insertSql = "insert into " + quotedTable + " (" + columnSql + ") values (" + placeholders + ")";
        jdbc.execute("delete from " + quotedTable);
        List<List<String>> rows = data.rows() == null ? List.of() : data.rows();
        jdbc.batchUpdate(insertSql, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int index) throws SQLException {
                List<String> row = rows.get(index);
                for (int i = 0; i < sourceIndexes.size(); i++) {
                    int source = sourceIndexes.get(i);
                    String value = source < row.size() ? row.get(source) : null;
                    if (value == null) {
                        ps.setNull(i + 1, Types.VARCHAR);
                    } else if (value.startsWith(BINARY_PREFIX)) {
                        ps.setBytes(i + 1, Base64.getDecoder().decode(value.substring(BINARY_PREFIX.length())));
                    } else {
                        ps.setString(i + 1, value);
                    }
                }
            }

            @Override
            public int getBatchSize() {
                return rows.size();
            }
        });
    }
}
