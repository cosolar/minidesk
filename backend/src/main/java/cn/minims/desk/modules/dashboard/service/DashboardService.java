package cn.minims.desk.modules.dashboard.service;

import cn.minims.desk.modules.common.calendar.service.CalendarService;
import cn.minims.desk.modules.common.calendar.vo.CalendarItemVO;
import cn.minims.desk.modules.dashboard.mapper.DashboardQueryMapper;
import cn.minims.desk.modules.dashboard.vo.DashboardVO;
import cn.minims.desk.modules.integration.weather.service.WeatherService;
import cn.minims.desk.modules.integration.weather.vo.WeatherVO;
import cn.minims.desk.modules.life.entity.LifeLogDO;
import cn.minims.desk.modules.life.mapper.LifeLogMapper;
import cn.minims.desk.modules.study.service.StudyService;
import cn.minims.desk.modules.study.vo.StudyWeekVO;
import cn.minims.desk.modules.work.task.service.TaskService;
import cn.minims.desk.modules.work.task.vo.TaskVO;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 首页聚合：一次请求返回所有区块，任一块失败只降级该块。
 */
@Service
public class DashboardService {

    private static final Logger log = LoggerFactory.getLogger(DashboardService.class);
    private static final int TODO_PREVIEW = 4;

    private final WeatherService weatherService;
    private final TaskService taskService;
    private final StudyService studyService;
    private final LifeLogMapper lifeLogMapper;
    private final CalendarService calendarService;
    private final DashboardQueryMapper queryMapper;

    public DashboardService(WeatherService weatherService,
                            TaskService taskService,
                            StudyService studyService,
                            LifeLogMapper lifeLogMapper,
                            CalendarService calendarService,
                            DashboardQueryMapper queryMapper) {
        this.weatherService = weatherService;
        this.taskService = taskService;
        this.studyService = studyService;
        this.lifeLogMapper = lifeLogMapper;
        this.calendarService = calendarService;
        this.queryMapper = queryMapper;
    }

    public DashboardVO summary() {
        AtomicBoolean partial = new AtomicBoolean(false);

        WeatherVO weather = safe(() -> weatherService.now(null), null, partial, "weather");
        List<TaskVO> todos = safe(() -> taskService.today(), List.of(), partial, "todo");
        StudyWeekVO study = safe(studyService::weekStats, null, partial, "study");
        List<LifeLogDO> logs = safe(this::latestLifeLogs, List.of(), partial, "lifeLogs");
        List<CalendarItemVO> events = safe(calendarService::today, List.of(), partial, "calendar");
        long unread = safe(queryMapper::countUnreadNotifications, 0L, partial, "unread");
        long doingProjects = safe(queryMapper::countDoingProjects, 0L, partial, "projects");
        int streak = study == null ? 0 : study.streak();

        long done = todos.stream().filter(TaskVO::done).count();

        return new DashboardVO(
                weather == null ? null
                        : new DashboardVO.WeatherBlock(weather.temp(), weather.text(), weather.city(), weather.isMock()),
                new DashboardVO.TodoBlock(todos.size(), done, todos.stream().limit(TODO_PREVIEW).toList()),
                study,
                logs.stream().map(this::toLifeItem).toList(),
                new DashboardVO.CalendarBlock(LocalDate.now().toString(), events.stream().limit(3).toList()),
                new DashboardVO.Counters(unread, doingProjects, streak),
                partial.get());
    }

    private List<LifeLogDO> latestLifeLogs() {
        return lifeLogMapper.selectList(Wrappers.lambdaQuery(LifeLogDO.class)
                .orderByDesc(LifeLogDO::getLogDate)
                .orderByDesc(LifeLogDO::getId)
                .last("limit 2"));
    }

    private DashboardVO.LifeLogItem toLifeItem(LifeLogDO log) {
        return new DashboardVO.LifeLogItem(
                String.valueOf(log.getId()),
                log.getTitle(),
                log.getLogDate() == null ? "" : log.getLogDate().toString(),
                log.getCoverFileId() == null ? null : "/api/files/" + log.getCoverFileId() + "/thumb",
                log.getMood());
    }

    private <T> T safe(Supplier<T> supplier, T fallback, AtomicBoolean partial, String block) {
        try {
            return supplier.get();
        } catch (RuntimeException ex) {
            partial.set(true);
            log.warn("dashboard block degraded: {}", block, ex);
            return fallback;
        }
    }
}
