package cn.minims.desk.modules.drive.config;

import cn.minims.desk.common.api.ErrorCode;
import cn.minims.desk.common.exception.BizException;
import cn.minims.desk.framework.crypto.AesGcmCipher;
import cn.minims.desk.framework.tenant.TenantContext;
import cn.minims.desk.modules.drive.dto.DriveConfigDTO;
import cn.minims.desk.modules.drive.vo.DriveConfigVO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 网盘存储配置。复用 integration_config（provider = drive），密钥 AES-GCM 加密落库。
 *
 * <p>用 JdbcTemplate 显式带 user_id，既保证多账号隔离，也让白名单里的 raw 下载接口
 * （没有登录上下文）能按传入的 userId 读到配置。
 */
@Service
public class DriveConfigService {

    private static final String PROVIDER = "drive";
    private static final DateTimeFormatter DATETIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final JdbcTemplate jdbcTemplate;
    private final AesGcmCipher cipher;
    private final ObjectMapper objectMapper;

    public DriveConfigService(JdbcTemplate jdbcTemplate, AesGcmCipher cipher, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.cipher = cipher;
        this.objectMapper = objectMapper;
    }

    public DriveSettings settings() {
        return settingsForUser(TenantContext.requireUserId());
    }

    public DriveSettings settingsForUser(long userId) {
        Map<String, Object> row = findRow(userId);
        if (row == null) {
            return DriveSettings.local();
        }
        Map<String, String> extra = readExtra(row.get("extra_json"));
        String secret = row.get("api_key_cipher") == null
                ? null : cipher.decrypt(String.valueOf(row.get("api_key_cipher")));
        return new DriveSettings(
                text(extra.get("storageType"), "local"),
                text(extra.get("endpoint"), ""),
                text(extra.get("bucket"), ""),
                text(extra.get("region"), "us-east-1"),
                text(extra.get("accessKey"), ""),
                text(secret, ""),
                !"false".equalsIgnoreCase(extra.get("pathStyle")),
                text(extra.get("prefix"), ""),
                text(extra.get("publicUrl"), ""),
                text(extra.get("acl"), ""));
    }

    public DriveConfigVO view() {
        DriveSettings s = settings();
        return new DriveConfigVO(
                s.storageType(), s.endpoint(), s.bucket(), s.region(), s.accessKey(),
                cipher.mask(s.secretKey()), StringUtils.hasText(s.secretKey()),
                s.pathStyle(), s.prefix(), s.publicUrl(), s.acl(), s.ready());
    }

    @Transactional(rollbackFor = Exception.class)
    public DriveConfigVO save(DriveConfigDTO dto) {
        long userId = TenantContext.requireUserId();
        Map<String, Object> row = findRow(userId);
        String oldSecret = row == null || row.get("api_key_cipher") == null
                ? null : cipher.decrypt(String.valueOf(row.get("api_key_cipher")));
        String secret = dto.secretKey();
        if (!StringUtils.hasText(secret) || secret.contains("****")) {
            secret = oldSecret;
        }

        Map<String, String> extra = new LinkedHashMap<>();
        extra.put("storageType", "s3".equalsIgnoreCase(dto.storageType()) ? "s3" : "local");
        extra.put("endpoint", trim(dto.endpoint()));
        extra.put("bucket", trim(dto.bucket()));
        extra.put("region", StringUtils.hasText(dto.region()) ? dto.region().trim() : "us-east-1");
        extra.put("accessKey", trim(dto.accessKey()));
        extra.put("pathStyle", String.valueOf(dto.pathStyle() == null || dto.pathStyle()));
        extra.put("prefix", trim(dto.prefix()));
        extra.put("publicUrl", trim(dto.publicUrl()));
        extra.put("acl", trim(dto.acl()));

        String now = LocalDateTime.now().format(DATETIME);
        String cipherText = cipher.encrypt(StringUtils.hasText(secret) ? secret.trim() : null);
        String json = writeExtra(extra);
        if (row == null) {
            jdbcTemplate.update("insert into integration_config "
                            + "(user_id, provider, api_key_cipher, api_host, extra_json, enabled, created_at, updated_at) "
                            + "values (?,?,?,?,?,1,?,?)",
                    userId, PROVIDER, cipherText, trim(dto.endpoint()), json, now, now);
        } else {
            jdbcTemplate.update("update integration_config set api_key_cipher = ?, api_host = ?, "
                            + "extra_json = ?, enabled = 1, updated_at = ? where id = ?",
                    cipherText, trim(dto.endpoint()), json, now, ((Number) row.get("id")).longValue());
        }
        return view();
    }

    private Map<String, Object> findRow(long userId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "select * from integration_config where provider = ? and user_id = ? limit 1",
                PROVIDER, userId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private Map<String, String> readExtra(Object value) {
        if (value == null) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(String.valueOf(value),
                    objectMapper.getTypeFactory().constructMapType(Map.class, String.class, String.class));
        } catch (JsonProcessingException ex) {
            return Map.of();
        }
    }

    private String writeExtra(Map<String, String> extra) {
        try {
            return objectMapper.writeValueAsString(extra);
        } catch (JsonProcessingException ex) {
            throw new BizException(ErrorCode.SERVER_ERROR, "存储配置序列化失败", ex);
        }
    }

    private static String text(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
