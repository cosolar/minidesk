package cn.minims.desk.modules.integration.minidocs.config;

import cn.minims.desk.framework.crypto.AesGcmCipher;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 允许用环境变量注入知识库令牌，方便单 Jar / Docker 部署免去手工填设置页。
 *
 * <p>只在「还没有任何用户配置过令牌」时导入，且只导入给最早注册的账号（首个注册者是管理员）；
 * 已存在的配置一律不覆盖，避免重启把用户后改的令牌冲掉。明文只在启动时短暂驻留内存。
 */
@Component
public class MiniDocsBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(MiniDocsBootstrap.class);
    private static final DateTimeFormatter DATETIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String DEFAULT_HOST = "https://docs.minims.cn";

    private final JdbcTemplate jdbcTemplate;
    private final AesGcmCipher cipher;
    private final String rawToken;

    public MiniDocsBootstrap(JdbcTemplate jdbcTemplate,
                             AesGcmCipher cipher,
                             @Value("${minidesk.minidocs-token:}") String rawToken) {
        this.jdbcTemplate = jdbcTemplate;
        this.cipher = cipher;
        this.rawToken = rawToken == null ? "" : rawToken.trim();
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!StringUtils.hasText(rawToken)) {
            return;
        }
        Integer existing = jdbcTemplate.queryForObject(
                "select count(*) from integration_config where provider = 'minidocs' "
                        + "and api_key_cipher is not null and api_key_cipher <> ''", Integer.class);
        if (existing != null && existing > 0) {
            return;
        }
        List<Long> owners = jdbcTemplate.queryForList(
                "select id from sys_user where deleted = 0 order by id limit 1", Long.class);
        if (owners.isEmpty()) {
            return;
        }
        long userId = owners.get(0);
        String now = LocalDateTime.now().format(DATETIME);
        String encrypted = cipher.encrypt(rawToken);
        Integer rows = jdbcTemplate.queryForObject(
                "select count(*) from integration_config where user_id = ? and provider = 'minidocs'",
                Integer.class, userId);
        if (rows != null && rows > 0) {
            jdbcTemplate.update("update integration_config set api_key_cipher = ?, api_host = ?, "
                            + "enabled = 1, updated_at = ? where user_id = ? and provider = 'minidocs'",
                    encrypted, DEFAULT_HOST, now, userId);
        } else {
            jdbcTemplate.update("insert into integration_config "
                            + "(user_id, provider, api_key_cipher, api_host, extra_json, enabled, created_at, updated_at) "
                            + "values (?, 'minidocs', ?, ?, null, 1, ?, ?)",
                    userId, encrypted, DEFAULT_HOST, now, now);
        }
        // 不输出令牌本身，也不输出密文
        log.info("minidocs token imported from environment for user {}", userId);
    }
}
