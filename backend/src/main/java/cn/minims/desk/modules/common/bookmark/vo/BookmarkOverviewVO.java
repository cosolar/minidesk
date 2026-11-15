package cn.minims.desk.modules.common.bookmark.vo;

import java.util.List;

/** 一次请求返回整页数据，避免前端为分类/站点/统计分别发请求。 */
public record BookmarkOverviewVO(
        List<BookmarkCategoryVO> categories,
        List<BookmarkSiteVO> sites,
        long totalClicks,
        int pinnedCount,
        int uncategorizedCount
) {
}
