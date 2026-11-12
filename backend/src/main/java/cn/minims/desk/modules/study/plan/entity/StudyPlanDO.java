package cn.minims.desk.modules.study.plan.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 学习计划：年（level=1）→ 月（2）→ 周（3），用 parent_id 串成树。
 */
@Data
@TableName("study_plan")
public class StudyPlanDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Integer level;

    private Long parentId;

    private String title;

    private BigDecimal targetHours;

    private BigDecimal doneHoursCache;

    private LocalDate startDate;

    private LocalDate endDate;

    private String status;

    private Integer sortOrder;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
