package cn.minims.desk.modules.common.navlink.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 导航链接：工作「工具」与学习「资源」共用一张表。
 * 两者结构完全一致（分组 + 标题 + 地址 + 图标 + 排序），只差 module。
 */
@Data
@TableName("nav_link")
public class NavLinkDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** work / study */
    private String module;

    private String groupName;

    private String title;

    private String url;

    private String icon;

    private String description;

    private Integer clickCount;

    private Integer sortOrder;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
