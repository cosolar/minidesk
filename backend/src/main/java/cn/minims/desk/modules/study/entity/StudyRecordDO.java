package cn.minims.desk.modules.study.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 学习记录：每次学习一条，按天聚合。
 */
@Data
@TableName("study_record")
public class StudyRecordDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String subject;

    private Integer minutes;

    private LocalDate studyDate;

    private Long planId;

    private String note;

    private LocalDateTime createdAt;
}
