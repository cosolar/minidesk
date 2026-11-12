package cn.minims.desk.modules.work.task.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 统一事项表：待办 / 学习任务 / 日常计划 / 会议跟进。
 * user_id 由租户插件自动写入，实体不声明。
 */
@Data
@TableName("task")
public class TaskDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** work / study / life */
    private String module;

    /** todo / study / daily / followup */
    private String taskType;

    private String title;

    private String note;

    /** 1 高 / 2 中 / 3 低 */
    private Integer priority;

    /** todo / doing / done / cancelled */
    private String status;

    /** 开始日期：开发任务工期起点，可为空 */
    private LocalDate startDate;

    /** HH:mm，开始时间 */
    private String startTime;

    private LocalDate dueDate;

    /** HH:mm，便于同日排序 */
    private String dueTime;

    private LocalDateTime remindAt;

    private String repeatRule;

    private Long seriesId;

    private Long projectId;

    private Long planId;

    private Long parentId;

    private String refType;

    private Long refId;

    private String tags;

    private Integer estimateMinutes;

    private Integer sortOrder;

    private LocalDateTime doneAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
