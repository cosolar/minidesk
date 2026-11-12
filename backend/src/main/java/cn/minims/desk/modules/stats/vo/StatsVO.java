package cn.minims.desk.modules.stats.vo;

import java.util.List;

/**
 * 数据统计：工作效率 / 学习成长 / 生活收支。
 * 趋势用「当前周期 + 前 N 期」的等长区间，前端直接画柱状图。
 */
public record StatsVO(
        Work work,
        Study study,
        Life life
) {
    /** 一个时间桶；primary 为主指标，secondary 为对照指标（如完成 vs 新建、支出 vs 收入） */
    public record Point(String label, long primary, long secondary) {
    }

    public record CategoryStat(String name, long cents, double ratio) {
    }

    public record Work(
            int weekCreated,
            int weekDone,
            int overdue,
            int activeProjects,
            double completionRate,
            List<Point> trend
    ) {
    }

    public record Study(
            long monthMinutes,
            int streakDays,
            double planProgress,
            int planCount,
            List<Point> trend
    ) {
    }

    public record Life(
            long monthIncome,
            long monthExpense,
            long balance,
            long budgetCents,
            double budgetUsedRate,
            List<Point> trend,
            List<CategoryStat> topCategories
    ) {
    }
}
