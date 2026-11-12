package cn.minims.desk.modules.life.habit.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 打卡记录，一天一条（habit_id + log_date 唯一）。
 */
@Data
@TableName("habit_log")
public class HabitLogDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long habitId;

    private LocalDate logDate;

    private Integer value;

    private String note;

    private LocalDateTime createdAt;
}
