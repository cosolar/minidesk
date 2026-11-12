package cn.minims.desk.modules.life.shopping.vo;

import java.math.BigDecimal;
import java.util.List;

/**
 * 购物清单及其条目。
 */
public record ShoppingListVO(
        String id,
        String name,
        String icon,
        long budgetCents,
        String status,
        int itemCount,
        int boughtCount,
        long totalCents,
        List<Item> items
) {
    public record Item(
            String id,
            String name,
            BigDecimal quantity,
            String unit,
            long priceCents,
            String category,
            boolean bought
    ) {
    }
}
