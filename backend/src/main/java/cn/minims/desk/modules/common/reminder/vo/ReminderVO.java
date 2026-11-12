package cn.minims.desk.modules.common.reminder.vo;

import java.time.LocalDate;

/**
 * 提醒条目。所有来源聚合成同一种结构，前端只按 level 配色、按 kind 分组。
 *
 * @param days 距今天数，负数表示已逾期
 */
public record ReminderVO(
        String id,
        String kind,
        String title,
        String subtitle,
        LocalDate date,
        long days,
        String level,
        String route,
        boolean done
) {
}
