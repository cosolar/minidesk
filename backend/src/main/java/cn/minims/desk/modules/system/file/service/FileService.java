package cn.minims.desk.modules.system.file.service;

import cn.minims.desk.common.api.ErrorCode;
import cn.minims.desk.common.exception.BizException;
import cn.minims.desk.modules.system.file.entity.SysFileDO;
import cn.minims.desk.modules.system.file.mapper.SysFileMapper;
import cn.minims.desk.modules.system.file.vo.FileVO;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

/**
 * 本地文件存储。
 *
 * <p>刻意不接对象存储：单机 + Tauri 场景下文件就在 data-dir 里，备份时一起打包最省事。
 * 相册也支持直接填图床外链，两种方式并存。
 */
@Service
public class FileService {

    private static final Set<String> IMAGE_EXT = Set.of("jpg", "jpeg", "png", "gif", "webp", "bmp", "svg");
    private static final Set<String> DOC_EXT = Set.of("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx",
            "md", "txt", "csv", "zip");
    private static final long MAX_SIZE = 20L * 1024 * 1024;
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyyMM");

    private final SysFileMapper mapper;
    private final JdbcTemplate jdbcTemplate;
    private final Path root;

    public FileService(SysFileMapper mapper, JdbcTemplate jdbcTemplate,
                       @Value("${minidesk.data-dir:./data}") String dataDir) {
        this.mapper = mapper;
        this.jdbcTemplate = jdbcTemplate;
        this.root = Paths.get(dataDir).toAbsolutePath().normalize();
    }

    @Transactional(rollbackFor = Exception.class)
    public FileVO store(MultipartFile file, String bizType) {
        if (file == null || file.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "请选择文件");
        }
        if (file.getSize() > MAX_SIZE) {
            throw new BizException(ErrorCode.PARAM_ERROR, "文件不能超过 20MB");
        }
        String original = StringUtils.hasText(file.getOriginalFilename())
                ? file.getOriginalFilename() : "file";
        String ext = extensionOf(original);
        if (!IMAGE_EXT.contains(ext) && !DOC_EXT.contains(ext)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "不支持的文件类型：" + ext);
        }

        String relative = "uploads/" + LocalDateTime.now().format(MONTH) + "/"
                + UUID.randomUUID().toString().replace("-", "") + "." + ext;
        Path target = root.resolve(relative).normalize();
        if (!target.startsWith(root)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "非法路径");
        }
        try {
            Files.createDirectories(target.getParent());
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ex) {
            throw new BizException(ErrorCode.SERVER_ERROR, "文件保存失败", ex);
        }

        SysFileDO record = new SysFileDO();
        record.setName(original);
        record.setPath(relative);
        record.setSize(file.getSize());
        record.setMime(file.getContentType());
        record.setBizType(StringUtils.hasText(bizType) ? bizType : "doc");
        record.setCreatedAt(LocalDateTime.now());
        mapper.insert(record);
        return new FileVO(record.getId(), original, file.getSize(), record.getMime(), "/api/files/" + record.getId() + "/raw");
    }

    public SysFileDO require(Long id) {
        SysFileDO record = mapper.selectById(id);
        if (record == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        return record;
    }

    /**
     * 按「文件 id + 归属用户」读取。
     *
     * <p>图片走 &lt;img&gt; 请求，没法带请求头，所以 /raw 在登录拦截器白名单里。
     * 这条路径不经过 Sa-Token 的登录态，租户插件也拿不到 userId，
     * 因此这里用显式 SQL 过滤 user_id，并只在调用方校验过 token 后使用。
     */
    public OwnedFile loadOwned(long id, long userId) {
        var rows = jdbcTemplate.queryForList(
                "select path, mime from sys_file where id = ? and user_id = ? and deleted = 0",
                id, userId);
        if (rows.isEmpty()) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        String stored = String.valueOf(rows.get(0).get("path"));
        Object mime = rows.get(0).get("mime");
        try {
            Path path = root.resolve(stored).normalize();
            if (!path.startsWith(root) || !Files.exists(path)) {
                throw new BizException(ErrorCode.NOT_FOUND);
            }
            return new OwnedFile(new UrlResource(path.toUri()), mime == null ? null : mime.toString());
        } catch (IOException ex) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
    }

    /** 读取结果：资源 + MIME */
    public record OwnedFile(Resource resource, String mime) {
    }

    public String urlOf(Long id) {
        return id == null ? null : "/api/files/" + id + "/raw";
    }

    private String extensionOf(String name) {
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            return "";
        }
        return name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
