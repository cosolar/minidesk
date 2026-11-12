package cn.minims.desk.modules.life.habit.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 打卡项。module 区分学习打卡与生活习惯。
 */
@Data
@TableName("habit")
public class HabitDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** study / life */
    private String module;

    private String name;

    private String icon;

    private String color;

    /** boolean / count */
    private String type;

    private Integer targetValue;

    private String unit;

    private String frequency;

    private String reminderTime;

    private Integer archived;

    private Integer sortOrder;

    private LocalDateTime createdAt;
}
