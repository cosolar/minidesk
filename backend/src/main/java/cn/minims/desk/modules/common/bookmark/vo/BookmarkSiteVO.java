package cn.minims.desk.modules.common.bookmark.vo;

import cn.minims.desk.modules.common.bookmark.entity.BookmarkSiteDO;
import java.time.LocalDateTime;

/** 站点卡片。 */
public record BookmarkSiteVO(
        Long id,
        Long categoryId,
        String name,
        String url,
        String description,
        String icon,
        String color,
        int clickCount,
        boolean pinned,
        Integer sortOrder,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static BookmarkSiteVO from(BookmarkSiteDO item) {
        return new BookmarkSiteVO(
                item.getId(),
                item.getCategoryId() == null ? 0L : item.getCategoryId(),
                item.getName(),
                item.getUrl(),
                item.getDescription(),
                item.getIcon(),
                item.getColor(),
                item.getClickCount() == null ? 0 : item.getClickCount(),
                item.getPinned() != null && item.getPinned() == 1,
                item.getSortOrder(),
                item.getCreatedAt(),
                item.getUpdatedAt());
    }
}
