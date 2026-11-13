package cn.minims.desk.modules.system.schedule.vo;

/** 定时任务相关的视图对象。 */
public final class ScheduleVO {

    private ScheduleVO() {
    }

    public record JobItem(
            String code,
            String name,
            String description,
            String cronExpr,
            boolean enabled,
            boolean allowManual,
            int sortOrder,
            String lastRunAt,
            String lastStatus,
            Long lastCostMs,
            String lastError,
            String nextRunAt
    ) {
    }

    public record JobLog(
            String id,
            String jobName,
            String startedAt,
            String finishedAt,
            boolean success,
            Long costMs,
            String message,
            String errorMsg,
            String triggerBy
    ) {
    }

    public record JobUpdate(String cronExpr, Boolean enabled) {
    }

    public record RunResult(
            String jobCode,
            boolean success,
            String message,
            long costMs,
            String finishedAt
    ) {
    }
}
