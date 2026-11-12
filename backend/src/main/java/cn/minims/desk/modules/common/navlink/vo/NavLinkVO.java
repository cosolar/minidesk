package cn.minims.desk.modules.common.navlink.vo;

import cn.minims.desk.modules.common.navlink.entity.NavLinkDO;

/**
 * 导航链接展示对象。
 */
public record NavLinkVO(
        Long id,
        String module,
        String groupName,
        String title,
        String url,
        String icon,
        String description,
        Integer clickCount,
        Integer sortOrder
) {
    public static NavLinkVO from(NavLinkDO item) {
        return new NavLinkVO(item.getId(), item.getModule(), item.getGroupName(), item.getTitle(),
                item.getUrl(), item.getIcon(), item.getDescription(), item.getClickCount(),
                item.getSortOrder());
    }
}
