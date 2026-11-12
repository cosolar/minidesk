package cn.minims.desk.modules.work.project.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 项目。进度按任务完成率实时计算，progress_cache 仅作列表页冗余展示。
 */
@Data
@TableName("project")
public class ProjectDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long groupId;

    private String name;

    private String description;

    private String color;

    /** planning / doing / paused / done / archived */
    private String status;

    private Integer priority;

    private LocalDate startDate;

    private LocalDate endDate;

    private Integer progressCache;

    private Integer sortOrder;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
