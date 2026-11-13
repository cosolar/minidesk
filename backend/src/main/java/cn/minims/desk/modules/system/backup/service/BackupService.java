package cn.minims.desk.modules.system.backup.service;

import cn.minims.desk.common.api.ErrorCode;
import cn.minims.desk.common.exception.BizException;
import cn.minims.desk.modules.system.backup.vo.BackupVO;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * 备份与恢复。
 *
 * <p>快照用 SQLite 的 {@code VACUUM INTO}：它在数据库内部生成一份事务一致、不含 WAL 的
 * 完整副本，运行中的应用无需停写，也不必手工拼接 -wal/-shm。ZIP 里另外带上附件、
 * 品牌资源和集成加密主密钥，缺任何一样恢复后都会出现文件打不开或密钥解不开。
 *
 * <p>恢复不在运行期替换数据库文件（连接池仍持有旧句柄）。上传包先落成 pending，
 * 由 {@link cn.minims.desk.modules.system.backup.RestoreBootstrap} 在 Spring 启动前应用。
 */
@Service
public class BackupService {

    private static final Logger log = LoggerFactory.getLogger(BackupService.class);
    private static final DateTimeFormatter DATETIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");
    private static final String PENDING_NAME = "restore-pending.zip";
    private static final String LAST_RESULT_NAME = "restore-last.json";
    private static final long MAX_UPLOAD_BYTES = 512L * 1024 * 1024;

    private final JdbcTemplate jdbc;

    @Value("${minidesk.data-dir:./data}")
    private String dataDir;

    public BackupService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 生成一份完整快照，并把记录写进 backup_record。 */
    public BackupVO.BackupFile create(String triggerBy) {
        Path root = dataRoot();
        Path backups = ensureDirectory(root.resolve("backups"));
        String stamp = LocalDateTime.now().format(STAMP);
        String fileName = "minidesk-" + stamp + ".zip";
        Path zip = backups.resolve(fileName);
        Path work = null;
        String now = LocalDateTime.now().format(DATETIME);
        try {
            // 先登记再快照：VACUUM INTO 拿到的是当前事务一致状态，若记录在快照之后才写入，
            // 恢复出来的库就会少一条记录、和磁盘上留下的备份文件对不上。
            jdbc.update("""
                            insert into backup_record (file_name, size, encrypted, status, remark, created_at)
                            values (?, 0, 0, 'running', ?, ?)
                            """,
                    fileName, triggerBy, now);

            work = Files.createTempDirectory(backups, ".building-");
            Path snapshot = work.resolve("minidesk.db");
            // VACUUM INTO 不支持绑定参数；路径全部由本方法生成，只需转义单引号
            String literal = snapshot.toAbsolutePath().toString().replace("'", "''");
            jdbc.execute("VACUUM INTO '" + literal + "'");

            boolean hasSecret = Files.isRegularFile(root.resolve(".secret"));
            boolean hasBranding = Files.isDirectory(root.resolve("branding"));
            boolean hasUploads = Files.isDirectory(root.resolve("uploads"));
            try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip,
                    StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE))) {
                writeText(out, "manifest.json", manifest(now, triggerBy, hasSecret, hasUploads, hasBranding));
                writeFile(out, "minidesk.db", snapshot);
                if (hasSecret) {
                    writeFile(out, ".secret", root.resolve(".secret"));
                }
                if (hasUploads) {
                    writeTree(out, root.resolve("uploads"), "uploads/");
                }
                if (hasBranding) {
                    writeTree(out, root.resolve("branding"), "branding/");
                }
            }

            long size = Files.size(zip);
            jdbc.update("update backup_record set size = ?, status = 'success' where file_name = ?",
                    size, fileName);
            log.info("backup created, file={}, size={}", fileName, size);
            return new BackupVO.BackupFile(fileName, size, sizeText(size), triggerBy, "success", now);
        } catch (IOException | RuntimeException ex) {
            try {
                Files.deleteIfExists(zip);
            } catch (IOException ignored) {
                log.debug("remove partial backup failed", ignored);
            }
            try {
                jdbc.update("update backup_record set status = 'failed' where file_name = ?", fileName);
            } catch (RuntimeException updateFailed) {
                log.debug("mark backup record failed", updateFailed);
            }
            log.error("backup creation failed", ex);
            throw new BizException(ErrorCode.BACKUP_FAILED, "备份生成失败：" + rootMessage(ex));
        } finally {
            deleteTreeQuietly(work);
        }
    }

    public List<BackupVO.BackupFile> list() {
        return jdbc.query("""
                        select file_name, size, status, remark, created_at
                        from backup_record order by id desc
                        """,
                (rs, rowNum) -> {
                    String name = rs.getString("file_name");
                    long size = rs.getLong("size");
                    boolean exists = Files.isRegularFile(dataRoot().resolve("backups").resolve(name));
                    return new BackupVO.BackupFile(name, size, sizeText(size),
                            rs.getString("remark"), exists ? rs.getString("status") : "missing",
                            rs.getString("created_at"));
                });
    }

    /** 只保留最近 keep 份；文件删不掉时保留记录并告警，避免误报「已清理」。 */
    public int cleanup(int keep) {
        int safeKeep = Math.max(keep, 1);
        List<String> names = jdbc.query(
                "select file_name from backup_record order by id desc",
                (rs, rowNum) -> rs.getString(1));
        if (names.size() <= safeKeep) {
            return 0;
        }
        int removed = 0;
        for (String name : names.subList(safeKeep, names.size())) {
            try {
                if (safeName(name) && Files.deleteIfExists(dataRoot().resolve("backups").resolve(name))) {
                    removed++;
                }
                if (safeName(name)) {
                    jdbc.update("delete from backup_record where file_name = ?", name);
                }
            } catch (IOException | RuntimeException ex) {
                log.warn("remove old backup failed, file={}", name, ex);
            }
        }
        return removed;
    }

    public void delete(String fileName) {
        requireSafeName(fileName);
        Path file = dataRoot().resolve("backups").resolve(fileName);
        if (!Files.isRegularFile(file)) {
            throw new BizException(ErrorCode.BACKUP_NOT_FOUND);
        }
        try {
            Files.deleteIfExists(file);
        } catch (IOException ex) {
            throw new BizException(ErrorCode.BACKUP_FAILED, "备份文件删除失败");
        }
        jdbc.update("delete from backup_record where file_name = ?", fileName);
    }

    public Path locate(String fileName) {
        requireSafeName(fileName);
        Path file = dataRoot().resolve("backups").resolve(fileName);
        if (!Files.isRegularFile(file)) {
            throw new BizException(ErrorCode.BACKUP_NOT_FOUND);
        }
        return file;
    }

    /** 上传恢复包：只做基本校验与落盘，真正的还原留给下次启动。 */
    public BackupVO.RestoreResult queue(MultipartFile file) {
        if (file == null || file.isEmpty() || file.getSize() > MAX_UPLOAD_BYTES) {
            throw new BizException(ErrorCode.RESTORE_INVALID, "恢复包为空或超过 512MB");
        }
        Path root = ensureDirectory(dataRoot());
        Path pending = root.resolve(PENDING_NAME);
        if (Files.isRegularFile(pending)) {
            throw new BizException(ErrorCode.RESTORE_PENDING);
        }
        byte[] magic = new byte[4];
        try (InputStream in = file.getInputStream()) {
            if (in.readNBytes(magic, 0, 4) != 4 || magic[0] != 'P' || magic[1] != 'K') {
                throw new BizException(ErrorCode.RESTORE_INVALID, "文件不是 ZIP 备份包");
            }
            try (InputStream full = new java.io.SequenceInputStream(
                    new java.io.ByteArrayInputStream(magic), in);
                 java.io.OutputStream out = Files.newOutputStream(pending,
                         StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
                         StandardOpenOption.WRITE)) {
                full.transferTo(out);
            }
        } catch (IOException ex) {
            throw new BizException(ErrorCode.RESTORE_INVALID, "恢复包读写失败");
        }
        String now = LocalDateTime.now().format(DATETIME);
        writeText(root.resolve(PENDING_NAME + ".meta"), now);
        return new BackupVO.RestoreResult("queued",
                "恢复包已排队，重启应用后生效；当前数据不会被立即覆盖");
    }

    public BackupVO.RestoreStatus status() {
        Path root = dataRoot();
        Path pending = root.resolve(PENDING_NAME);
        String queuedAt = readText(root.resolve(PENDING_NAME + ".meta"));
        String last = readText(root.resolve(LAST_RESULT_NAME));
        return new BackupVO.RestoreStatus(Files.isRegularFile(pending),
                Files.isRegularFile(pending) ? PENDING_NAME : null, queuedAt, last);
    }

    /** 供启动引导类使用：清掉 pending 标记并写出最后一次结果。 */
    public static void finishRestore(Path root, boolean success, String message) {
        try {
            Files.writeString(root.resolve(LAST_RESULT_NAME),
                    (success ? "success: " : "failed: ") + message,
                    StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            Files.deleteIfExists(root.resolve(PENDING_NAME + ".meta"));
        } catch (IOException ex) {
            log.warn("write restore result failed", ex);
        }
    }

    public static String sizeText(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        if (bytes < 1024 * 1024) {
            return Math.round(bytes / 102.4) / 10.0 + " KB";
        }
        return Math.round(bytes / 1024.0 / 1024.0 * 10) / 10.0 + " MB";
    }

    private Path dataRoot() {
        return Path.of(dataDir).toAbsolutePath().normalize();
    }

    private static Path ensureDirectory(Path path) {
        try {
            Files.createDirectories(path);
            return path;
        } catch (IOException ex) {
            throw new BizException(ErrorCode.BACKUP_FAILED, "数据目录不可写");
        }
    }

    private static String manifest(String now, String triggerBy, boolean secret,
                                   boolean uploads, boolean branding) {
        return "{\"product\":\"MiniDesk\",\"schemaVersion\":1,\"createdAt\":\"" + now
                + "\",\"triggerBy\":\"" + triggerBy + "\",\"database\":\"minidesk.db\""
                + ",\"includesSecret\":" + secret + ",\"includesUploads\":" + uploads
                + ",\"includesBranding\":" + branding + "}";
    }

    private static void writeText(ZipOutputStream out, String name, String content) throws IOException {
        out.putNextEntry(new ZipEntry(name));
        out.write(content.getBytes(StandardCharsets.UTF_8));
        out.closeEntry();
    }

    private static void writeText(Path path, String content) {
        try {
            Files.writeString(path, content, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException ex) {
            log.debug("write text file failed, path={}", path);
        }
    }

    private static void writeFile(ZipOutputStream out, String name, Path source) throws IOException {
        out.putNextEntry(new ZipEntry(name));
        Files.copy(source, out);
        out.closeEntry();
    }

    private static void writeTree(ZipOutputStream out, Path root, String prefix) throws IOException {
        List<Path> files = new ArrayList<>(64);
        try (Stream<Path> walk = Files.walk(root)) {
            walk.filter(Files::isRegularFile).forEach(files::add);
        }
        files.sort(Comparator.comparing(Path::toString));
        for (Path file : files) {
            String relative = root.relativize(file).toString().replace('\\', '/');
            writeFile(out, prefix + relative, file);
        }
    }

    private static void deleteTreeQuietly(Path root) {
        if (root == null || !Files.exists(root)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(root)) {
            walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // 临时构建目录，残留不影响功能
                }
            });
        } catch (IOException ignored) {
            log.debug("cleanup temp backup dir failed");
        }
    }

    private static boolean safeName(String name) {
        return name != null && name.matches("[A-Za-z0-9._-]+\\.zip") && !name.contains("..");
    }

    private static void requireSafeName(String name) {
        if (!safeName(name)) {
            throw new BizException(ErrorCode.BACKUP_NOT_FOUND);
        }
    }

    private static String readText(Path path) {
        try {
            return Files.isRegularFile(path) ? Files.readString(path, StandardCharsets.UTF_8) : null;
        } catch (IOException ex) {
            return null;
        }
    }

    private static String rootMessage(Throwable ex) {
        Throwable current = ex;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current.getMessage() == null ? ex.getClass().getSimpleName() : current.getMessage();
    }
}
