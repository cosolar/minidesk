package cn.minims.desk.modules.system.backup;

import cn.minims.desk.framework.db.DbDialect;
import cn.minims.desk.framework.db.LogicalDatabaseBackup;
import cn.minims.desk.modules.system.backup.service.BackupService;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 外部数据库（MySQL）的恢复引导。
 *
 * <p>{@link RestoreBootstrap} 在 Spring 启动前没有 DataSource，只能处理 SQLite 文件替换；
 * MySQL 的 pending 包留到这里，在建表脚本执行完毕、连接池就绪后按逻辑快照写回。
 * 恢复包里的 database.jsonl 由 {@link LogicalDatabaseBackup} 校验表名/列名并整体事务化。
 */
@Component
@Order(0)
public class MysqlRestoreRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(MysqlRestoreRunner.class);
    private static final String PENDING_NAME = "restore-pending.zip";

    private final DbDialect dialect;
    private final LogicalDatabaseBackup logicalBackup;

    public MysqlRestoreRunner(DbDialect dialect, LogicalDatabaseBackup logicalBackup) {
        this.dialect = dialect;
        this.logicalBackup = logicalBackup;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (dialect.isSqlite()) {
            return;
        }
        Path root = RestoreBootstrap.dataRoot();
        Path pending = root.resolve(PENDING_NAME);
        if (!Files.isRegularFile(pending)) {
            return;
        }
        Path work = root.resolve(".restore-tmp");
        try {
            RestoreBootstrap.deleteTree(work);
            Files.createDirectories(work);
            RestoreBootstrap.unpack(pending, work);
            String jsonl = Files.readString(work.resolve("database.jsonl"), StandardCharsets.UTF_8);
            int tables = logicalBackup.restore(jsonl);
            RestoreBootstrap.overlay(work.resolve("uploads"), root.resolve("uploads"));
            RestoreBootstrap.overlay(work.resolve("branding"), root.resolve("branding"));
            Path secret = work.resolve(".secret");
            if (Files.isRegularFile(secret)) {
                Files.copy(secret, root.resolve(".secret"), StandardCopyOption.REPLACE_EXISTING);
            }
            Files.deleteIfExists(pending);
            String message = "restored " + pending.getFileName() + " (" + tables + " tables)";
            BackupService.finishRestore(root, true, message);
            log.info("mysql restore: {}", message);
        } catch (Exception ex) {
            String reason = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
            try {
                Files.deleteIfExists(pending);
            } catch (IOException ignored) {
                log.debug("remove failed restore package failed", ignored);
            }
            BackupService.finishRestore(root, false, reason);
            log.error("mysql restore failed, current data kept", ex);
        } finally {
            RestoreBootstrap.deleteTree(work);
        }
    }
}
