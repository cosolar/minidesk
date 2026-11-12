package cn.minims.desk.modules.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 站点级配置（全局表，不受租户隔离约束）。
 */
@Data
@TableName("sys_config")
public class SysConfigDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String configKey;

    private String configValue;

    private String remark;

    private LocalDateTime updatedAt;
}
