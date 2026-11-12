package cn.minims.desk.modules.common.calendar.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 倒数日。
 */
@Data
@TableName("countdown")
public class CountdownDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String title;

    private LocalDate targetDate;

    private String type;

    private String icon;

    private String color;

    private Integer pinned;

    private String note;

    private LocalDateTime createdAt;
}
