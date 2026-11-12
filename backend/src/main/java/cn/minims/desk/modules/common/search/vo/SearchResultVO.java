package cn.minims.desk.modules.common.search.vo;

import java.util.List;

/**
 * 全局搜索结果。按数据域分组返回，前端直接渲染分组列表。
 */
public record SearchResultVO(
        String keyword,
        int total,
        List<Group> groups
) {
    /** 单条命中：title + 摘要 + 跳转路由 */
    public record Hit(
            String type,
            String id,
            String title,
            String snippet,
            String meta,
            String route
    ) {
    }

    public record Group(String key, String label, List<Hit> hits) {
    }
}
