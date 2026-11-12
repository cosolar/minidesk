package cn.minims.desk.modules.life.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 生活记录 / 心情日记。
 */
@Data
@TableName("life_log")
public class LifeLogDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String title;

    private String content;

    /** happy / calm / tired / sad / excited */
    private String mood;

    private String weather;

    private String location;

    private LocalDate logDate;

    private Long coverFileId;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
