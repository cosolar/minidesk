package cn.minims.desk.modules.work.meeting.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 会议纪要。
 */
@Data
@TableName("meeting")
public class MeetingDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String title;

    private LocalDate meetingDate;

    private String startTime;

    private String endTime;

    private String location;

    private String host;

    /** Markdown（Vditor） */
    private String content;

    private Long projectId;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
