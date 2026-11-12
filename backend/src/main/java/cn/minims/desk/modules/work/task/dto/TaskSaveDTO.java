package cn.minims.desk.modules.work.task.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * 任务创建 / 编辑入参。
 */
public record TaskSaveDTO(
        @NotBlank(message = "请输入任务标题")
        @Size(max = 200, message = "标题过长")
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

        String repeatRule,

        Long projectId,

        Long planId,

        Long parentId,

        String tags,

        Integer estimateMinutes
) {
}
