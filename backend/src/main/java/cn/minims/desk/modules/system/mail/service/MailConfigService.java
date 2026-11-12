package cn.minims.desk.modules.system.mail.service;

import cn.minims.desk.common.api.ErrorCode;
import cn.minims.desk.common.exception.BizException;
import cn.minims.desk.framework.crypto.AesGcmCipher;
import cn.minims.desk.modules.system.mail.vo.MailConfigVO;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Properties;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 发件邮箱（SMTP）配置。
 *
 * <p>配置存在 sys_config 的全局键里，密码用 AES-GCM 加密。后台改完立即生效，
 * 不需要重启：每次发信按当前配置新建 JavaMailSender。
 */
@Service
public class MailConfigService {

    private static final String K_HOST = "smtp_host";
    private static final String K_PORT = "smtp_port";
    private static final String K_USER = "smtp_username";
    private static final String K_PASS = "smtp_password";
    private static final String K_FROM = "smtp_from";
    private static final String K_SSL = "smtp_ssl";
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final JdbcTemplate jdbcTemplate;
    private final AesGcmCipher cipher;

    public MailConfigService(JdbcTemplate jdbcTemplate, AesGcmCipher cipher) {
        this.jdbcTemplate = jdbcTemplate;
        this.cipher = cipher;
    }

    public MailConfigVO get() {
        String host = value(K_HOST);
        String password = cipher.decrypt(value(K_PASS));
        return new MailConfigVO(
                host,
                parseInt(value(K_PORT), 465),
                value(K_USER),
                value(K_FROM),
                !"false".equalsIgnoreCase(value(K_SSL)),
                cipher.mask(password),
                StringUtils.hasText(host) && StringUtils.hasText(value(K_USER)) && StringUtils.hasText(password),
                !"false".equalsIgnoreCase(value("mail_enabled")));
    }

    @Transactional(rollbackFor = Exception.class)
    public MailConfigVO save(MailConfigVO dto) {
        upsert(K_HOST, trim(dto.host()));
        upsert(K_PORT, String.valueOf(dto.port() <= 0 ? 465 : dto.port()));
        upsert(K_USER, trim(dto.username()));
        upsert(K_FROM, trim(StringUtils.hasText(dto.from()) ? dto.from() : dto.username()));
        upsert(K_SSL, dto.ssl() ? "true" : "false");
        upsert("mail_enabled", dto.enabled() ? "true" : "false");

        // 传回掩码说明用户没改密码，保持原值
        String pass = dto.maskedPassword();
        if (StringUtils.hasText(pass) && !pass.startsWith("****")) {
            upsert(K_PASS, cipher.encrypt(pass));
        }
        return get();
    }

    /** 未配置时不要静默失败：注册页要能明确提示「管理员未配置发件邮箱」 */
    public JavaMailSender sender() {
        MailConfigVO config = get();
        if (!config.configured()) {
            throw new BizException(ErrorCode.MAIL_NOT_CONFIGURED);
        }
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(config.host());
        sender.setPort(config.port());
        sender.setUsername(config.username());
        sender.setPassword(cipher.decrypt(value(K_PASS)));
        sender.setDefaultEncoding("UTF-8");

        Properties props = sender.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.connectiontimeout", "5000");
        props.put("mail.smtp.timeout", "8000");
        props.put("mail.smtp.writetimeout", "8000");
        if (config.ssl() && config.port() == 465) {
            props.put("mail.smtp.ssl.enable", "true");
        } else {
            props.put("mail.smtp.starttls.enable", "true");
            props.put("mail.smtp.starttls.required", "false");
        }
        return sender;
    }

    public String fromAddress() {
        MailConfigVO config = get();
        return StringUtils.hasText(config.from()) ? config.from() : config.username();
    }

    public boolean isReady() {
        MailConfigVO config = get();
        return config.configured() && config.enabled();
    }

    private String value(String key) {
        List<String> values = jdbcTemplate.queryForList(
                "select config_value from sys_config where config_key = ?", String.class, key);
        return values.isEmpty() ? "" : values.get(0);
    }

    private void upsert(String key, String value) {
        String now = LocalDateTime.now().format(TS);
        int updated = jdbcTemplate.update(
                "update sys_config set config_value = ?, updated_at = ? where config_key = ?",
                value, now, key);
        if (updated == 0) {
            jdbcTemplate.update(
                    "insert into sys_config (config_key, config_value, remark, updated_at) values (?,?,?,?)",
                    key, value, "发件邮箱", now);
        }
    }

    private int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (RuntimeException ex) {
            return fallback;
        }
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
