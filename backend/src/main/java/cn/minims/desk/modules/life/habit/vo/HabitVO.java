package cn.minims.desk.modules.life.habit.vo;

import java.util.List;

/**
 * 打卡项展示对象。
 */
public record HabitVO(
        String id,
        String module,
        String name,
        String icon,
        String color,
        String type,
        Integer targetValue,
        String unit,
        int todayValue,
        boolean checked,
        int streak
) {
    public record Heatmap(
            String habitId,
            String name,
            int year,
            int currentStreak,
            int longestStreak,
            int totalDays,
            List<Day> days
    ) {
        public record Day(String date, int value) {
        }
    }
}
