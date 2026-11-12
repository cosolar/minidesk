package cn.minims.desk.modules.work.meeting.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

/**
 * 会议保存入参。参会人与跟进事项随会议一起提交，整体替换。
 */
public record MeetingSaveDTO(
        @NotBlank(message = "请输入会议主题")
        @Size(max = 200, message = "主题过长")
        String title,

        LocalDate meetingDate,

        String startTime,

        String endTime,

        String location,

        String host,

        String content,

        Long projectId,

        List<String> attendees,

        List<ActionItem> actions
) {
    public record ActionItem(String content, String owner, LocalDate dueDate) {
    }
}
