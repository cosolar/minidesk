package cn.minims.desk.modules.common.bookmark.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 网站导航分类。user_id 由租户插件自动补齐，业务代码不手写。
 */
@Data
@TableName("bookmark_category")
public class BookmarkCategoryDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    /** emoji 或内置图标名 */
    private String icon;

    private String color;

    private Integer sortOrder;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
