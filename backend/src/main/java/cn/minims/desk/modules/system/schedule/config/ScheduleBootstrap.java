package cn.minims.desk.modules.system.schedule.config;

import cn.minims.desk.modules.system.schedule.service.ScheduleService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 启动顺序：字段升级(1) → 调度定义与 trigger 重建(3) → 其余种子数据。
 * trigger 必须在调度器 start 之前注册完，否则首次触发时间会算错。
 */
@Component
@Order(3)
public class ScheduleBootstrap implements ApplicationRunner {

    private final ScheduleService scheduleService;

    public ScheduleBootstrap(ScheduleService scheduleService) {
        this.scheduleService = scheduleService;
    }

    @Override
    public void run(ApplicationArguments args) {
        scheduleService.ensureDefaults();
        scheduleService.reload();
    }
}
