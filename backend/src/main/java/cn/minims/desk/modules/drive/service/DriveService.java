package cn.minims.desk.modules.drive.service;

import cn.minims.desk.common.api.ErrorCode;
import cn.minims.desk.common.exception.BizException;
import cn.minims.desk.framework.tenant.TenantContext;
import cn.minims.desk.modules.drive.config.DriveConfigService;
import cn.minims.desk.modules.drive.config.DriveSettings;
import cn.minims.desk.modules.drive.dto.DriveFolderDTO;
import cn.minims.desk.modules.drive.entity.DriveFileDO;
import cn.minims.desk.modules.drive.mapper.DriveFileMapper;
import cn.minims.desk.modules.drive.storage.S3DriveClient;
import cn.minims.desk.modules.drive.vo.DriveFileVO;
import cn.minims.desk.modules.drive.vo.DriveStatsVO;
import cn.minims.desk.modules.drive.vo.DriveTestResultVO;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

/**
 * 网盘文件管理。本地磁盘与 S3 兼容对象存储双后端，按文件记录里的 storage 字段决定读取方式。
 */
@Service
public class DriveService {

    private static final Logger log = LoggerFactory.getLogger(DriveService.class);
    private static final long MAX_FILE_SIZE = 100L * 1024 * 1024;
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyyMM");
    private static final Set<String> DOC_EXT = Set.of("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx",
            "md", "txt", "csv", "json", "xml", "yaml", "yml");

    private final DriveFileMapper mapper;
    private final JdbcTemplate jdbcTemplate;
    private final DriveConfigService configService;
    private final S3DriveClient s3;
    private final Path root;

    public DriveService(DriveFileMapper mapper, JdbcTemplate jdbcTemplate,
                        DriveConfigService configService, S3DriveClient s3,
                        @Value("${minidesk.data-dir:./data}") String dataDir) {
        this.mapper = mapper;
        this.jdbcTemplate = jdbcTemplate;
        this.configService = configService;
        this.s3 = s3;
        this.root = Paths.get(dataDir).toAbsolutePath().normalize().resolve("drive");
    }

    public List<DriveFileVO> list(Long parentId, String keyword) {
        long parent = parentId == null ? 0L : parentId;
        var query = Wrappers.lambdaQuery(DriveFileDO.class);
        if (StringUtils.hasText(keyword)) {
            query.like(DriveFileDO::getName, keyword.trim());
        } else {
            query.eq(DriveFileDO::getParentId, parent);
        }
        List<DriveFileDO> rows = new ArrayList<>(mapper.selectList(query));
        rows.sort(Comparator
                .comparingInt((DriveFileDO item) -> "folder".equals(item.getType()) ? 0 : 1)
                .thenComparing(item -> item.getName() == null ? "" : item.getName(),
                        String.CASE_INSENSITIVE_ORDER));

        Map<Long, Integer> childCounts = new HashMap<>();
        for (DriveFileDO item : mapper.selectList(Wrappers.lambdaQuery(DriveFileDO.class))) {
            Long key = item.getParentId() == null ? 0L : item.getParentId();
            childCounts.merge(key, 1, Integer::sum);
        }
        DriveSettings settings = configService.settings();
        return rows.stream()
                .map(item -> new DriveFileVO(
                        item.getId(),
                        item.getParentId() == null ? 0L : item.getParentId(),
                        item.getName(), item.getType(),
                        item.getSize() == null ? 0L : item.getSize(),
                        item.getMime(), item.getStorage(),
                        childCounts.getOrDefault(item.getId(), 0),
                        linkOf(item, settings),
                        item.getCreatedAt(), item.getUpdatedAt()))
                .toList();
    }

    public DriveStatsVO stats() {
        List<DriveFileDO> rows = mapper.selectList(Wrappers.lambdaQuery(DriveFileDO.class));
        int files = 0;
        int folders = 0;
        int images = 0;
        int documents = 0;
        int others = 0;
        long used = 0;
        for (DriveFileDO item : rows) {
            if ("folder".equals(item.getType())) {
                folders++;
                continue;
            }
            files++;
            used += item.getSize() == null ? 0 : item.getSize();
            String mime = item.getMime() == null ? "" : item.getMime();
            String ext = extensionOf(item.getName());
            if (mime.startsWith("image/")) {
                images++;
            } else if (mime.startsWith("video/") || mime.startsWith("audio/")
                    || DOC_EXT.contains(ext) || mime.contains("pdf") || mime.contains("word")) {
                documents++;
            } else {
                others++;
            }
        }
        DriveSettings settings = configService.settings();
        long capacity = 0;
        if (!settings.isS3()) {
            try {
                Files.createDirectories(root);
                capacity = Files.getFileStore(root).getTotalSpace();
            } catch (IOException ignored) {
                capacity = 0;
            }
        }
        return new DriveStatsVO(files, folders, used, capacity, images, documents, others,
                settings.storageType(), settings.isS3() ? "S3 对象存储" : "本地磁盘", settings.ready());
    }

    @Transactional(rollbackFor = Exception.class)
    public DriveFileVO createFolder(DriveFolderDTO dto) {
        long parentId = requireFolder(dto.parentId());
        String name = sanitizeName(dto.name());
        name = uniqueName(parentId, name);
        LocalDateTime now = LocalDateTime.now();
        DriveFileDO folder = new DriveFileDO();
        folder.setParentId(parentId);
        folder.setName(name);
        folder.setType("folder");
        folder.setSize(0L);
        folder.setStorage("local");
        folder.setCreatedAt(now);
        folder.setUpdatedAt(now);
        mapper.insert(folder);
        return toVO(folder, 0, configService.settings());
    }

    @Transactional(rollbackFor = Exception.class)
    public DriveFileVO upload(MultipartFile file, Long parentId) {
        if (file == null || file.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "请选择文件");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BizException(ErrorCode.PARAM_ERROR, "单个文件不能超过 100MB");
        }
        long parent = requireFolder(parentId);
        long userId = TenantContext.requireUserId();
        String original = file.getOriginalFilename();
        String name = uniqueName(parent, sanitizeName(StringUtils.hasText(original) ? original : "未命名文件"));
        String objectKey = MONTH.format(LocalDateTime.now()) + "/"
                + UUID.randomUUID().toString().replace("-", "") + "." + extensionOf(name);

        DriveSettings settings = configService.settings();
        boolean useS3 = settings.isS3() && settings.ready();
        String storage = useS3 ? "s3" : "local";
        String mime = StringUtils.hasText(file.getContentType())
                ? file.getContentType() : "application/octet-stream";
        try {
            if (useS3) {
                Path temp = Files.createTempFile("minidesk-drive-", ".tmp");
                try {
                    try (InputStream in = file.getInputStream()) {
                        Files.copy(in, temp, StandardCopyOption.REPLACE_EXISTING);
                    }
                    s3.put(settings, settings.fullKey(objectKey), temp, mime);
                } finally {
                    Files.deleteIfExists(temp);
                }
            } else {
                Path target = localPath(userId, objectKey);
                Files.createDirectories(target.getParent());
                try (InputStream in = file.getInputStream()) {
                    Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        } catch (IOException ex) {
            throw new BizException(ErrorCode.SERVER_ERROR, "文件写入失败：" + ex.getMessage(), ex);
        }

        LocalDateTime now = LocalDateTime.now();
        DriveFileDO record = new DriveFileDO();
        record.setParentId(parent);
        record.setName(name);
        record.setType("file");
        record.setSize(file.getSize());
        record.setMime(mime);
        record.setStorage(storage);
        record.setObjectKey(objectKey);
        record.setCreatedAt(now);
        record.setUpdatedAt(now);
        mapper.insert(record);
        return toVO(record, 0, settings);
    }

    @Transactional(rollbackFor = Exception.class)
    public DriveFileVO rename(Long id, String rawName) {
        DriveFileDO item = require(id);
        String name = sanitizeName(rawName);
        if (!name.equals(item.getName())) {
            item.setName(uniqueName(item.getParentId() == null ? 0L : item.getParentId(), name));
        }
        item.setUpdatedAt(LocalDateTime.now());
        mapper.updateById(item);
        return toVO(item, 0, configService.settings());
    }

    /** 删除目录时连同后代一起删；对象存储的清理失败只记日志，不阻塞数据库记录删除 */
    @Transactional(rollbackFor = Exception.class)
    public void remove(Long id) {
        require(id);
        List<Long> ids = new ArrayList<>();
        Deque<Long> queue = new ArrayDeque<>();
        queue.add(id);
        while (!queue.isEmpty() && ids.size() < 1000) {
            Long current = queue.poll();
            ids.add(current);
            List<DriveFileDO> children = mapper.selectList(Wrappers.lambdaQuery(DriveFileDO.class)
                    .eq(DriveFileDO::getParentId, current));
            for (DriveFileDO child : children) {
                if ("folder".equals(child.getType())) {
                    queue.add(child.getId());
                } else {
                    ids.add(child.getId());
                }
            }
        }
        DriveSettings settings = configService.settings();
        long userId = TenantContext.requireUserId();
        for (DriveFileDO item : mapper.selectBatchIds(ids)) {
            if (!"file".equals(item.getType()) || !StringUtils.hasText(item.getObjectKey())) {
                continue;
            }
            try {
                if ("s3".equals(item.getStorage()) && settings.isS3()) {
                    s3.delete(settings, settings.fullKey(item.getObjectKey()));
                } else {
                    Files.deleteIfExists(localPath(userId, item.getObjectKey()));
                }
            } catch (IOException | RuntimeException ex) {
                log.warn("drive object cleanup failed, key={}, reason={}", item.getObjectKey(), ex.toString());
            }
        }
        mapper.deleteBatchIds(ids);
    }

    public DriveTestResultVO testStorage() {
        DriveSettings settings = configService.settings();
        if (!settings.isS3()) {
            try {
                Files.createDirectories(root);
                long free = Files.getFileStore(root).getUsableSpace();
                return new DriveTestResultVO(true, "本地目录可写，可用空间 " + human(free), "local");
            } catch (IOException ex) {
                return new DriveTestResultVO(false, "本地目录不可写：" + ex.getMessage(), "local");
            }
        }
        if (!settings.ready()) {
            return new DriveTestResultVO(false, "请先填完 Endpoint / Bucket / AccessKey / SecretKey", "s3");
        }
        try {
            s3.probe(settings);
            return new DriveTestResultVO(true, "连接成功 · 读 / 写 / 删均正常", "s3");
        } catch (BizException ex) {
            return new DriveTestResultVO(false, ex.getMessage(), "s3");
        }
    }

    /**
     * 白名单接口专用：显式按 userId 过滤，避免依赖租户插件的登录上下文。
     */
    public DriveDownload openOwned(long id, long userId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "select name, type, size, mime, storage, object_key from drive_file "
                        + "where id = ? and user_id = ? and deleted = 0", id, userId);
        if (rows.isEmpty() || !"file".equals(String.valueOf(rows.get(0).get("type")))) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        Map<String, Object> row = rows.get(0);
        String name = String.valueOf(row.get("name"));
        String mime = row.get("mime") == null ? "" : String.valueOf(row.get("mime"));
        long size = row.get("size") == null ? 0L : ((Number) row.get("size")).longValue();
        String storage = String.valueOf(row.get("storage"));
        String key = row.get("object_key") == null ? "" : String.valueOf(row.get("object_key"));
        try {
            InputStream stream;
            if ("s3".equals(storage)) {
                DriveSettings settings = configService.settingsForUser(userId);
                stream = s3.get(settings, settings.fullKey(key));
            } else {
                stream = Files.newInputStream(localPath(userId, key));
            }
            return new DriveDownload(name, mime, size, stream);
        } catch (IOException ex) {
            throw new BizException(ErrorCode.NOT_FOUND, "文件已不存在", ex);
        }
    }

    /** 读取结果：名称 / MIME / 大小 / 流，由调用方写入响应并关闭 */
    public record DriveDownload(String name, String mime, long size, InputStream stream) {
    }

    private DriveFileDO require(Long id) {
        DriveFileDO item = mapper.selectById(id);
        if (item == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "文件不存在");
        }
        return item;
    }

    private long requireFolder(Long parentId) {
        if (parentId == null || parentId == 0L) {
            return 0L;
        }
        DriveFileDO folder = mapper.selectById(parentId);
        if (folder == null || !"folder".equals(folder.getType())) {
            throw new BizException(ErrorCode.NOT_FOUND, "目标文件夹不存在");
        }
        return parentId;
    }

    private String uniqueName(long parentId, String name) {
        Set<String> taken = new HashSet<>();
        for (DriveFileDO sibling : mapper.selectList(Wrappers.lambdaQuery(DriveFileDO.class)
                .eq(DriveFileDO::getParentId, parentId))) {
            taken.add(sibling.getName());
        }
        if (!taken.contains(name)) {
            return name;
        }
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        String ext = dot > 0 ? name.substring(dot) : "";
        for (int index = 2; index < 100; index++) {
            String candidate = base + " (" + index + ")" + ext;
            if (!taken.contains(candidate)) {
                return candidate;
            }
        }
        return base + "-" + UUID.randomUUID().toString().substring(0, 6) + ext;
    }

    private String sanitizeName(String name) {
        String value = name.replace('\\', '/');
        int slash = value.lastIndexOf('/');
        if (slash >= 0) {
            value = value.substring(slash + 1);
        }
        value = value.replaceAll("[\\u0000-\\u001f]", "").trim();
        if (value.isEmpty() || ".".equals(value) || "..".equals(value)) {
            value = "未命名";
        }
        return value.length() > 200 ? value.substring(0, 200) : value;
    }

    private Path localPath(long userId, String objectKey) {
        Path userRoot = root.resolve(String.valueOf(userId)).normalize();
        Path target = userRoot.resolve(objectKey).normalize();
        if (!target.startsWith(userRoot)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "非法文件路径");
        }
        return target;
    }

    private String linkOf(DriveFileDO item, DriveSettings settings) {
        if (!"file".equals(item.getType())) {
            return "";
        }
        if ("s3".equals(item.getStorage()) && StringUtils.hasText(settings.publicUrl())
                && StringUtils.hasText(item.getObjectKey())) {
            String base = settings.publicUrl().trim();
            if (!base.endsWith("/")) {
                base += "/";
            }
            return base + settings.fullKey(item.getObjectKey());
        }
        return "/api/drive/files/" + item.getId() + "/raw";
    }

    private DriveFileVO toVO(DriveFileDO item, int childCount, DriveSettings settings) {
        return new DriveFileVO(item.getId(),
                item.getParentId() == null ? 0L : item.getParentId(),
                item.getName(), item.getType(),
                item.getSize() == null ? 0L : item.getSize(),
                item.getMime(), item.getStorage(), childCount,
                linkOf(item, settings), item.getCreatedAt(), item.getUpdatedAt());
    }

    private static String extensionOf(String name) {
        int dot = name == null ? -1 : name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            return "bin";
        }
        String ext = name.substring(dot + 1).toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]", "");
        return ext.isEmpty() || ext.length() > 12 ? "bin" : ext;
    }

    private static String human(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        String[] units = {"KB", "MB", "GB", "TB"};
        double value = bytes;
        int index = -1;
        do {
            value /= 1024;
            index++;
        } while (value >= 1024 && index < units.length - 1);
        return String.format(Locale.ROOT, "%.1f %s", value, units[index]);
    }
}
