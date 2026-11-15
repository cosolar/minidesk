package cn.minims.desk.modules.common.bookmark.vo;

/** 分类及其实时站点数量。 */
public record BookmarkCategoryVO(
        Long id,
        String name,
        String icon,
        String color,
        Integer sortOrder,
        int siteCount
) {
}
