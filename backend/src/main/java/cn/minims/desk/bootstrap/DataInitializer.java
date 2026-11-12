package cn.minims.desk.bootstrap;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 启动种子数据：角色、权限、站点配置。
 *
 * <p>使用 INSERT OR IGNORE 保证幂等（SQLite 方言；切换 MySQL / PG 时改为 ON CONFLICT / ON DUPLICATE KEY）。
 */
@Component
public class DataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);
    private static final DateTimeFormatter DATETIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final JdbcTemplate jdbcTemplate;

    public DataInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void run(ApplicationArguments args) {
        String now = LocalDateTime.now().format(DATETIME);

        // 内置角色
        jdbcTemplate.update(
                "insert or ignore into sys_role (code, name, built_in, remark, created_at) values (?,?,?,?,?)",
                "ADMIN", "管理员", 1, "系统管理：账号、备份、调度、集成配置", now);
        jdbcTemplate.update(
                "insert or ignore into sys_role (code, name, built_in, remark, created_at) values (?,?,?,?,?)",
                "MEMBER", "家庭成员", 1, "全部业务功能，无系统管理权限", now);

        Long adminId = jdbcTemplate.queryForObject(
                "select id from sys_role where code = 'ADMIN'", Long.class);
        Long memberId = jdbcTemplate.queryForObject(
                "select id from sys_role where code = 'MEMBER'", Long.class);

        // 权限授予：ADMIN 用通配符，MEMBER 按模块
        insertPermission(adminId, "*");
        for (String code : new String[]{"work:*", "study:*", "life:*", "shared:*", "system:setting:update"}) {
            insertPermission(memberId, code);
        }

        // 站点配置
        insertConfig("site_name", "MiniDesk", "站点名称", now);
        insertConfig("allow_register", "true", "是否开放自助注册", now);
        insertConfig("install_claimed", "false", "首个管理员是否已被注册领取", now);
        insertConfig("password_min_length", "8", "密码最小长度", now);
        insertConfig("login_fail_limit", "5", "登录失败锁定阈值", now);
        insertConfig("login_lock_minutes", "15", "锁定时长（分钟）", now);
        insertConfig("audit_keep_days", "180", "审计日志保留天数", now);
        insertConfig("backup_keep", "7", "备份保留份数", now);

        log.info("seed data ready");
    }

    private void insertPermission(Long roleId, String code) {
        Integer exists = jdbcTemplate.queryForObject(
                "select count(*) from sys_role_permission where role_id = ? and permission_code = ?",
                Integer.class, roleId, code);
        if (exists != null && exists > 0) {
            return;
        }
        jdbcTemplate.update(
                "insert into sys_role_permission (role_id, permission_code) values (?,?)", roleId, code);
    }

    private void insertConfig(String key, String value, String remark, String now) {
        jdbcTemplate.update(
                "insert or ignore into sys_config (config_key, config_value, remark, updated_at) values (?,?,?,?)",
                key, value, remark, now);
    }
}
