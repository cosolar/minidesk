package cn.minims.desk.modules.work.meeting.vo;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 会议展示对象。
 */
public record MeetingVO(
        Long id,
        String title,
        LocalDate meetingDate,
        String startTime,
        String endTime,
        String location,
        String host,
        String content,
        Long projectId,
        List<String> attendees,
        List<ActionVO> actions,
        LocalDateTime createdAt
) {
    public record ActionVO(Long id, String content, String owner, LocalDate dueDate,
                           Long taskId, String status) {
    }
}
