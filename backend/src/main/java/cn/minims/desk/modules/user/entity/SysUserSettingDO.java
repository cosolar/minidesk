package cn.minims.desk.modules.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 用户偏好（主题、城市、周起始、首页布局）。
 */
@Data
@TableName("sys_user_setting")
public class SysUserSettingDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String theme;

    private Integer sidebarCollapsed;

    private String city;

    private Integer weekStart;

    private String dashboardLayout;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
