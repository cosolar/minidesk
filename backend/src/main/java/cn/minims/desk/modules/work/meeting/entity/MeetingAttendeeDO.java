package cn.minims.desk.modules.work.meeting.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 参会人。会议是独立实体的原因见 AGENTS.md：纪要要能挂参会人、跟进事项、项目。
 */
@Data
@TableName("meeting_attendee")
public class MeetingAttendeeDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long meetingId;

    private String name;

    private String role;

    private Long contactId;
}
