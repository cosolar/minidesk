package cn.minims.desk.modules.life.ledger.vo;

import java.util.List;

/**
 * 记账列表条目与月度汇总。
 */
public record LedgerVO(
        String id,
        String type,
        long amountCents,
        String categoryId,
        String categoryName,
        String remark,
        String occurDate
) {
    public record CategorySum(String categoryId, String name, long amountCents, double ratio) {
    }

    public record Summary(
            long incomeCents,
            long expenseCents,
            long balanceCents,
            long budgetCents,
            double budgetUsedRate,
            List<CategorySum> categories
    ) {
    }
}
