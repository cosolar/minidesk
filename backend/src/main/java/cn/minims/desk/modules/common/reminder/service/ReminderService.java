package cn.minims.desk.modules.common.reminder.service;

import cn.minims.desk.framework.tenant.TenantContext;
import cn.minims.desk.modules.common.reminder.vo.ReminderVO;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 统一提醒中心。
 *
 * <p>提醒不落库、不建调度：它本质是「多个表按时间窗口查一遍」。
 * 存成通知表反而要处理「任务改期后旧提醒怎么撤回」，而实时聚合永远与数据一致。
 */
@Service
public class ReminderService {

    /** 任务提前 7 天、生日与纪念日提前 30 天、计划提前 14 天 */
    private static final int TASK_WINDOW = 7;
    private static final int DAY_WINDOW = 30;
    private static final int PLAN_WINDOW = 14;

    private final JdbcTemplate jdbcTemplate;

    public ReminderService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<ReminderVO> list() {
        long userId = TenantContext.requireUserId();
        LocalDate today = LocalDate.now();
        List<ReminderVO> items = new ArrayList<>();

        // 1. 任务：逾期 + 未来 7 天到期
        for (Map<String, Object> row : jdbcTemplate.queryForList(
                "select id, title, due_date, priority, module from task "
                        + "where user_id = ? and deleted = 0 and status not in ('done', 'cancelled') "
                        + "and due_date is not null and due_date <= ? order by due_date asc limit 60",
                userId, today.plusDays(TASK_WINDOW).toString())) {
            LocalDate date = LocalDate.parse(String.valueOf(row.get("due_date")));
            long days = ChronoUnit.DAYS.between(today, date);
            String module = String.valueOf(row.get("module"));
            String subtitle = switch (module) {
                case "study" -> "学习任务";
                case "life" -> "生活待办";
                default -> "工作任务";
            };
            items.add(new ReminderVO("task-" + row.get("id"), "task",
                    String.valueOf(row.get("title")), subtitle, date, days, levelOf(days), "/todo", false));
        }

        // 2. 联系人生日：按「下一次发生」计算
        for (Map<String, Object> row : jdbcTemplate.queryForList(
                "select id, name, birthday from contact "
                        + "where user_id = ? and deleted = 0 and birthday is not null",
                userId)) {
            LocalDate birthday = LocalDate.parse(String.valueOf(row.get("birthday")));
            LocalDate next = nextOccurrence(birthday, today);
            long days = ChronoUnit.DAYS.between(today, next);
            if (days <= DAY_WINDOW) {
                items.add(new ReminderVO("birthday-" + row.get("id"), "birthday",
                        String.valueOf(row.get("name")) + " 的生日", "联系人", next, days,
                        levelOf(days), "/life/contact", false));
            }
        }

        // 3. 纪念日：一年一次的那类滚到下一次
        for (Map<String, Object> row : jdbcTemplate.queryForList(
                "select id, title, date, repeat_yearly from anniversary where user_id = ?",
                userId)) {
            LocalDate date = LocalDate.parse(String.valueOf(row.get("date")));
            boolean yearly = row.get("repeat_yearly") == null
                    || Integer.parseInt(String.valueOf(row.get("repeat_yearly"))) == 1;
            LocalDate occur = yearly ? nextOccurrence(date, today) : date;
            long days = ChronoUnit.DAYS.between(today, occur);
            if (days >= 0 && days <= DAY_WINDOW) {
                items.add(new ReminderVO("anniversary-" + row.get("id"), "anniversary",
                        String.valueOf(row.get("title")), "纪念日", occur, days,
                        levelOf(days), "/life/anniversary", false));
            }
        }

        // 4. 倒数日
        for (Map<String, Object> row : jdbcTemplate.queryForList(
                "select id, title, target_date from countdown "
                        + "where user_id = ? and target_date >= ? and target_date <= ? "
                        + "order by target_date asc limit 30",
                userId, today.toString(), today.plusDays(DAY_WINDOW).toString())) {
            LocalDate date = LocalDate.parse(String.valueOf(row.get("target_date")));
            long days = ChronoUnit.DAYS.between(today, date);
            items.add(new ReminderVO("countdown-" + row.get("id"), "countdown",
                    String.valueOf(row.get("title")), "倒数日", date, days,
                    levelOf(days), "/life/anniversary", false));
        }

        // 5. 学习计划：到期在两周内的周计划
        for (Map<String, Object> row : jdbcTemplate.queryForList(
                "select id, title, end_date from study_plan "
                        + "where user_id = ? and status = 'doing' and end_date is not null "
                        + "and end_date >= ? and end_date <= ? order by end_date asc limit 20",
                userId, today.toString(), today.plusDays(PLAN_WINDOW).toString())) {
            LocalDate date = LocalDate.parse(String.valueOf(row.get("end_date")));
            long days = ChronoUnit.DAYS.between(today, date);
            items.add(new ReminderVO("plan-" + row.get("id"), "plan",
                    String.valueOf(row.get("title")), "学习计划到期", date, days,
                    levelOf(days), "/study/plan", false));
        }

        items.sort(Comparator.comparingLong(ReminderVO::days).thenComparing(ReminderVO::kind));
        return items;
    }

    private String levelOf(long days) {
        if (days < 0) {
            return "overdue";
        }
        if (days == 0) {
            return "today";
        }
        return days <= 3 ? "urgent" : "soon";
    }

    /** 今年已过则滚到下一年 */
    private LocalDate nextOccurrence(LocalDate original, LocalDate today) {
        LocalDate next = original.withYear(today.getYear());
        if (next.isBefore(today)) {
            next = next.plusYears(1);
        }
        return next;
    }
}
