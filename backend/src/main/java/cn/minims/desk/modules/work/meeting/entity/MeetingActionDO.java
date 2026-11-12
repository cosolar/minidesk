package cn.minims.desk.modules.work.meeting.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDate;
import lombok.Data;

/**
 * 会议跟进事项。可直接转成任务，task_id 记录转换结果，避免重复转。
 */
@Data
@TableName("meeting_action")
public class MeetingActionDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long meetingId;

    private String content;

    private String owner;

    private LocalDate dueDate;

    private Long taskId;

    /** open / done */
    private String status;
}
