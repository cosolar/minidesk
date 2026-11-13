package cn.minims.desk.modules.system.backup;

import cn.minims.desk.modules.system.backup.service.BackupService;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 启动前的恢复引导。
 *
 * <p>必须在 SpringApplication.run 之前调用：连接池一旦建立就持有旧数据库句柄，
 * 运行期替换文件会导致连接与文件不一致。这里只做纯文件操作，不依赖 Spring 容器。
 *
 * <p>安全策略：ZIP 路径穿越与解压总量限制、SQLite 文件头与核心表校验、
 * 替换前先把当前库另存一份、任何一步失败都回滚到原库。
 */
public final class RestoreBootstrap {

    private static final String PENDING_NAME = "restore-pending.zip";
    private static final long MAX_UNPACKED_BYTES = 1024L * 1024 * 1024;
    private static final int MAX_ENTRIES = 10_000;

    private RestoreBootstrap() {
    }

    public static void applyIfPending() {
        Path root = dataRoot();
        Path pending = root.resolve(PENDING_NAME);
        if (!Files.isRegularFile(pending)) {
            return;
        }
        System.out.println("[MiniDesk] restore package detected, applying before startup");
        try {
            String message = apply(root, pending);
            Files.deleteIfExists(pending);
            BackupService.finishRestore(root, true, message);
            System.out.println("[MiniDesk] " + message);
        } catch (Exception ex) {
            try {
                Files.deleteIfExists(pending);
            } catch (IOException ignored) {
                // 下次启动仍会读 restore-last.json 里的失败原因
            }
            String reason = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
            BackupService.finishRestore(root, false, reason);
            System.err.println("[MiniDesk] restore failed, original database kept: " + reason);
        }
    }

    private static String apply(Path root, Path pending) throws IOException, SQLException {
        Path work = root.resolve(".restore-tmp");
        deleteTree(work);
        Files.createDirectories(work);
        try {
            unpack(pending, work);
            Path newDb = work.resolve("minidesk.db");
            validateDatabase(newDb);

            Path current = root.resolve("minidesk.db");
            String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
            Path safety = root.resolve("minidesk.pre-restore-" + stamp + ".db");
            boolean hadCurrent = Files.isRegularFile(current);
            if (hadCurrent) {
                Files.move(current, safety, StandardCopyOption.REPLACE_EXISTING);
            }
            Files.deleteIfExists(root.resolve("minidesk.db-wal"));
            Files.deleteIfExists(root.resolve("minidesk.db-shm"));
            try {
                Files.move(newDb, current);
                overlay(work.resolve("uploads"), root.resolve("uploads"));
                overlay(work.resolve("branding"), root.resolve("branding"));
                Path secret = work.resolve(".secret");
                if (Files.isRegularFile(secret)) {
                    Files.copy(secret, root.resolve(".secret"), StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException | RuntimeException ex) {
                Files.deleteIfExists(current);
                if (hadCurrent) {
                    Files.move(safety, current, StandardCopyOption.REPLACE_EXISTING);
                }
                throw ex;
            }
            return "restored " + pending.getFileName()
                    + (hadCurrent ? ", previous database saved as " + safety.getFileName() : "");
        } finally {
            deleteTree(work);
        }
    }

    private static void unpack(Path zip, Path target) throws IOException {
        int entries = 0;
        long total = 0;
        try (ZipInputStream in = new ZipInputStream(Files.newInputStream(zip))) {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                if (++entries > MAX_ENTRIES) {
                    throw new IOException("恢复包条目过多");
                }
                String name = entry.getName().replace('\\', '/');
                if (name.isEmpty() || name.startsWith("/") || name.contains("..")) {
                    throw new IOException("恢复包包含非法路径：" + name);
                }
                Path file = target.resolve(name).normalize();
                if (!file.startsWith(target)) {
                    throw new IOException("恢复包包含越界路径：" + name);
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(file);
                } else {
                    Files.createDirectories(file.getParent());
                    total += Files.copy(in, file, StandardCopyOption.REPLACE_EXISTING);
                    if (total > MAX_UNPACKED_BYTES) {
                        throw new IOException("恢复包解压后体积过大");
                    }
                }
                in.closeEntry();
            }
        }
    }

    private static void validateDatabase(Path db) throws IOException, SQLException {
        byte[] header = new byte[16];
        try (InputStream in = Files.newInputStream(db)) {
            if (in.read(header) != header.length
                    || !"SQLite format 3\u0000".equals(new String(header, StandardCharsets.ISO_8859_1))) {
                throw new IOException("minidesk.db 不是有效的 SQLite 数据库");
            }
        }
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + db.toAbsolutePath());
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("select count(*) from sys_user")) {
            if (!rs.next()) {
                throw new SQLException("minidesk.db 缺少 sys_user 表数据");
            }
        }
    }

    private static void overlay(Path source, Path target) throws IOException {
        if (!Files.isDirectory(source)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(source)) {
            for (Path file : walk.filter(Files::isRegularFile).sorted().toList()) {
                Path destination = target.resolve(source.relativize(file).toString());
                Files.createDirectories(destination.getParent());
                Files.copy(file, destination, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private static void deleteTree(Path root) {
        if (!Files.exists(root)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(root)) {
            walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // 临时目录，残留不影响启动
                }
            });
        } catch (IOException ignored) {
            System.err.println("[MiniDesk] cleanup of .restore-tmp skipped");
        }
    }

    private static Path dataRoot() {
        String configured = System.getProperty("minidesk.data-dir",
                System.getenv().getOrDefault("MINIDESK_DATA_DIR", "./data"));
        return Path.of(configured).toAbsolutePath().normalize();
    }
}
