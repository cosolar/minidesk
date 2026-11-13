package cn.minims.desk.modules.system.schedule.service;

import cn.minims.desk.common.api.ErrorCode;
import cn.minims.desk.common.exception.BizException;
import cn.minims.desk.modules.system.backup.service.BackupService;
import cn.minims.desk.modules.system.backup.vo.BackupVO;
import cn.minims.desk.modules.system.schedule.job.JobKeys;
import cn.minims.desk.modules.system.schedule.vo.ScheduleVO;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.quartz.TriggerKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;

/**
 * 定时任务执行器：Quartz 触发与后台手动触发共用同一条路径。
 *
 * <p>每次执行都写 job_exec_log 并回写 sys_schedule_job 的最近状态；
 * 任务内部统一用 JdbcTemplate 跨用户处理，不依赖登录态，也不走租户插件。
 */
@Service
public class ScheduleTaskRunner {

    private static final Logger log = LoggerFactory.getLogger(ScheduleTaskRunner.class);
    private static final DateTimeFormatter DATETIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int MAX_NOTICES = 200;
    private static final Set<String> RUNNING = ConcurrentHashMap.newKeySet();

    private final JdbcTemplate jdbc;
    private final Scheduler scheduler;
    private final BackupService backupService;

    public ScheduleTaskRunner(JdbcTemplate jdbc, Scheduler scheduler, BackupService backupService) {
        this.jdbc = jdbc;
        this.scheduler = scheduler;
        this.backupService = backupService;
    }

    /** 同步执行一个任务；同一任务不允许并发，结果立即返回给手动触发的调用方。 */
    public ScheduleVO.RunResult run(String code, String triggerBy) {
        if (code == null || code.isBlank()) {
            throw new BizException(ErrorCode.JOB_NOT_FOUND);
        }
        if (!RUNNING.add(code)) {
            throw new BizException(ErrorCode.JOB_RUNNING);
        }
        long begin = System.currentTimeMillis();
        String started = LocalDateTime.now().format(DATETIME);
        long logId = openLog(code, started, triggerBy);
        try {
            String message = dispatch(code);
            long cost = System.currentTimeMillis() - begin;
            closeLog(logId, true, cost, truncate(message, 500), null);
            updateJobState(code, "success", cost, null, started);
            return new ScheduleVO.RunResult(code, true, message, cost,
                    LocalDateTime.now().format(DATETIME));
        } catch (RuntimeException ex) {
            long cost = System.currentTimeMillis() - begin;
            String error = truncate(ex.getMessage(), 500);
            log.error("scheduled job failed, code={}, costMs={}", code, cost, ex);
            closeLog(logId, false, cost, null, error);
            updateJobState(code, "failed", cost, error, started);
            return new ScheduleVO.RunResult(code, false, error, cost,
                    LocalDateTime.now().format(DATETIME));
        } finally {
            RUNNING.remove(code);
        }
    }

    private String dispatch(String code) {
        return switch (code) {
            case "reminderScan" -> scanReminders();
            case "journalDraft" -> generateJournalDrafts();
            case "autoBackup" -> autoBackup();
            case "logCleanup" -> cleanupLogs();
            default -> throw new BizException(ErrorCode.JOB_NOT_FOUND);
        };
    }

    /** 逾期 / 今日到期任务与 24 小时内的日程，生成去重后的站内通知。 */
    private String scanReminders() {
        String today = LocalDate.now().toString();
        String dayStart = today + " 00:00:00";
        String now = LocalDateTime.now().format(DATETIME);
        String horizon = LocalDateTime.now().plusHours(24).format(DATETIME);
        List<Notice> notices = new ArrayList<>(64);

        notices.addAll(jdbc.query("""
                        select t.id, t.user_id, t.title, t.due_date
                        from task t
                        where t.deleted = 0 and t.status not in ('done', 'cancelled')
                          and t.due_date is not null and t.due_date <= ?
                          and not exists (
                              select 1 from notification n
                              where n.user_id = t.user_id and n.biz_type = 'task'
                                and n.biz_id = t.id and n.created_at >= ?)
                        limit ?
                        """,
                (rs, rowNum) -> new Notice(
                        rs.getLong("user_id"), rs.getLong("id"), "task",
                        "待办到期：" + rs.getString("title"),
                        "截止 " + rs.getString("due_date") + "，记得处理"),
                today, dayStart, MAX_NOTICES));

        notices.addAll(jdbc.query("""
                        select e.id, e.user_id, e.title, e.start_time
                        from calendar_event e
                        where e.deleted = 0 and e.start_time >= ? and e.start_time <= ?
                          and not exists (
                              select 1 from notification n
                              where n.user_id = e.user_id and n.biz_type = 'event'
                                and n.biz_id = e.id and n.created_at >= ?)
                        limit ?
                        """,
                (rs, rowNum) -> new Notice(
                        rs.getLong("user_id"), rs.getLong("id"), "event",
                        "日程提醒：" + rs.getString("title"),
                        "开始时间 " + rs.getString("start_time")),
                now, horizon, dayStart, MAX_NOTICES));

        if (notices.isEmpty()) {
            return "没有新增提醒";
        }
        jdbc.batchUpdate("""
                        insert into notification
                            (user_id, type, title, content, biz_type, biz_id, read_flag, created_at)
                        values (?, 'reminder', ?, ?, ?, ?, 0, ?)
                        """, notices, 100, (ps, item) -> {
                    ps.setLong(1, item.userId());
                    ps.setString(2, truncate(item.title(), 200));
                    ps.setString(3, truncate(item.content(), 500));
                    ps.setString(4, item.bizType());
                    ps.setLong(5, item.bizId());
                    ps.setString(6, now);
                });
        long tasks = notices.stream().filter(item -> "task".equals(item.bizType())).count();
        return "新增 " + notices.size() + " 条提醒（待办 " + tasks + " / 日程 "
                + (notices.size() - tasks) + "）";
    }

    /** 为还没有当日草稿的启用账号生成一份带真实统计的日报草稿。 */
    private String generateJournalDrafts() {
        String today = LocalDate.now().toString();
        String now = LocalDateTime.now().format(DATETIME);
        List<DraftRow> drafts = jdbc.query("""
                        select u.id as user_id,
                               (select count(*) from task t where t.user_id = u.id and t.deleted = 0
                                  and t.status = 'done' and substr(t.done_at, 1, 10) = ?) as done_count,
                               (select count(*) from task t where t.user_id = u.id and t.deleted = 0
                                  and substr(t.created_at, 1, 10) = ?) as created_count,
                               (select count(*) from note n where n.user_id = u.id and n.deleted = 0
                                  and substr(n.created_at, 1, 10) = ?) as note_count
                        from sys_user u
                        where u.deleted = 0 and u.status = 1
                          and not exists (
                              select 1 from journal j
                              where j.user_id = u.id and j.journal_type = 'daily'
                                and j.period_start = ? and j.auto_generated = 1 and j.deleted = 0)
                        """,
                (rs, rowNum) -> new DraftRow(
                        rs.getLong("user_id"), rs.getLong("done_count"),
                        rs.getLong("created_count"), rs.getLong("note_count")),
                today, today, today, today);

        if (drafts.isEmpty()) {
            return "所有账号的当日草稿已存在";
        }
        String title = today + " 日报";
        jdbc.batchUpdate("""
                        insert into journal
                            (user_id, journal_type, title, period_start, period_end, template_key,
                             content, stats_json, auto_generated, created_at, updated_at, deleted)
                        values (?, 'daily', ?, ?, ?, 'standard', ?, ?, 1, ?, ?, 0)
                        """, drafts, 100, (ps, item) -> {
                    String content = "## 今日完成\n\n" + item.doneCount() + " 项任务\n\n"
                            + "## 新增待办\n\n" + item.createdCount() + " 项\n\n"
                            + "## 笔记\n\n" + item.noteCount() + " 篇\n\n"
                            + "> 由定时任务生成，可直接编辑补充。";
                    ps.setLong(1, item.userId());
                    ps.setString(2, title);
                    ps.setString(3, today);
                    ps.setString(4, today);
                    ps.setString(5, content);
                    ps.setString(6, "{\"done\":" + item.doneCount()
                            + ",\"created\":" + item.createdCount()
                            + ",\"notes\":" + item.noteCount() + "}");
                    ps.setString(7, now);
                    ps.setString(8, now);
                });
        return "生成 " + drafts.size() + " 份日报草稿";
    }

    /** 自动快照 + 按站点配置保留份数清理历史备份。 */
    private String autoBackup() {
        BackupVO.BackupFile file = backupService.create("auto");
        int removed = backupService.cleanup(configInt("backup_keep", 7));
        return "已生成 " + file.fileName() + "（" + file.sizeText() + "），清理旧备份 " + removed + " 份";
    }

    /** 按 audit_keep_days 清理登录、操作、任务执行日志与已读通知。 */
    private String cleanupLogs() {
        int keepDays = Math.max(configInt("audit_keep_days", 180), 1);
        String cutoff = LocalDateTime.now().minusDays(keepDays).format(DATETIME);
        int login = jdbc.update("delete from sys_login_log where created_at < ?", cutoff);
        int oper = jdbc.update("delete from sys_oper_log where created_at < ?", cutoff);
        int job = jdbc.update("delete from job_exec_log where started_at < ?", cutoff);
        int notice = jdbc.update("delete from notification where read_flag = 1 and created_at < ?", cutoff);
        return "清理 " + (login + oper + job + notice) + " 条（登录 " + login + " / 操作 " + oper
                + " / 任务 " + job + " / 通知 " + notice + "），保留近 " + keepDays + " 天";
    }

    private int configInt(String key, int fallback) {
        try {
            String value = jdbc.queryForObject(
                    "select config_value from sys_config where config_key = ?", String.class, key);
            return value == null ? fallback : Integer.parseInt(value.trim());
        } catch (RuntimeException ex) {
            log.debug("read config {} failed, use fallback {}", key, fallback);
            return fallback;
        }
    }

    private long openLog(String code, String started, String triggerBy) {
        KeyHolder holder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                            insert into job_exec_log (job_name, started_at, success, trigger_by)
                            values (?, ?, 0, ?)
                            """, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, code);
            ps.setString(2, started);
            ps.setString(3, triggerBy);
            return ps;
        }, holder);
        Number key = holder.getKey();
        return key == null ? 0L : key.longValue();
    }

    private void closeLog(long logId, boolean success, long cost, String message, String error) {
        if (logId <= 0) {
            return;
        }
        jdbc.update("""
                        update job_exec_log
                        set finished_at = ?, success = ?, cost_ms = ?, message = ?, error_msg = ?
                        where id = ?
                        """,
                LocalDateTime.now().format(DATETIME), success ? 1 : 0, cost, message, error, logId);
    }

    private void updateJobState(String code, String status, long cost, String error, String started) {
        jdbc.update("""
                        update sys_schedule_job
                        set last_run_at = ?, last_status = ?, last_cost_ms = ?, last_error = ?,
                            next_run_at = ?, updated_at = ?
                        where job_code = ?
                        """,
                started, status, cost, error, nextRunAt(code),
                LocalDateTime.now().format(DATETIME), code);
    }

    /** 直接读 Quartz trigger 的下次触发时间，比按 cron 自己算更准。 */
    private String nextRunAt(String code) {
        try {
            Trigger trigger = scheduler.getTrigger(TriggerKey.triggerKey(code, JobKeys.GROUP));
            if (trigger == null || trigger.getNextFireTime() == null) {
                return null;
            }
            Date next = trigger.getNextFireTime();
            return LocalDateTime.ofInstant(next.toInstant(), ZoneId.systemDefault()).format(DATETIME);
        } catch (SchedulerException ex) {
            log.debug("read next fire time failed, code={}", code);
            return null;
        }
    }

    private static String truncate(String value, int max) {
        if (value == null || value.length() <= max) {
            return value;
        }
        return value.substring(0, max);
    }

    private record Notice(long userId, long bizId, String bizType, String title, String content) {
    }

    private record DraftRow(long userId, long doneCount, long createdCount, long noteCount) {
    }
}
