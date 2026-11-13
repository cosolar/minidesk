package cn.minims.desk.modules.system.schedule.config;

import java.util.Properties;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.impl.StdSchedulerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Quartz 调度器。
 *
 * <p>任务定义、启停状态与执行历史持久化在 sys_schedule_job / job_exec_log，
 * 触发器放在 RAMJobStore，启动时按库里的配置重建。自托管单机场景下这样最省事：
 * 不需要 QRTZ_* 表、不需要处理 SQLite 的 SELECT ... FOR UPDATE 方言差异，
 * 也避免调度器与业务库抢连接。代价是不支持集群与 misfire 补跑。
 */
@Configuration
public class QuartzConfig {

    private static final int THREAD_COUNT = 4;

    @Bean(destroyMethod = "shutdown")
    public Scheduler deskScheduler() throws SchedulerException {
        Properties props = new Properties();
        props.setProperty("org.quartz.scheduler.instanceName", "MiniDeskScheduler");
        props.setProperty("org.quartz.scheduler.instanceId", "AUTO");
        props.setProperty("org.quartz.scheduler.skipUpdateCheck", "true");
        props.setProperty("org.quartz.scheduler.makeSchedulerThreadDaemon", "true");
        props.setProperty("org.quartz.threadPool.class", "org.quartz.simpl.SimpleThreadPool");
        props.setProperty("org.quartz.threadPool.threadCount", String.valueOf(THREAD_COUNT));
        props.setProperty("org.quartz.threadPool.threadPriority", "5");
        props.setProperty("org.quartz.threadPool.makeThreadsDaemons", "true");
        props.setProperty("org.quartz.jobStore.class", "org.quartz.simpl.RAMJobStore");
        props.setProperty("org.quartz.jobStore.misfireThreshold", "60000");
        return new StdSchedulerFactory(props).getScheduler();
    }
}
