package cn.minims.desk.modules.common.calendar.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

/**
 * 日程事件保存入参。
 */
public record CalendarEventSaveDTO(
        @NotBlank(message = "请输入日程标题")
        String title,

        String description,

        @NotNull(message = "请选择开始时间")
        LocalDateTime startTime,

        LocalDateTime endTime,

        Boolean allDay,

        String location,

        String color
) {
}
