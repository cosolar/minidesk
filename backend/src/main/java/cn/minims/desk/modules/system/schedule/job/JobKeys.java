package cn.minims.desk.modules.system.schedule.job;

/** Quartz JobDataMap 与 TriggerKey 的公共常量。 */
public final class JobKeys {

    public static final String GROUP = "minidesk";
    public static final String CODE = "jobCode";
    public static final String TRIGGER = "triggerBy";
    public static final String CRON = "cron";
    public static final String MANUAL = "manual";

    private JobKeys() {
    }
}
