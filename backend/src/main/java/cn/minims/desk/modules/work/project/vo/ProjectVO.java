package cn.minims.desk.modules.work.project.vo;

import java.time.LocalDate;

/**
 * 项目展示对象，含任务统计与进度。
 */
public record ProjectVO(
        String id,
        String name,
        String description,
        String color,
        String status,
        Integer priority,
        LocalDate startDate,
        LocalDate endDate,
        int progress,
        Stats stats
) {
    public record Stats(long total, long done, long doing, long overdue) {
    }
}
