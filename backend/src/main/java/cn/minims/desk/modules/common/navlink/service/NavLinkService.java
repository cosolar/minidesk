package cn.minims.desk.modules.common.navlink.service;

import cn.minims.desk.common.api.ErrorCode;
import cn.minims.desk.common.exception.BizException;
import cn.minims.desk.modules.common.navlink.dto.NavLinkSaveDTO;
import cn.minims.desk.modules.common.navlink.entity.NavLinkDO;
import cn.minims.desk.modules.common.navlink.mapper.NavLinkMapper;
import cn.minims.desk.modules.common.navlink.vo.NavLinkVO;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 导航链接（工作书签 / 学习资源）。
 */
@Service
public class NavLinkService {

    private final NavLinkMapper mapper;

    public NavLinkService(NavLinkMapper mapper) {
        this.mapper = mapper;
    }

    public List<NavLinkVO> list(String module, String keyword) {
        var query = Wrappers.lambdaQuery(NavLinkDO.class)
                .eq(StringUtils.hasText(module), NavLinkDO::getModule, module);
        if (StringUtils.hasText(keyword)) {
            query.and(w -> w.like(NavLinkDO::getTitle, keyword.trim())
                    .or().like(NavLinkDO::getDescription, keyword.trim())
                    .or().like(NavLinkDO::getUrl, keyword.trim()));
        }
        query.orderByAsc(NavLinkDO::getGroupName).orderByAsc(NavLinkDO::getSortOrder)
                .orderByAsc(NavLinkDO::getId);
        return mapper.selectList(query).stream().map(NavLinkVO::from).toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public NavLinkVO create(NavLinkSaveDTO dto) {
        NavLinkDO item = new NavLinkDO();
        item.setModule(StringUtils.hasText(dto.module()) ? dto.module() : "work");
        item.setGroupName(StringUtils.hasText(dto.groupName()) ? dto.groupName() : "常用");
        item.setTitle(dto.title().trim());
        item.setUrl(normalizeUrl(dto.url()));
        item.setIcon(dto.icon());
        item.setDescription(dto.description());
        item.setClickCount(0);
        item.setSortOrder(dto.sortOrder() == null ? nextSort(item.getModule()) : dto.sortOrder());
        item.setCreatedAt(LocalDateTime.now());
        item.setUpdatedAt(LocalDateTime.now());
        mapper.insert(item);
        return NavLinkVO.from(item);
    }

    @Transactional(rollbackFor = Exception.class)
    public NavLinkVO update(Long id, NavLinkSaveDTO dto) {
        NavLinkDO item = require(id);
        item.setGroupName(StringUtils.hasText(dto.groupName()) ? dto.groupName() : "常用");
        item.setTitle(dto.title().trim());
        item.setUrl(normalizeUrl(dto.url()));
        item.setIcon(dto.icon());
        item.setDescription(dto.description());
        if (dto.sortOrder() != null) {
            item.setSortOrder(dto.sortOrder());
        }
        item.setUpdatedAt(LocalDateTime.now());
        mapper.updateById(item);
        return NavLinkVO.from(item);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        if (mapper.deleteById(id) == 0) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
    }

    /** 记录点击次数：用于「最常用」排序，失败不影响跳转 */
    @Transactional(rollbackFor = Exception.class)
    public void click(Long id) {
        NavLinkDO item = require(id);
        item.setClickCount(item.getClickCount() == null ? 1 : item.getClickCount() + 1);
        mapper.updateById(item);
    }

    public List<String> groups(String module) {
        return mapper.selectList(Wrappers.lambdaQuery(NavLinkDO.class)
                        .eq(NavLinkDO::getModule, module)
                        .orderByAsc(NavLinkDO::getGroupName))
                .stream().map(NavLinkDO::getGroupName).distinct().toList();
    }

    private NavLinkDO require(Long id) {
        NavLinkDO item = mapper.selectById(id);
        if (item == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        return item;
    }

    private int nextSort(String module) {
        Long count = mapper.selectCount(Wrappers.lambdaQuery(NavLinkDO.class)
                .eq(NavLinkDO::getModule, module));
        return count == null ? 0 : count.intValue();
    }

    private String normalizeUrl(String url) {
        String value = url.trim();
        if (value.startsWith("http://") || value.startsWith("https://")) {
            return value;
        }
        return "https://" + value;
    }
}
