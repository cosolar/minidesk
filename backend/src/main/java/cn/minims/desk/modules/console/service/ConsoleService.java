package cn.minims.desk.modules.console.service;

import cn.hutool.core.util.RandomUtil;
import cn.hutool.crypto.digest.BCrypt;
import cn.minims.desk.common.api.ErrorCode;
import cn.minims.desk.common.api.PageResult;
import cn.minims.desk.common.exception.BizException;
import cn.minims.desk.framework.tenant.TenantContext;
import cn.minims.desk.modules.console.vo.ConsoleVO;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 管理后台业务。
 *
 * <p>跨用户统计与账号管理用 JdbcTemplate 直接查（不走租户插件），
 * 且只做计数与账号字段，**不读取任何用户业务正文**。
 */
@Service
public class ConsoleService {

    private static final Logger log = LoggerFactory.getLogger(ConsoleService.class);
    private static final DateTimeFormatter DATETIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final JdbcTemplate jdbc;

    @Value("${minidesk.data-dir:./data}")
    private String dataDir;

    public ConsoleService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public ConsoleVO.Dashboard dashboard() {
        long totalUsers = count("select count(*) from sys_user where deleted = 0");
        long active7d = count("select count(*) from sys_user where deleted = 0 and last_login_at >= datetime('now','-7 days')");
        long disabled = count("select count(*) from sys_user where deleted = 0 and status = 0");
        long tasks = count("select count(*) from task where deleted = 0");
        long notes = count("select count(*) from note where deleted = 0");
        long ledgers = count("select count(*) from ledger where deleted = 0");
        long lifeLogs = count("select count(*) from life_log where deleted = 0");

        double dbSizeMb = 0;
        try {
            Path db = Path.of(dataDir, "minidesk.db");
            if (Files.exists(db)) {
                dbSizeMb = Math.round(Files.size(db) / 1024.0 / 102.4) / 10.0;
            }
        } catch (IOException ex) {
            log.warn("read db size failed", ex);
        }

        long uptimeHours = java.lang.management.ManagementFactory.getRuntimeMXBean().getUptime() / 3_600_000;

        return new ConsoleVO.Dashboard(totalUsers, active7d, disabled, tasks, notes, ledgers,
                lifeLogs, dbSizeMb, uptimeHours, false);
    }

    public PageResult<ConsoleVO.UserRow> users(String keyword, long page, long size) {
        String where = " where u.deleted = 0 ";
        List<Object> args = new ArrayList<>();
        if (StringUtils.hasText(keyword)) {
            where += " and (u.username like ? or u.nickname like ?) ";
            args.add("%" + keyword.trim() + "%");
            args.add("%" + keyword.trim() + "%");
        }

        long total = count("select count(*) from sys_user u" + where, args.toArray());

        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(size);
        pageArgs.add((page - 1) * size);

        List<ConsoleVO.UserRow> rows = jdbc.query("""
                        select u.id, u.username, u.nickname, u.status, u.source,
                               u.last_login_at, u.created_at,
                               (select count(*) from task t where t.user_id = u.id and t.deleted = 0) as task_count,
                               (select count(*) from note n where n.user_id = u.id and n.deleted = 0) as note_count,
                               (select count(*) from ledger l where l.user_id = u.id and l.deleted = 0) as ledger_count,
                               (select group_concat(r.code) from sys_user_role ur
                                  join sys_role r on r.id = ur.role_id where ur.user_id = u.id) as roles
                        from sys_user u
                        """ + where + " order by u.id limit ? offset ?",
                (rs, rowNum) -> {
                    String roles = rs.getString("roles");
                    return new ConsoleVO.UserRow(
                            String.valueOf(rs.getLong("id")),
                            rs.getString("username"),
                            rs.getString("nickname"),
                            rs.getInt("status"),
                            roles == null ? List.of() : List.of(roles.split(",")),
                            rs.getString("source"),
                            rs.getLong("task_count"),
                            rs.getLong("note_count"),
                            rs.getLong("ledger_count"),
                            rs.getString("last_login_at"),
                            rs.getString("created_at"));
                },
                pageArgs.toArray());

        return PageResult.of(rows, total, page, size);
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long userId, boolean enabled) {
        long current = TenantContext.requireUserId();
        if (current == userId) {
            throw new BizException(ErrorCode.SELF_OPERATION_DENIED, "不能禁用自己的账号");
        }
        if (!enabled) {
            ensureNotLastAdmin(userId);
        }
        jdbc.update("update sys_user set status = ?, updated_at = ? where id = ?",
                enabled ? 1 : 0, LocalDateTime.now().format(DATETIME), userId);
    }

    @Transactional(rollbackFor = Exception.class)
    public ConsoleVO.ResetResult resetPassword(Long userId) {
        // 一次性临时密码：只在本次响应里出现，库里只存哈希
        String temp = RandomUtil.randomString("ABCDEFGHJKMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789", 12) + "@A1";
        int updated = jdbc.update(
                "update sys_user set password_hash = ?, pwd_reset_required = 1, updated_at = ? where id = ?",
                BCrypt.hashpw(temp, BCrypt.gensalt()),
                LocalDateTime.now().format(DATETIME), userId);
        if (updated == 0) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        return new ConsoleVO.ResetResult(temp);
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteUser(Long userId) {
        long current = TenantContext.requireUserId();
        if (current == userId) {
            throw new BizException(ErrorCode.SELF_OPERATION_DENIED, "不能删除自己的账号");
        }
        ensureNotLastAdmin(userId);
        jdbc.update("update sys_user set deleted = 1, updated_at = ? where id = ?",
                LocalDateTime.now().format(DATETIME), userId);
    }

    @Transactional(rollbackFor = Exception.class)
    public void createUser(String username, String nickname, String password, String roleCode) {
        if (!StringUtils.hasText(username) || !StringUtils.hasText(password)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "用户名与密码不能为空");
        }
        Long exists = jdbc.queryForObject("select count(*) from sys_user where username = ? and deleted = 0",
                Long.class, username.trim());
        if (exists != null && exists > 0) {
            throw new BizException(ErrorCode.USERNAME_EXISTS);
        }
        String now = LocalDateTime.now().format(DATETIME);
        jdbc.update("""
                        insert into sys_user (username, password_hash, nickname, status, source,
                                              pwd_reset_required, login_count, created_at, updated_at, deleted)
                        values (?, ?, ?, 1, 'admin', 1, 0, ?, ?, 0)
                        """,
                username.trim(), BCrypt.hashpw(password, BCrypt.gensalt()),
                StringUtils.hasText(nickname) ? nickname.trim() : "用户", now, now);
        Long userId = jdbc.queryForObject("select id from sys_user where username = ?", Long.class, username.trim());
        Long roleId = jdbc.queryForObject("select id from sys_role where code = ?", Long.class,
                StringUtils.hasText(roleCode) ? roleCode : "MEMBER");
        jdbc.update("insert into sys_user_role (user_id, role_id) values (?, ?)", userId, roleId);
        jdbc.update("""
                        insert into sys_user_setting (user_id, theme, sidebar_collapsed, city, week_start, created_at, updated_at)
                        values (?, 'auto', 0, '北京', 1, ?, ?)
                        """, userId, now, now);
    }

    public List<ConsoleVO.ConfigItem> config() {
        return jdbc.query("select config_key, config_value, remark from sys_config order by id",
                (rs, rowNum) -> new ConsoleVO.ConfigItem(
                        rs.getString("config_key"), rs.getString("config_value"), rs.getString("remark")));
    }

    @Transactional(rollbackFor = Exception.class)
    public List<ConsoleVO.ConfigItem> updateConfig(Map<String, String> values) {
        String now = LocalDateTime.now().format(DATETIME);
        values.forEach((key, value) -> jdbc.update(
                "update sys_config set config_value = ?, updated_at = ? where config_key = ?", value, now, key));
        return config();
    }

    public PageResult<ConsoleVO.LoginLog> loginLogs(long page, long size) {
        long total = count("select count(*) from sys_login_log");
        List<ConsoleVO.LoginLog> rows = jdbc.query("""
                        select id, username, success, fail_reason, ip, created_at
                        from sys_login_log order by id desc limit ? offset ?
                        """,
                (rs, rowNum) -> new ConsoleVO.LoginLog(
                        String.valueOf(rs.getLong("id")),
                        rs.getString("username"),
                        rs.getInt("success") == 1,
                        rs.getString("fail_reason"),
                        rs.getString("ip"),
                        rs.getString("created_at")),
                size, (page - 1) * size);
        return PageResult.of(rows, total, page, size);
    }

    public PageResult<ConsoleVO.OperLog> operLogs(long page, long size, String keyword, String result) {
        StringBuilder where = new StringBuilder(64).append(" where 1 = 1 ");
        List<Object> args = new ArrayList<>(8);
        if (StringUtils.hasText(keyword)) {
            where.append(" and (username like ? or module like ? or action like ? or target_id like ?) ");
            String pattern = "%" + keyword.trim() + "%";
            args.add(pattern);
            args.add(pattern);
            args.add(pattern);
            args.add(pattern);
        }
        if (StringUtils.hasText(result)) {
            where.append(" and result = ? ");
            args.add(result.trim());
        }
        long total = count("select count(*) from sys_oper_log" + where, args.toArray());

        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(size);
        pageArgs.add((page - 1) * size);
        List<ConsoleVO.OperLog> rows = jdbc.query("""
                        select id, username, module, action, target_type, target_id, result,
                               created_at, params, error_msg, cost_ms, trace_id
                        from sys_oper_log
                        """ + where + " order by id desc limit ? offset ?",
                (rs, rowNum) -> {
                    long cost = rs.getLong("cost_ms");
                    Long costMs = rs.wasNull() ? null : cost;
                    return new ConsoleVO.OperLog(
                            String.valueOf(rs.getLong("id")),
                            rs.getString("username"),
                            rs.getString("module"),
                            rs.getString("action"),
                            rs.getString("target_type"),
                            rs.getString("target_id"),
                            rs.getString("result"),
                            rs.getString("created_at"),
                            rs.getString("params"),
                            rs.getString("error_msg"),
                            costMs,
                            rs.getString("trace_id"));
                },
                pageArgs.toArray());
        return PageResult.of(rows, total, page, size);
    }

    /** 最后一个管理员保护：只剩一个启用状态的 ADMIN 时拒绝禁用/删除 */
    private void ensureNotLastAdmin(Long userId) {
        Integer isAdmin = jdbc.queryForObject("""
                        select count(*) from sys_user_role ur join sys_role r on r.id = ur.role_id
                        where ur.user_id = ? and r.code = 'ADMIN'
                        """, Integer.class, userId);
        if (isAdmin == null || isAdmin == 0) {
            return;
        }
        Integer admins = jdbc.queryForObject("""
                        select count(distinct u.id) from sys_user u
                        join sys_user_role ur on ur.user_id = u.id
                        join sys_role r on r.id = ur.role_id
                        where u.deleted = 0 and u.status = 1 and r.code = 'ADMIN'
                        """, Integer.class);
        if (admins != null && admins <= 1) {
            throw new BizException(ErrorCode.LAST_ADMIN_PROTECTED);
        }
    }

    private long count(String sql, Object... args) {
        Long value = jdbc.queryForObject(sql, Long.class, args);
        return value == null ? 0 : value;
    }

    /** 供前端下拉：可选角色 */
    public List<Map<String, Object>> roles() {
        return jdbc.queryForList("select id, code, name from sys_role order by id");
    }

    /** 管理员创建用户时的默认配置（占位，便于后续扩展） */
    public Map<String, String> defaults() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("city", "北京");
        map.put("theme", "auto");
        return map;
    }
}
