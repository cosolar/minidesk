package cn.minims.desk.modules.study.vo;

import java.util.List;

/**
 * 本周学习统计。口径见设计文档：周起始为周一，增长率分母为 0 时返回 null。
 */
public record StudyWeekVO(
        long totalMinutes,
        long avgMinutesPerDay,
        Double growthRate,
        double targetCompletion,
        double targetHours,
        int streak,
        List<Day> days
) {
    public record Day(String date, String weekday, long minutes) {
    }
}
