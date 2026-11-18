package cn.minims.desk.modules.common.calendar.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 生日 / 纪念日。repeatYearly = 1 时每年按月日重复。
 */
@Data
@TableName("anniversary")
public class AnniversaryDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String title;

    private String type;

    private String person;

    private LocalDate date;

    private Integer lunar;

    private Integer repeatYearly;

    private Integer advanceDays;

    private Long contactId;

    private String note;

    /** 封面图地址：本地上传的 /api/files/{id}/raw 或外链 */
    private String cover;

    /** 1 = 重要，列表里打「重要」标签并参与排序 */
    private Integer important;

    private LocalDateTime createdAt;
}
