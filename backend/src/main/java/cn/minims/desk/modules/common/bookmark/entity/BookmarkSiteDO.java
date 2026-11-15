package cn.minims.desk.modules.common.bookmark.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 网站导航条目。category_id = 0 表示未分类。
 */
@Data
@TableName("bookmark_site")
public class BookmarkSiteDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long categoryId;

    private String name;

    private String url;

    private String description;

    /** 自定义图标 URL；为空时用站点首字母生成色块 */
    private String icon;

    private String color;

    private Integer clickCount;

    /** 1 = 置顶 */
    private Integer pinned;

    private Integer sortOrder;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
