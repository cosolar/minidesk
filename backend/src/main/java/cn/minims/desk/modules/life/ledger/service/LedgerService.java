package cn.minims.desk.modules.life.ledger.service;

import cn.minims.desk.common.api.ErrorCode;
import cn.minims.desk.common.api.PageResult;
import cn.minims.desk.common.exception.BizException;
import cn.minims.desk.modules.life.ledger.entity.LedgerCategoryDO;
import cn.minims.desk.modules.life.ledger.entity.LedgerDO;
import cn.minims.desk.modules.life.ledger.mapper.LedgerCategoryMapper;
import cn.minims.desk.modules.life.ledger.mapper.LedgerMapper;
import cn.minims.desk.modules.life.ledger.vo.LedgerVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 记账业务。金额全程用「分」，汇总在内存里算，避免 SQL 方言差异。
 */
@Service
public class LedgerService {

    private static final String EXPENSE = "expense";
    private static final String INCOME = "income";
    private static final long DEFAULT_BUDGET_CENTS = 500_000L;

    private static final String[] EXPENSE_NAMES = {
            "餐饮", "交通", "购物", "居住", "娱乐", "医疗", "教育", "人情", "通讯", "其他"
    };

    private final LedgerMapper ledgerMapper;
    private final LedgerCategoryMapper categoryMapper;

    public LedgerService(LedgerMapper ledgerMapper, LedgerCategoryMapper categoryMapper) {
        this.ledgerMapper = ledgerMapper;
        this.categoryMapper = categoryMapper;
    }

    public PageResult<LedgerVO> page(String month, String type, long pageNo, long pageSize) {
        YearMonth ym = parseMonth(month);
        LambdaQueryWrapper<LedgerDO> query = Wrappers.lambdaQuery(LedgerDO.class)
                .between(LedgerDO::getOccurDate, ym.atDay(1), ym.atEndOfMonth())
                .orderByDesc(LedgerDO::getOccurDate)
                .orderByDesc(LedgerDO::getId);
        if (type != null && !type.isBlank()) {
            query.eq(LedgerDO::getType, type);
        }

        Page<LedgerDO> result = ledgerMapper.selectPage(Page.of(pageNo, pageSize), query);
        Map<Long, String> names = categoryNameMap();
        List<LedgerVO> list = result.getRecords().stream()
                .map(item -> new LedgerVO(
                        String.valueOf(item.getId()),
                        item.getType(),
                        item.getAmountCents(),
                        item.getCategoryId() == null ? null : String.valueOf(item.getCategoryId()),
                        names.get(item.getCategoryId()),
                        item.getRemark(),
                        item.getOccurDate() == null ? null : item.getOccurDate().toString()))
                .toList();
        return PageResult.of(list, result.getTotal(), pageNo, pageSize);
    }

    public LedgerVO.Summary summary(String month) {
        YearMonth ym = parseMonth(month);
        List<Map<String, Object>> rows = ledgerMapper.sumByCategory(
                ym.atDay(1).toString(), ym.atEndOfMonth().toString());

        Map<Long, String> names = categoryNameMap();
        long income = 0;
        long expense = 0;
        Map<Long, Long> expenseByCategory = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            long amount = ((Number) row.get("amountCents")).longValue();
            String type = String.valueOf(row.get("type"));
            if (INCOME.equals(type)) {
                income += amount;
            } else {
                expense += amount;
                Long categoryId = row.get("categoryId") == null
                        ? 0L : ((Number) row.get("categoryId")).longValue();
                expenseByCategory.merge(categoryId, amount, Long::sum);
            }
        }

        List<LedgerVO.CategorySum> categories = new ArrayList<>();
        for (Map.Entry<Long, Long> entry : expenseByCategory.entrySet()) {
            categories.add(new LedgerVO.CategorySum(
                    String.valueOf(entry.getKey()),
                    names.getOrDefault(entry.getKey(), "未分类"),
                    entry.getValue(),
                    expense == 0 ? 0 : Math.round(entry.getValue() * 1000.0 / expense) / 1000.0));
        }
        categories.sort((a, b) -> Long.compare(b.amountCents(), a.amountCents()));

        long budget = DEFAULT_BUDGET_CENTS;
        double used = budget == 0 ? 0 : Math.round(expense * 1000.0 / budget) / 1000.0;

        return new LedgerVO.Summary(income, expense, income - expense, budget, used, categories);
    }

    @Transactional(rollbackFor = Exception.class)
    public LedgerVO create(String type, long amountCents, Long categoryId, String remark, LocalDate occurDate) {
        if (amountCents <= 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "金额必须大于 0");
        }
        LedgerDO ledger = new LedgerDO();
        ledger.setType(INCOME.equals(type) ? INCOME : EXPENSE);
        ledger.setAmountCents(amountCents);
        ledger.setCategoryId(categoryId);
        ledger.setRemark(remark);
        ledger.setOccurDate(occurDate == null ? LocalDate.now() : occurDate);
        ledger.setCreatedAt(LocalDateTime.now());
        ledger.setUpdatedAt(LocalDateTime.now());
        ledgerMapper.insert(ledger);

        return new LedgerVO(
                String.valueOf(ledger.getId()),
                ledger.getType(),
                ledger.getAmountCents(),
                ledger.getCategoryId() == null ? null : String.valueOf(ledger.getCategoryId()),
                categoryNameMap().get(ledger.getCategoryId()),
                ledger.getRemark(),
                ledger.getOccurDate().toString());
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        if (ledgerMapper.selectById(id) == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        ledgerMapper.deleteById(id);
    }

    /** 分类是每用户一份，首次访问时惰性初始化 */
    public List<LedgerCategoryDO> categories() {
        List<LedgerCategoryDO> list = categoryMapper.selectList(Wrappers.lambdaQuery(LedgerCategoryDO.class)
                .orderByAsc(LedgerCategoryDO::getSortOrder));
        if (!list.isEmpty()) {
            return list;
        }
        seedCategories();
        return categoryMapper.selectList(Wrappers.lambdaQuery(LedgerCategoryDO.class)
                .orderByAsc(LedgerCategoryDO::getSortOrder));
    }

    private void seedCategories() {
        int order = 0;
        for (String name : EXPENSE_NAMES) {
            insertCategory(EXPENSE, name, order++);
        }
        insertCategory(INCOME, "工资", order++);
        insertCategory(INCOME, "理财", order++);
        insertCategory(INCOME, "其他收入", order);
    }

    private void insertCategory(String type, String name, int order) {
        LedgerCategoryDO category = new LedgerCategoryDO();
        category.setType(type);
        category.setName(name);
        category.setSortOrder(100 + order);
        category.setIsSystem(1);
        categoryMapper.insert(category);
    }

    private Map<Long, String> categoryNameMap() {
        Map<Long, String> map = new HashMap<>();
        for (LedgerCategoryDO category : categoryMapper.selectList(null)) {
            map.put(category.getId(), category.getName());
        }
        return map;
    }

    private YearMonth parseMonth(String month) {
        if (month == null || month.isBlank()) {
            return YearMonth.now();
        }
        try {
            return YearMonth.parse(month);
        } catch (RuntimeException ex) {
            throw new BizException(ErrorCode.PARAM_ERROR, "月份格式应为 yyyy-MM");
        }
    }
}
