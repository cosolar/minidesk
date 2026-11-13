package cn.minims.desk.modules.system.schedule.service;

import cn.minims.desk.common.api.ErrorCode;
import cn.minims.desk.common.api.PageResult;
import cn.minims.desk.common.exception.BizException;
import cn.minims.desk.modules.system.schedule.job.DeskJob;
import cn.minims.desk.modules.system.schedule.job.JobKeys;
import cn.minims.desk.modules.system.schedule.vo.ScheduleVO;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.quartz.CronExpression;
import org.quartz.CronScheduleBuilder;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.quartz.TriggerKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 定时任务管理：库里的定义是唯一事实来源，Quartz trigger 由它重建。
 *
 * <p>更新 cron / 启停时先删旧 job 再按新定义注册，比 pause + reschedule 的组合更不容易
 * 留下僵尸 trigger；单机 RAMJobStore 下重建成本可以忽略。
 */
@Service
public class ScheduleService {

    private static final Logger log = LoggerFactory.getLogger(ScheduleService.class);
    private static final DateTimeFormatter DATETIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final List<ScheduleVO.JobItem> DEFAULTS = List.of(
            new ScheduleVO.JobItem("reminderScan", "待办与日程提醒", "扫描逾期任务与 24 小时内日程，生成站内通知",
                    "0 0/15 * * * ?", true, true, 10, null, null, null, null, null),
            new ScheduleVO.JobItem("journalDraft", "日报草稿生成", "为还没有当日草稿的账号生成一份带统计的日报",
                    "0 30 22 * * ?", true, true, 20, null, null, null, null, null),
            new ScheduleVO.JobItem("autoBackup", "自动备份", "生成数据库与附件快照，并按保留份数清理旧备份",
                    "0 0 3 * * ?", true, true, 30, null, null, null, null, null),
            new ScheduleVO.JobItem("logCleanup", "日志清理", "按审计保留天数清理登录、操作、任务日志与已读通知",
                    "0 0 4 1 * ?", true, true, 40, null, null, null, null, null));

    private static final String JOB_COLUMNS = """
            job_code, job_name, description, cron_expr, enabled, allow_manual, sort_order,
            last_run_at, last_status, last_cost_ms, last_error, next_run_at
            """;

    private static final RowMapper<ScheduleVO.JobItem> JOB_MAPPER = (rs, rowNum) -> new ScheduleVO.JobItem(
            rs.getString("job_code"), rs.getString("job_name"), rs.getString("description"),
            rs.getString("cron_expr"), rs.getInt("enabled") == 1, rs.getInt("allow_manual") == 1,
            rs.getInt("sort_order"), rs.getString("last_run_at"), rs.getString("last_status"),
            nullableLong(rs, "last_cost_ms"), rs.getString("last_error"), rs.getString("next_run_at"));

    private final JdbcTemplate jdbc;
    private final Scheduler scheduler;
    private final ScheduleTaskRunner runner;

    public ScheduleService(JdbcTemplate jdbc, Scheduler scheduler, ScheduleTaskRunner runner) {
        this.jdbc = jdbc;
        this.scheduler = scheduler;
        this.runner = runner;
    }

    /** 首次启动写入内置任务；已有配置一律保留，用户改过的 cron 不会被覆盖。 */
    @Transactional(rollbackFor = Exception.class)
    public void ensureDefaults() {
        String now = LocalDateTime.now().format(DATETIME);
        for (ScheduleVO.JobItem item : DEFAULTS) {
            jdbc.update("""
                            insert or ignore into sys_schedule_job
                                (job_code, job_name, description, cron_expr, enabled,
                                 allow_manual, sort_order, created_at, updated_at)
                            values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                            """,
                    item.code(), item.name(), item.description(), item.cronExpr(),
                    item.enabled() ? 1 : 0, item.allowManual() ? 1 : 0,
                    item.sortOrder(), now, now);
        }
    }

    /** 启动时按库里的定义重建全部 trigger。 */
    public void reload() {
        try {
            scheduler.clear();
            for (ScheduleVO.JobItem item : listFromDb()) {
                sync(item.code(), item.cronExpr(), item.enabled());
            }
            scheduler.start();
            log.info("scheduler ready, {} triggers registered", countEnabled());
        } catch (SchedulerException ex) {
            log.error("scheduler startup failed", ex);
            throw new IllegalStateException("scheduler startup failed", ex);
        }
    }

    public List<ScheduleVO.JobItem> list() {
        List<ScheduleVO.JobItem> rows = listFromDb();
        List<ScheduleVO.JobItem> result = new ArrayList<>(rows.size());
        for (ScheduleVO.JobItem item : rows) {
            String next = item.enabled() ? nextRunAt(item.code()) : null;
            result.add(new ScheduleVO.JobItem(item.code(), item.name(), item.description(),
                    item.cronExpr(), item.enabled(), item.allowManual(), item.sortOrder(),
                    item.lastRunAt(), item.lastStatus(), item.lastCostMs(), item.lastError(),
                    next == null ? (item.enabled() ? item.nextRunAt() : null) : next));
        }
        return result;
    }

    /** 更新 cron / 启停，随后立刻同步调度器；非法 cron 直接拒绝，不会把库写坏。 */
    @Transactional(rollbackFor = Exception.class)
    public List<ScheduleVO.JobItem> update(String code, String cronExpr, Boolean enabled) {
        ScheduleVO.JobItem current = find(code);
        String newCron = cronExpr == null || cronExpr.isBlank() ? current.cronExpr() : cronExpr.trim();
        if (!CronExpression.isValidExpression(newCron)) {
            throw new BizException(ErrorCode.JOB_CRON_INVALID);
        }
        boolean newEnabled = enabled == null ? current.enabled() : enabled;
        jdbc.update("""
                        update sys_schedule_job
                        set cron_expr = ?, enabled = ?, next_run_at = ?, updated_at = ?
                        where job_code = ?
                        """,
                newCron, newEnabled ? 1 : 0, null,
                LocalDateTime.now().format(DATETIME), code);
        try {
            sync(code, newCron, newEnabled);
        } catch (SchedulerException ex) {
            log.error("sync trigger failed, code={}", code, ex);
            throw new BizException(ErrorCode.SERVER_ERROR, "调度器同步失败");
        }
        return list();
    }

    public ScheduleVO.RunResult trigger(String code) {
        ScheduleVO.JobItem job = find(code);
        if (!job.allowManual()) {
            throw new BizException(ErrorCode.FORBIDDEN, "该任务不允许手动触发");
        }
        return runner.run(code, JobKeys.MANUAL);
    }

    public PageResult<ScheduleVO.JobLog> logs(String jobCode, long page, long size) {
        String where = jobCode == null || jobCode.isBlank() ? "" : " where job_name = ? ";
        List<Object> countArgs = new ArrayList<>(2);
        if (!where.isEmpty()) {
            countArgs.add(jobCode.trim());
        }
        long total = count("select count(*) from job_exec_log" + where, countArgs.toArray());

        String sql = """
                select id, job_name, started_at, finished_at, success, cost_ms,
                       message, error_msg, trigger_by
                from job_exec_log
                """ + where + " order by id desc limit ? offset ?";
        List<Object> args = new ArrayList<>(countArgs);
        args.add(size);
        args.add((page - 1) * size);
        List<ScheduleVO.JobLog> rows = jdbc.query(sql, (rs, rowNum) -> new ScheduleVO.JobLog(
                String.valueOf(rs.getLong("id")), rs.getString("job_name"),
                rs.getString("started_at"), rs.getString("finished_at"),
                rs.getInt("success") == 1, nullableLong(rs, "cost_ms"),
                rs.getString("message"), rs.getString("error_msg"), rs.getString("trigger_by")),
                args.toArray());
        return PageResult.of(rows, total, page, size);
    }

    private ScheduleVO.JobItem find(String code) {
        List<ScheduleVO.JobItem> rows = jdbc.query(
                "select " + JOB_COLUMNS + " from sys_schedule_job where job_code = ?", JOB_MAPPER, code);
        if (rows.isEmpty()) {
            throw new BizException(ErrorCode.JOB_NOT_FOUND);
        }
        return rows.get(0);
    }

    private List<ScheduleVO.JobItem> listFromDb() {
        return jdbc.query("select " + JOB_COLUMNS
                + " from sys_schedule_job order by sort_order, id", JOB_MAPPER);
    }

    private long countEnabled() {
        Long value = jdbc.queryForObject(
                "select count(*) from sys_schedule_job where enabled = 1", Long.class);
        return value == null ? 0 : value;
    }

    private long count(String sql, Object... args) {
        Long value = jdbc.queryForObject(sql, Long.class, args);
        return value == null ? 0 : value;
    }

    private void sync(String code, String cron, boolean enabled) throws SchedulerException {
        scheduler.deleteJob(JobKey.jobKey(code, JobKeys.GROUP));
        if (!enabled) {
            return;
        }
        JobDetail detail = JobBuilder.newJob(DeskJob.class)
                .withIdentity(code, JobKeys.GROUP)
                .usingJobData(JobKeys.CODE, code)
                .build();
        Trigger trigger = TriggerBuilder.newTrigger()
                .withIdentity(code, JobKeys.GROUP)
                .forJob(detail)
                .withSchedule(CronScheduleBuilder.cronSchedule(cron)
                        .withMisfireHandlingInstructionDoNothing())
                .build();
        scheduler.scheduleJob(detail, trigger);
        jdbc.update("update sys_schedule_job set next_run_at = ? where job_code = ?",
                nextRunAt(code), code);
    }

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

    private static Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }
}
