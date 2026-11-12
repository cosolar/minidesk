package cn.minims.desk.modules.common.calendar.vo;

/**
 * 日历统一条目：任务、事件、纪念日、倒数日聚合后的视图。
 * source 决定前端点击后的跳转目标。
 */
public record CalendarItemVO(
        String id,
        String source,
        String title,
        String start,
        boolean allDay,
        String color,
        boolean done
) {
}
