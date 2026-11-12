package cn.minims.desk.modules.life.shopping.service;

import cn.minims.desk.common.api.ErrorCode;
import cn.minims.desk.common.exception.BizException;
import cn.minims.desk.modules.life.shopping.entity.ShoppingItemDO;
import cn.minims.desk.modules.life.shopping.entity.ShoppingListDO;
import cn.minims.desk.modules.life.shopping.mapper.ShoppingItemMapper;
import cn.minims.desk.modules.life.shopping.mapper.ShoppingListMapper;
import cn.minims.desk.modules.life.shopping.vo.ShoppingListVO;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 购物清单业务。金额按分存储，合计在内存计算。
 */
@Service
public class ShoppingService {

    private final ShoppingListMapper listMapper;
    private final ShoppingItemMapper itemMapper;

    public ShoppingService(ShoppingListMapper listMapper, ShoppingItemMapper itemMapper) {
        this.listMapper = listMapper;
        this.itemMapper = itemMapper;
    }

    public List<ShoppingListVO> lists() {
        List<ShoppingListDO> lists = listMapper.selectList(
                Wrappers.lambdaQuery(ShoppingListDO.class).orderByDesc(ShoppingListDO::getId));
        if (lists.isEmpty()) {
            return List.of();
        }

        List<ShoppingItemDO> items = itemMapper.selectList(Wrappers.lambdaQuery(ShoppingItemDO.class)
                .orderByAsc(ShoppingItemDO::getBought)
                .orderByAsc(ShoppingItemDO::getSortOrder)
                .orderByDesc(ShoppingItemDO::getId));

        Map<Long, List<ShoppingItemDO>> byList = new HashMap<>();
        for (ShoppingItemDO item : items) {
            byList.computeIfAbsent(item.getListId(), key -> new ArrayList<>()).add(item);
        }

        List<ShoppingListVO> result = new ArrayList<>(lists.size());
        for (ShoppingListDO list : lists) {
            List<ShoppingItemDO> own = byList.getOrDefault(list.getId(), List.of());
            long total = own.stream()
                    .mapToLong(item -> {
                        long price = item.getPriceCents() == null ? 0 : item.getPriceCents();
                        BigDecimal qty = item.getQuantity() == null ? BigDecimal.ONE : item.getQuantity();
                        return Math.round(price * qty.doubleValue());
                    })
                    .sum();
            int bought = (int) own.stream().filter(item -> item.getBought() != null && item.getBought() == 1).count();
            result.add(new ShoppingListVO(
                    String.valueOf(list.getId()),
                    list.getName(),
                    list.getIcon(),
                    list.getBudgetCents() == null ? 0 : list.getBudgetCents(),
                    list.getStatus(),
                    own.size(),
                    bought,
                    total,
                    own.stream().map(this::toItem).toList()));
        }
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public ShoppingListVO createList(String name, String icon, Long budgetCents) {
        if (!StringUtils.hasText(name)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "请输入清单名称");
        }
        ShoppingListDO list = new ShoppingListDO();
        list.setName(name.trim());
        list.setIcon(icon);
        list.setBudgetCents(budgetCents == null ? 0 : budgetCents);
        list.setStatus("open");
        list.setCreatedAt(LocalDateTime.now());
        list.setUpdatedAt(LocalDateTime.now());
        listMapper.insert(list);
        return lists().stream()
                .filter(vo -> vo.id().equals(String.valueOf(list.getId())))
                .findFirst()
                .orElseThrow();
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteList(Long id) {
        listMapper.deleteById(id);
        itemMapper.delete(Wrappers.lambdaQuery(ShoppingItemDO.class).eq(ShoppingItemDO::getListId, id));
    }

    @Transactional(rollbackFor = Exception.class)
    public void addItem(Long listId, String name, BigDecimal quantity, String unit, Long priceCents, String category) {
        if (!StringUtils.hasText(name)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "请输入商品名称");
        }
        ShoppingItemDO item = new ShoppingItemDO();
        item.setListId(listId);
        item.setName(name.trim());
        item.setQuantity(quantity == null ? BigDecimal.ONE : quantity);
        item.setUnit(unit);
        item.setPriceCents(priceCents == null ? 0 : priceCents);
        item.setCategory(category);
        item.setBought(0);
        item.setSortOrder(0);
        item.setCreatedAt(LocalDateTime.now());
        itemMapper.insert(item);
    }

    @Transactional(rollbackFor = Exception.class)
    public void toggleItem(Long itemId, boolean bought) {
        ShoppingItemDO item = itemMapper.selectById(itemId);
        if (item == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        item.setBought(bought ? 1 : 0);
        item.setBoughtAt(bought ? LocalDateTime.now() : null);
        itemMapper.updateById(item);
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteItem(Long itemId) {
        itemMapper.deleteById(itemId);
    }

    /** 已购条目一键转记账（记一笔支出） */
    @Transactional(rollbackFor = Exception.class)
    public long sumBoughtCents(Long listId) {
        List<ShoppingItemDO> items = itemMapper.selectList(Wrappers.lambdaQuery(ShoppingItemDO.class)
                .eq(ShoppingItemDO::getListId, listId)
                .eq(ShoppingItemDO::getBought, 1));
        return items.stream()
                .mapToLong(item -> {
                    long price = item.getPriceCents() == null ? 0 : item.getPriceCents();
                    BigDecimal qty = item.getQuantity() == null ? BigDecimal.ONE : item.getQuantity();
                    return Math.round(price * qty.doubleValue());
                })
                .sum();
    }

    private ShoppingListVO.Item toItem(ShoppingItemDO item) {
        return new ShoppingListVO.Item(
                String.valueOf(item.getId()),
                item.getName(),
                item.getQuantity(),
                item.getUnit(),
                item.getPriceCents() == null ? 0 : item.getPriceCents(),
                item.getCategory(),
                item.getBought() != null && item.getBought() == 1);
    }
}
