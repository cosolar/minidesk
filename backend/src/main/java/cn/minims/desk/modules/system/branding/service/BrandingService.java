package cn.minims.desk.modules.system.branding.service;

import cn.minims.desk.common.api.ErrorCode;
import cn.minims.desk.common.exception.BizException;
import cn.minims.desk.modules.system.branding.vo.SiteVO;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * 站点品牌：站点名 + 方形 logo + 横版 logo。
 *
 * <p>logo 文件放 data/branding 下，通过 {@code /api/site/branding/{kind}} 公开访问。
 * 这几张图要出现在登录页（未登录），所以不能走需要 token 的 /api/files。
 */
@Service
public class BrandingService {

    private static final Set<String> ALLOWED = Set.of("png", "jpg", "jpeg", "webp", "svg");
    private static final long MAX_SIZE = 2L * 1024 * 1024;
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final JdbcTemplate jdbcTemplate;
    private final Path dir;

    public BrandingService(JdbcTemplate jdbcTemplate,
                           @Value("${minidesk.data-dir:./data}") String dataDir) {
        this.jdbcTemplate = jdbcTemplate;
        this.dir = Paths.get(dataDir, "branding").toAbsolutePath().normalize();
    }

    public SiteVO site() {
        return new SiteVO(config("site_name", "MiniDesk"), urlOf("logo"), urlOf("logo-wide"));
    }

    /** 读取品牌图；找不到返回 null，由前端回退内置资源 */
    public StoredFile read(String kind) {
        String safeKind = normalizeKind(kind);
        Path file = findFile(safeKind);
        if (file == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        try {
            String name = file.getFileName().toString();
            String ext = name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
            return new StoredFile(new UrlResource(file.toUri()), mediaType(ext));
        } catch (IOException ex) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public SiteVO upload(String kind, MultipartFile file) {
        String safeKind = normalizeKind(kind);
        if (file == null || file.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "请选择图片");
        }
        if (file.getSize() > MAX_SIZE) {
            throw new BizException(ErrorCode.PARAM_ERROR, "图片不能超过 2MB");
        }
        String original = file.getOriginalFilename() == null ? "logo" : file.getOriginalFilename();
        int dot = original.lastIndexOf('.');
        String ext = dot < 0 ? "" : original.substring(dot + 1).toLowerCase(Locale.ROOT);
        if (!ALLOWED.contains(ext)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "只支持 png / jpg / webp / svg");
        }

        try {
            Files.createDirectories(dir);
            deleteExisting(safeKind);
            Path target = dir.resolve(safeKind + "." + ext).normalize();
            if (!target.startsWith(dir)) {
                throw new BizException(ErrorCode.PARAM_ERROR, "非法路径");
            }
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ex) {
            throw new BizException(ErrorCode.SERVER_ERROR, "图片保存失败", ex);
        }

        upsertConfig("site_" + (safeKind.equals("logo") ? "logo" : "logo_wide"), file.getOriginalFilename());
        return site();
    }

    @Transactional(rollbackFor = Exception.class)
    public SiteVO rename(String siteName) {
        if (siteName != null && !siteName.isBlank()) {
            upsertConfig("site_name", siteName.trim());
        }
        return site();
    }

    public void remove(String kind) {
        String safeKind = normalizeKind(kind);
        deleteExisting(safeKind);
        jdbcTemplate.update("delete from sys_config where config_key = ?",
                "site_" + (safeKind.equals("logo") ? "logo" : "logo_wide"));
    }

    /** 公开 URL 带时间戳，后台换图后浏览器能立刻刷新 */
    private String urlOf(String kind) {
        Path file = findFile(kind);
        if (file == null) {
            return null;
        }
        try {
            return "/api/site/branding/" + kind + "?v="
                    + Files.getLastModifiedTime(file).toMillis();
        } catch (IOException ex) {
            return "/api/site/branding/" + kind;
        }
    }

    private Path findFile(String kind) {
        if (!Files.isDirectory(dir)) {
            return null;
        }
        for (String ext : ALLOWED) {
            Path candidate = dir.resolve(kind + "." + ext);
            if (Files.exists(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private void deleteExisting(String kind) {
        for (String ext : ALLOWED) {
            try {
                Files.deleteIfExists(dir.resolve(kind + "." + ext));
            } catch (IOException ignored) {
                // 删除失败不影响新图写入
            }
        }
    }

    private String normalizeKind(String kind) {
        return "logo-wide".equals(kind) ? "logo-wide" : "logo";
    }

    private String mediaType(String ext) {
        return switch (ext) {
            case "png" -> "image/png";
            case "svg" -> "image/svg+xml";
            case "webp" -> "image/webp";
            default -> "image/jpeg";
        };
    }

    private String config(String key, String fallback) {
        List<String> values = jdbcTemplate.queryForList(
                "select config_value from sys_config where config_key = ?", String.class, key);
        return values.isEmpty() || values.get(0) == null || values.get(0).isBlank()
                ? fallback : values.get(0);
    }

    private void upsertConfig(String key, String value) {
        String now = LocalDateTime.now().format(TS);
        int updated = jdbcTemplate.update(
                "update sys_config set config_value = ?, updated_at = ? where config_key = ?",
                value, now, key);
        if (updated == 0) {
            jdbcTemplate.update(
                    "insert into sys_config (config_key, config_value, remark, updated_at) values (?,?,?,?)",
                    key, value, "站点品牌", now);
        }
    }

    public record StoredFile(Resource resource, String contentType) {
    }
}
