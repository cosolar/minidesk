package cn.minims.desk.modules.work.task.vo;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 任务展示对象。
 */
public record TaskVO(
        String id,
        String title,
        String note,
        String module,
        String taskType,
        Integer priority,
        String status,
        LocalDate startDate,
        String startTime,
        LocalDate dueDate,
        String dueTime,
        Long projectId,
        String projectName,
        String tags,
        boolean done,
        LocalDateTime doneAt,
        LocalDateTime createdAt
) {
    public static TaskVO from(cn.minims.desk.modules.work.task.entity.TaskDO task) {
        return new TaskVO(
                String.valueOf(task.getId()),
                task.getTitle(),
                task.getNote(),
                task.getModule(),
                task.getTaskType(),
                task.getPriority(),
                task.getStatus(),
                task.getStartDate(),
                task.getStartTime(),
                task.getDueDate(),
                task.getDueTime(),
                task.getProjectId(),
                null,
                task.getTags(),
                "done".equals(task.getStatus()),
                task.getDoneAt(),
                task.getCreatedAt());
    }

    /** 批量补项目名用（避免 N+1，由 Service 一次性查好映射） */
    public TaskVO withProjectName(String name) {
        return new TaskVO(id, title, note, module, taskType, priority, status, startDate, startTime,
                dueDate, dueTime, projectId, name, tags, done, doneAt, createdAt);
    }

    public static List<TaskVO> emptyList() {
        return List.of();
    }
}
