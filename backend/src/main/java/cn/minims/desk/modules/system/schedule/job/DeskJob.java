package cn.minims.desk.modules.system.schedule.job;

import cn.minims.desk.framework.spring.SpringContextHolder;
import cn.minims.desk.modules.system.schedule.service.ScheduleTaskRunner;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Quartz 与 Spring 之间的桥。
 *
 * <p>Quartz 自己 new Job 实例，拿不到 Spring 注入；这里只做一次上下文取回，
 * 具体业务都在 {@link ScheduleTaskRunner} 里，方便单独测试和手动触发复用。
 * 同一任务禁止并发，避免提醒重复推送、备份互相覆盖。
 */
@DisallowConcurrentExecution
public class DeskJob implements Job {

    private static final Logger log = LoggerFactory.getLogger(DeskJob.class);

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        String code = context.getMergedJobDataMap().getString(JobKeys.CODE);
        String triggerBy = context.getMergedJobDataMap().getString(JobKeys.TRIGGER);
        try {
            SpringContextHolder.bean(ScheduleTaskRunner.class)
                    .run(code, triggerBy == null ? JobKeys.CRON : triggerBy);
        } catch (RuntimeException ex) {
            // runner 内部已落执行日志，这里只记一条调度侧日志，避免 Quartz 无限重试
            log.warn("scheduled job failed, code={}", code, ex);
            throw new JobExecutionException(ex, false);
        }
    }
}
