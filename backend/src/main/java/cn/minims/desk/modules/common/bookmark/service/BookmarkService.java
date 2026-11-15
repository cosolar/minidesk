package cn.minims.desk.modules.common.bookmark.service;

import cn.minims.desk.common.api.ErrorCode;
import cn.minims.desk.common.exception.BizException;
import cn.minims.desk.modules.common.bookmark.dto.BookmarkCategorySaveDTO;
import cn.minims.desk.modules.common.bookmark.dto.BookmarkReorderDTO;
import cn.minims.desk.modules.common.bookmark.dto.BookmarkSiteSaveDTO;
import cn.minims.desk.modules.common.bookmark.entity.BookmarkCategoryDO;
import cn.minims.desk.modules.common.bookmark.entity.BookmarkSiteDO;
import cn.minims.desk.modules.common.bookmark.mapper.BookmarkCategoryMapper;
import cn.minims.desk.modules.common.bookmark.mapper.BookmarkSiteMapper;
import cn.minims.desk.modules.common.bookmark.vo.BookmarkCategoryVO;
import cn.minims.desk.modules.common.bookmark.vo.BookmarkOverviewVO;
import cn.minims.desk.modules.common.bookmark.vo.BookmarkSiteVO;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 网站导航：分类 + 站点。所有写操作返回整页快照，前端一次刷新即可。
 */
@Service
public class BookmarkService {

    private static final List<String> PALETTE = List.of(
            "#0B7D77", "#4C6FFF", "#8B7CF6", "#FF9F43", "#F25F5C", "#2AA6C9");

    private final BookmarkCategoryMapper categoryMapper;
    private final BookmarkSiteMapper siteMapper;

    public BookmarkService(BookmarkCategoryMapper categoryMapper, BookmarkSiteMapper siteMapper) {
        this.categoryMapper = categoryMapper;
        this.siteMapper = siteMapper;
    }

    public BookmarkOverviewVO overview() {
        List<BookmarkSiteDO> sites = siteMapper.selectList(Wrappers.lambdaQuery(BookmarkSiteDO.class)
                .orderByAsc(BookmarkSiteDO::getSortOrder)
                .orderByAsc(BookmarkSiteDO::getId));
        Map<Long, Integer> counts = new HashMap<>();
        long clicks = 0;
        int pinned = 0;
        for (BookmarkSiteDO site : sites) {
            Long key = site.getCategoryId() == null ? 0L : site.getCategoryId();
            counts.merge(key, 1, Integer::sum);
            clicks += site.getClickCount() == null ? 0 : site.getClickCount();
            if (site.getPinned() != null && site.getPinned() == 1) {
                pinned++;
            }
        }

        List<BookmarkCategoryVO> categories = categoryMapper.selectList(
                        Wrappers.lambdaQuery(BookmarkCategoryDO.class)
                                .orderByAsc(BookmarkCategoryDO::getSortOrder)
                                .orderByAsc(BookmarkCategoryDO::getId))
                .stream()
                .map(item -> new BookmarkCategoryVO(item.getId(), item.getName(), item.getIcon(),
                        item.getColor(), item.getSortOrder(), counts.getOrDefault(item.getId(), 0)))
                .toList();
        List<BookmarkSiteVO> siteVOs = sites.stream().map(BookmarkSiteVO::from).toList();
        return new BookmarkOverviewVO(categories, siteVOs, clicks, pinned,
                counts.getOrDefault(0L, 0));
    }

    @Transactional(rollbackFor = Exception.class)
    public BookmarkOverviewVO saveCategory(Long id, BookmarkCategorySaveDTO dto) {
        BookmarkCategoryDO item;
        LocalDateTime now = LocalDateTime.now();
        if (id == null) {
            item = new BookmarkCategoryDO();
            item.setSortOrder(nextCategorySort());
            item.setCreatedAt(now);
        } else {
            item = requireCategory(id);
        }
        item.setName(dto.name().trim());
        item.setIcon(StringUtils.hasText(dto.icon()) ? dto.icon().trim() : "folder");
        item.setColor(StringUtils.hasText(dto.color()) ? dto.color().trim() : PALETTE.get(0));
        item.setUpdatedAt(now);
        if (item.getId() == null) {
            categoryMapper.insert(item);
        } else {
            categoryMapper.updateById(item);
        }
        return overview();
    }

    @Transactional(rollbackFor = Exception.class)
    public BookmarkOverviewVO deleteCategory(Long id) {
        requireCategory(id);
        // 分类删掉后站点移到「未分类」，不连带丢失收藏
        List<BookmarkSiteDO> children = siteMapper.selectList(Wrappers.lambdaQuery(BookmarkSiteDO.class)
                .eq(BookmarkSiteDO::getCategoryId, id));
        for (BookmarkSiteDO child : children) {
            child.setCategoryId(0L);
            child.setUpdatedAt(LocalDateTime.now());
            siteMapper.updateById(child);
        }
        categoryMapper.deleteById(id);
        return overview();
    }

    @Transactional(rollbackFor = Exception.class)
    public BookmarkOverviewVO saveSite(Long id, BookmarkSiteSaveDTO dto) {
        long categoryId = dto.categoryId() == null ? 0L : dto.categoryId();
        if (categoryId != 0L) {
            requireCategory(categoryId);
        }
        BookmarkSiteDO item;
        LocalDateTime now = LocalDateTime.now();
        if (id == null) {
            item = new BookmarkSiteDO();
            item.setClickCount(0);
            item.setSortOrder(nextSiteSort(categoryId));
            item.setCreatedAt(now);
        } else {
            item = requireSite(id);
        }
        item.setCategoryId(categoryId);
        item.setName(dto.name().trim());
        item.setUrl(normalizeUrl(dto.url()));
        item.setDescription(dto.description());
        item.setIcon(StringUtils.hasText(dto.icon()) ? dto.icon().trim() : null);
        item.setColor(StringUtils.hasText(dto.color()) ? dto.color().trim() : colorOf(dto.name()));
        item.setPinned(Boolean.TRUE.equals(dto.pinned()) ? 1 : 0);
        item.setUpdatedAt(now);
        if (item.getId() == null) {
            siteMapper.insert(item);
        } else {
            siteMapper.updateById(item);
        }
        return overview();
    }

    @Transactional(rollbackFor = Exception.class)
    public BookmarkOverviewVO deleteSite(Long id) {
        requireSite(id);
        siteMapper.deleteById(id);
        return overview();
    }

    @Transactional(rollbackFor = Exception.class)
    public BookmarkOverviewVO togglePin(Long id) {
        BookmarkSiteDO item = requireSite(id);
        item.setPinned(item.getPinned() != null && item.getPinned() == 1 ? 0 : 1);
        item.setUpdatedAt(LocalDateTime.now());
        siteMapper.updateById(item);
        return overview();
    }

    /** 记录点击次数：失败不影响跳转 */
    @Transactional(rollbackFor = Exception.class)
    public void click(Long id) {
        BookmarkSiteDO item = siteMapper.selectById(id);
        if (item != null) {
            item.setClickCount(item.getClickCount() == null ? 1 : item.getClickCount() + 1);
            siteMapper.updateById(item);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public BookmarkOverviewVO reorder(BookmarkReorderDTO dto) {
        boolean isCategory = "category".equalsIgnoreCase(dto.type());
        int index = 0;
        for (Long id : dto.ids()) {
            if (isCategory) {
                BookmarkCategoryDO item = categoryMapper.selectById(id);
                if (item != null) {
                    item.setSortOrder(index++);
                    item.setUpdatedAt(LocalDateTime.now());
                    categoryMapper.updateById(item);
                }
            } else {
                BookmarkSiteDO item = siteMapper.selectById(id);
                if (item != null) {
                    item.setSortOrder(index++);
                    item.setUpdatedAt(LocalDateTime.now());
                    siteMapper.updateById(item);
                }
            }
        }
        return overview();
    }

    /** 空页一键导入常用站点；已有数据时不重复写入 */
    @Transactional(rollbackFor = Exception.class)
    public BookmarkOverviewVO seed() {
        Long count = siteMapper.selectCount(Wrappers.lambdaQuery(BookmarkSiteDO.class));
        if (count != null && count > 0) {
            return overview();
        }
        String[][] groups = {
                {"开发工具", "code", "#4C6FFF", "GitHub|https://github.com|代码托管与开源项目",
                        "MDN|https://developer.mozilla.org|Web 开发权威文档",
                        "Stack Overflow|https://stackoverflow.com|技术问题问答",
                        "Vercel|https://vercel.com|前端部署平台"},
                {"效率办公", "briefcase", "#0B7D77", "腾讯文档|https://docs.qq.com|在线协作文档",
                        "飞书|https://www.feishu.cn|团队沟通与日程",
                        "Notion|https://www.notion.so|笔记与知识管理",
                        "Figma|https://www.figma.com|界面设计与原型"},
                {"设计灵感", "picture", "#8B7CF6", "Dribbble|https://dribbble.com|设计作品社区",
                        "Behance|https://www.behance.net|创意作品集",
                        "Unsplash|https://unsplash.com|免费高清图库",
                        "Pinterest|https://www.pinterest.com|灵感收集"},
                {"学习阅读", "reading", "#FF9F43", "掘金|https://juejin.cn|技术文章社区",
                        "知乎|https://www.zhihu.com|问答与深度讨论",
                        "哔哩哔哩|https://www.bilibili.com|课程与视频",
                        "少数派|https://sspai.com|效率工具与数码"},
        };
        int categorySort = 0;
        int siteSort = 0;
        LocalDateTime now = LocalDateTime.now();
        for (String[] group : groups) {
            BookmarkCategoryDO category = new BookmarkCategoryDO();
            category.setName(group[0]);
            category.setIcon(group[1]);
            category.setColor(group[2]);
            category.setSortOrder(categorySort++);
            category.setCreatedAt(now);
            category.setUpdatedAt(now);
            categoryMapper.insert(category);
            for (int i = 3; i < group.length; i++) {
                String[] parts = group[i].split("\\|", 3);
                BookmarkSiteDO site = new BookmarkSiteDO();
                site.setCategoryId(category.getId());
                site.setName(parts[0]);
                site.setUrl(parts[1]);
                site.setDescription(parts.length > 2 ? parts[2] : "");
                site.setColor(colorOf(parts[0]));
                site.setClickCount(0);
                site.setPinned(0);
                site.setSortOrder(siteSort++);
                site.setCreatedAt(now);
                site.setUpdatedAt(now);
                siteMapper.insert(site);
            }
        }
        return overview();
    }

    private BookmarkCategoryDO requireCategory(Long id) {
        BookmarkCategoryDO item = categoryMapper.selectById(id);
        if (item == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "分类不存在");
        }
        return item;
    }

    private BookmarkSiteDO requireSite(Long id) {
        BookmarkSiteDO item = siteMapper.selectById(id);
        if (item == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "站点不存在");
        }
        return item;
    }

    private int nextCategorySort() {
        Long count = categoryMapper.selectCount(Wrappers.lambdaQuery(BookmarkCategoryDO.class));
        return count == null ? 0 : count.intValue();
    }

    private int nextSiteSort(long categoryId) {
        Long count = siteMapper.selectCount(Wrappers.lambdaQuery(BookmarkSiteDO.class)
                .eq(BookmarkSiteDO::getCategoryId, categoryId));
        return count == null ? 0 : count.intValue() + 100;
    }

    private String normalizeUrl(String url) {
        String value = url.trim();
        if (value.startsWith("http://") || value.startsWith("https://")) {
            return value;
        }
        return "https://" + value;
    }

    /** 稳定散列配色：同名/同类站点颜色一致，不受排序影响 */
    private String colorOf(String text) {
        int hash = 7;
        for (char ch : text.toCharArray()) {
            hash = (hash * 31 + ch) % 9973;
        }
        return PALETTE.get(Math.abs(hash) % PALETTE.size());
    }
}
