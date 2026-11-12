package cn.minims.desk.modules.dashboard.vo;

import cn.minims.desk.modules.common.calendar.vo.CalendarItemVO;
import cn.minims.desk.modules.study.vo.StudyWeekVO;
import cn.minims.desk.modules.work.task.vo.TaskVO;
import java.util.List;

/**
 * 首页聚合响应。任一子块失败降级为 null，前端展示降级态。
 */
public record DashboardVO(
        WeatherBlock weather,
        TodoBlock todo,
        StudyWeekVO study,
        List<LifeLogItem> lifeLogs,
        CalendarBlock calendar,
        Counters counters,
        boolean partial
) {
    public record WeatherBlock(int temp, String text, String city, boolean isMock) {
    }

    public record TodoBlock(long total, long done, List<TaskVO> items) {
    }

    public record LifeLogItem(String id, String title, String date, String cover, String mood) {
    }

    public record CalendarBlock(String today, List<CalendarItemVO> events) {
    }

    public record Counters(long unreadNotification, long doingProjects, int habitStreak) {
    }
}
