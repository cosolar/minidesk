package cn.minims.desk.modules.stats.service;

import cn.minims.desk.framework.tenant.TenantContext;
import cn.minims.desk.modules.stats.vo.StatsVO;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 数据统计。
 *
 * <p>统计口径写死在这里，前端只负责画图：
 * 周从周一开始；完成率 = 已完成 / 同一区间内新建（分母为 0 时按 0 处理）；
 * 金额一律为「分」，展示时再除 100。
 */
@Service
public class StatsService {

    private static final DateTimeFormatter WEEK_LABEL = DateTimeFormatter.ofPattern("M/d");
    private static final DateTimeFormatter MONTH_LABEL = DateTimeFormatter.ofPattern("M月");

    private final JdbcTemplate jdbcTemplate;

    public StatsService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public StatsVO summary() {
        long userId = TenantContext.requireUserId();
        LocalDate today = LocalDate.now();
        LocalDate monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));

        return new StatsVO(work(userId, today, monday), study(userId, today, monday), life(userId, today));
    }

    // ---------- 工作效率 ----------

    private StatsVO.Work work(long userId, LocalDate today, LocalDate monday) {
        List<StatsVO.Point> trend = new ArrayList<>(8);
        int weekCreated = 0;
        int weekDone = 0;

        for (int i = 7; i >= 0; i--) {
            LocalDate from = monday.minusWeeks(i);
            LocalDate to = from.plusDays(6);
            int created = count("select count(*) from task where user_id = ? and deleted = 0 "
                    + "and date(created_at) between ? and ?", userId, from, to);
            int done = count("select count(*) from task where user_id = ? and deleted = 0 "
                    + "and status = 'done' and done_at is not null and date(done_at) between ? and ?",
                    userId, from, to);
            if (i == 0) {
                weekCreated = created;
                weekDone = done;
            }
            trend.add(new StatsVO.Point(from.format(WEEK_LABEL), done, created));
        }

        int overdue = count("select count(*) from task where user_id = ? and deleted = 0 "
                + "and status not in ('done', 'cancelled') and due_date < ?", userId, today);
        int activeProjects = count("select count(*) from project where user_id = ? and deleted = 0 "
                + "and status = 'doing'", userId);

        // 近 30 天完成率：口径固定，避免「本周刚过两天完成率很低」的误读
        LocalDate monthAgo = today.minusDays(30);
        int created30 = count("select count(*) from task where user_id = ? and deleted = 0 "
                + "and date(created_at) between ? and ?", userId, monthAgo, today);
        int done30 = count("select count(*) from task where user_id = ? and deleted = 0 "
                + "and status = 'done' and done_at is not null and date(done_at) between ? and ?",
                userId, monthAgo, today);
        double rate = created30 == 0 ? 0 : Math.round(done30 * 1000.0 / created30) / 10.0;

        return new StatsVO.Work(weekCreated, weekDone, overdue, activeProjects, rate, trend);
    }

    // ---------- 学习成长 ----------

    private StatsVO.Study study(long userId, LocalDate today, LocalDate monday) {
        List<StatsVO.Point> trend = new ArrayList<>(8);
        for (int i = 7; i >= 0; i--) {
            LocalDate from = monday.minusWeeks(i);
            LocalDate to = from.plusDays(6);
            long minutes = sum("select coalesce(sum(minutes), 0) from study_record "
                    + "where user_id = ? and study_date between ? and ?", userId, from, to);
            trend.add(new StatsVO.Point(from.format(WEEK_LABEL), minutes, 0));
        }

        LocalDate monthStart = today.withDayOfMonth(1);
        long monthMinutes = sum("select coalesce(sum(minutes), 0) from study_record "
                + "where user_id = ? and study_date between ? and ?", userId, monthStart, today);

        int streak = streak(userId, today);

        List<java.util.Map<String, Object>> plans = jdbcTemplate.queryForList(
                "select coalesce(sum(target_hours), 0) as target, coalesce(sum(done_hours_cache), 0) as done, "
                        + "count(*) as cnt from study_plan where user_id = ? and target_hours > 0",
                userId);
        double progress = 0;
        int planCount = 0;
        if (!plans.isEmpty()) {
            Object target = plans.get(0).get("target");
            Object done = plans.get(0).get("done");
            Object cnt = plans.get(0).get("cnt");
            double t = target == null ? 0 : Double.parseDouble(String.valueOf(target));
            double d = done == null ? 0 : Double.parseDouble(String.valueOf(done));
            planCount = cnt == null ? 0 : Integer.parseInt(String.valueOf(cnt));
            progress = t <= 0 ? 0 : Math.min(100, Math.round(d * 1000.0 / t) / 10.0);
        }

        return new StatsVO.Study(monthMinutes, streak, progress, planCount, trend);
    }

    /** 连续学习天数：从今天（或昨天）往前数 */
    private int streak(long userId, LocalDate today) {
        List<String> dates = jdbcTemplate.queryForList(
                "select distinct study_date from study_record where user_id = ? "
                        + "and study_date <= ? order by study_date desc limit 400",
                String.class, userId, today.toString());
        if (dates.isEmpty()) {
            return 0;
        }
        LocalDate cursor = LocalDate.parse(dates.get(0));
        // 今天还没记，从昨天开始算，避免一早打开就显示 0
        if (cursor.isBefore(today.minusDays(1))) {
            return 0;
        }
        int streak = 1;
        for (int i = 1; i < dates.size(); i++) {
            LocalDate prev = LocalDate.parse(dates.get(i));
            if (prev.equals(cursor.minusDays(1))) {
                streak++;
                cursor = prev;
            } else {
                break;
            }
        }
        return streak;
    }

    // ---------- 生活收支 ----------

    private StatsVO.Life life(long userId, LocalDate today) {
        List<StatsVO.Point> trend = new ArrayList<>(6);
        LocalDate monthStart = today.withDayOfMonth(1);

        for (int i = 5; i >= 0; i--) {
            LocalDate from = monthStart.minusMonths(i);
            LocalDate to = from.with(TemporalAdjusters.lastDayOfMonth());
            long expense = sum("select coalesce(sum(amount_cents), 0) from ledger "
                    + "where user_id = ? and deleted = 0 and type = 'expense' and occur_date between ? and ?",
                    userId, from, to);
            long income = sum("select coalesce(sum(amount_cents), 0) from ledger "
                    + "where user_id = ? and deleted = 0 and type = 'income' and occur_date between ? and ?",
                    userId, from, to);
            trend.add(new StatsVO.Point(from.format(MONTH_LABEL), expense, income));
        }

        long monthExpense = sum("select coalesce(sum(amount_cents), 0) from ledger "
                + "where user_id = ? and deleted = 0 and type = 'expense' and occur_date between ? and ?",
                userId, monthStart, today);
        long monthIncome = sum("select coalesce(sum(amount_cents), 0) from ledger "
                + "where user_id = ? and deleted = 0 and type = 'income' and occur_date between ? and ?",
                userId, monthStart, today);

        long budget = sum("select coalesce(sum(amount_cents), 0) from budget "
                + "where user_id = ? and period = ? and category_id is null",
                userId, today.format(DateTimeFormatter.ofPattern("yyyy-MM")));
        double used = budget <= 0 ? 0 : Math.round(monthExpense * 1000.0 / budget) / 10.0;

        List<StatsVO.CategoryStat> categories = new ArrayList<>();
        for (java.util.Map<String, Object> row : jdbcTemplate.queryForList(
                "select coalesce(c.name, '未分类') as name, sum(l.amount_cents) as total "
                        + "from ledger l left join ledger_category c on c.id = l.category_id "
                        + "where l.user_id = ? and l.deleted = 0 and l.type = 'expense' "
                        + "and l.occur_date between ? and ? "
                        + "group by coalesce(c.name, '未分类') order by total desc limit 5",
                userId, monthStart, today)) {
            long total = Long.parseLong(String.valueOf(row.get("total")));
            double ratio = monthExpense <= 0 ? 0 : Math.round(total * 1000.0 / monthExpense) / 10.0;
            categories.add(new StatsVO.CategoryStat(String.valueOf(row.get("name")), total, ratio));
        }

        return new StatsVO.Life(monthIncome, monthExpense, monthIncome - monthExpense,
                budget, used, trend, categories);
    }

    private int count(String sql, Object... args) {
        Integer value = jdbcTemplate.queryForObject(sql, Integer.class, args);
        return value == null ? 0 : value;
    }

    private long sum(String sql, Object... args) {
        Number value = jdbcTemplate.queryForObject(sql, Number.class, args);
        return value == null ? 0L : value.longValue();
    }
}
