package cn.minims.desk.modules.common.calendar.service;

import cn.minims.desk.common.api.ErrorCode;
import cn.minims.desk.common.exception.BizException;
import cn.minims.desk.framework.tenant.TenantContext;
import cn.minims.desk.modules.common.calendar.dto.CalendarEventSaveDTO;
import cn.minims.desk.modules.common.calendar.entity.AnniversaryDO;
import cn.minims.desk.modules.common.calendar.entity.CalendarEventDO;
import cn.minims.desk.modules.common.calendar.entity.CountdownDO;
import cn.minims.desk.modules.common.calendar.mapper.AnniversaryMapper;
import cn.minims.desk.modules.common.calendar.mapper.CalendarEventMapper;
import cn.minims.desk.modules.common.calendar.mapper.CountdownMapper;
import cn.minims.desk.modules.common.calendar.vo.CalendarItemVO;
import cn.minims.desk.modules.work.task.entity.TaskDO;
import cn.minims.desk.modules.work.task.mapper.TaskMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 日历聚合：任务截止日、日历事件、纪念日、倒数日四源合并。
 * 任务不双写日历表，避免改期/删除后不同步。
 */
@Service
public class CalendarService {

    private static final Logger log = LoggerFactory.getLogger(CalendarService.class);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATETIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final CalendarEventMapper eventMapper;
    private final AnniversaryMapper anniversaryMapper;
    private final CountdownMapper countdownMapper;
    private final TaskMapper taskMapper;
    private final JdbcTemplate jdbcTemplate;

    public CalendarService(CalendarEventMapper eventMapper,
                           AnniversaryMapper anniversaryMapper,
                           CountdownMapper countdownMapper,
                           TaskMapper taskMapper,
                           JdbcTemplate jdbcTemplate) {
        this.eventMapper = eventMapper;
        this.anniversaryMapper = anniversaryMapper;
        this.countdownMapper = countdownMapper;
        this.taskMapper = taskMapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<CalendarItemVO> range(LocalDate start, LocalDate end) {
        LocalDate from = start == null ? LocalDate.now().withDayOfMonth(1) : start;
        LocalDate to = end == null ? from.plusMonths(1).minusDays(1) : end;
        if (from.isAfter(to)) {
            return List.of();
        }

        List<CalendarItemVO> items = new ArrayList<>(32);
        collectEvents(from, to, items);
        collectTasks(from, to, items);
        collectAnniversaries(from, to, items);
        collectCountdowns(from, to, items);
        collectExams(from, to, items);
        collectBirthdays(from, to, items);

        items.sort(Comparator.comparing(CalendarItemVO::start));
        return items;
    }

    private void collectEvents(LocalDate from, LocalDate to, List<CalendarItemVO> items) {
        try {
            List<CalendarEventDO> events = eventMapper.selectList(Wrappers.lambdaQuery(CalendarEventDO.class)
                    .ge(CalendarEventDO::getStartTime, from.atStartOfDay())
                    .le(CalendarEventDO::getStartTime, to.atTime(23, 59, 59)));
            for (CalendarEventDO event : events) {
                boolean allDay = event.getAllDay() != null && event.getAllDay() == 1;
                String value = allDay ? event.getStartTime().format(DATE) : event.getStartTime().format(DATETIME);
                items.add(new CalendarItemVO(
                        String.valueOf(event.getId()), "event", event.getTitle(), value, allDay,
                        event.getColor() == null ? "#35C79B" : event.getColor(), false));
            }
        } catch (RuntimeException ex) {
            log.warn("collect calendar events failed", ex);
        }
    }

    private void collectTasks(LocalDate from, LocalDate to, List<CalendarItemVO> items) {
        try {
            List<TaskDO> tasks = taskMapper.selectList(Wrappers.lambdaQuery(TaskDO.class)
                    .and(w -> w.between(TaskDO::getDueDate, from, to)
                            .or().between(TaskDO::getStartDate, from, to))
                    .ne(TaskDO::getStatus, "cancelled"));
            for (TaskDO task : tasks) {
                String color = switch (task.getModule() == null ? "work" : task.getModule()) {
                    case "study" -> "#8B7CF6";
                    case "life" -> "#FF9F43";
                    default -> "#4A86E8";
                };
                boolean done = "done".equals(task.getStatus());
                LocalDate startDate = task.getStartDate();
                LocalDate dueDate = task.getDueDate();
                boolean hasStart = startDate != null && !startDate.isBefore(from) && !startDate.isAfter(to);
                boolean hasDue = dueDate != null && !dueDate.isBefore(from) && !dueDate.isAfter(to);

                // 有开始日期的任务拆成「开始 / 截止」两个落点，日历上才看得出工期
                if (hasStart && (!hasDue || !startDate.equals(dueDate))) {
                    boolean allDay = isBlank(task.getStartTime());
                    String value = startDate.format(DATE) + (allDay ? "" : " " + task.getStartTime());
                    items.add(new CalendarItemVO(task.getId() + "-start", "task",
                            "开始 · " + task.getTitle(), value, allDay, color, done));
                }
                if (hasDue) {
                    boolean allDay = isBlank(task.getDueTime());
                    String value = allDay ? dueDate.format(DATE)
                            : dueDate.format(DATE) + " " + task.getDueTime();
                    items.add(new CalendarItemVO(String.valueOf(task.getId()), "task", task.getTitle(),
                            value, allDay, color, done));
                }
            }
        } catch (RuntimeException ex) {
            log.warn("collect tasks failed", ex);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /** 纪念日按年重复时，只比对月日 */
    private void collectAnniversaries(LocalDate from, LocalDate to, List<CalendarItemVO> items) {
        try {
            for (AnniversaryDO item : anniversaryMapper.selectList(null)) {
                if (item.getDate() == null) {
                    continue;
                }
                boolean yearly = item.getRepeatYearly() == null || item.getRepeatYearly() == 1;
                LocalDate occur = item.getDate();
                if (yearly) {
                    occur = item.getDate().withYear(from.getYear());
                    if (occur.isBefore(from) && from.getYear() == to.getYear()) {
                        occur = occur.plusYears(1);
                    }
                }
                if (!occur.isBefore(from) && !occur.isAfter(to)) {
                    items.add(new CalendarItemVO(
                            String.valueOf(item.getId()), "anniversary", item.getTitle(),
                            occur.format(DATE), true, "#FF9F43", false));
                }
            }
        } catch (RuntimeException ex) {
            log.warn("collect anniversaries failed", ex);
        }
    }

    private void collectCountdowns(LocalDate from, LocalDate to, List<CalendarItemVO> items) {
        try {
            List<CountdownDO> list = countdownMapper.selectList(Wrappers.lambdaQuery(CountdownDO.class)
                    .between(CountdownDO::getTargetDate, from, to));
            for (CountdownDO item : list) {
                items.add(new CalendarItemVO(
                        String.valueOf(item.getId()), "countdown", item.getTitle(),
                        item.getTargetDate().format(DATE), true,
                        item.getColor() == null ? "#FF6B6B" : item.getColor(), false));
            }
        } catch (RuntimeException ex) {
            log.warn("collect countdowns failed", ex);
        }
    }

    /** 首页用：今天的日程 */
    public List<CalendarItemVO> today() {
        return range(LocalDate.now(), LocalDate.now());
    }

    public String nowText() {
        return LocalDateTime.now().format(DATETIME);
    }

    // === 日程事件 CRUD：日历页可以手动建会议、出差、生日会这类非任务事项 ===

    @Transactional(rollbackFor = Exception.class)
    public Long createEvent(CalendarEventSaveDTO dto) {
        CalendarEventDO event = new CalendarEventDO();
        applyEvent(event, dto);
        event.setSource("manual");
        event.setCreatedAt(LocalDateTime.now());
        event.setUpdatedAt(LocalDateTime.now());
        eventMapper.insert(event);
        return event.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateEvent(Long id, CalendarEventSaveDTO dto) {
        CalendarEventDO event = eventMapper.selectById(id);
        if (event == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        applyEvent(event, dto);
        event.setUpdatedAt(LocalDateTime.now());
        eventMapper.updateById(event);
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteEvent(Long id) {
        if (eventMapper.deleteById(id) == 0) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
    }

    /** 考试：只看考试日当天，颜色与学习区分开 */
    private void collectExams(LocalDate from, LocalDate to, List<CalendarItemVO> items) {
        try {
            for (var row : jdbcTemplate.queryForList(
                    "select id, name, exam_date from exam where user_id = ? "
                            + "and exam_date between ? and ? order by exam_date",
                    TenantContext.requireUserId(), from.toString(), to.toString())) {
                items.add(new CalendarItemVO(row.get("id").toString(), "exam",
                        String.valueOf(row.get("name")), String.valueOf(row.get("exam_date")),
                        true, "#F2789F", false));
            }
        } catch (RuntimeException ex) {
            log.warn("collect exams failed", ex);
        }
    }

    /** 联系人生日：按年重复，跨年时同时尝试下一年 */
    private void collectBirthdays(LocalDate from, LocalDate to, List<CalendarItemVO> items) {
        try {
            for (var row : jdbcTemplate.queryForList(
                    "select id, name, birthday from contact "
                            + "where user_id = ? and deleted = 0 and birthday is not null",
                    TenantContext.requireUserId())) {
                LocalDate origin = LocalDate.parse(String.valueOf(row.get("birthday")));
                for (int year = from.getYear(); year <= to.getYear(); year++) {
                    LocalDate occur = origin.withYear(year);
                    if (!occur.isBefore(from) && !occur.isAfter(to)) {
                        items.add(new CalendarItemVO(row.get("id").toString(), "birthday",
                                String.valueOf(row.get("name")) + " 生日", occur.toString(),
                                true, "#FF9F43", false));
                    }
                }
            }
        } catch (RuntimeException ex) {
            log.warn("collect birthdays failed", ex);
        }
    }

    private void applyEvent(CalendarEventDO event, CalendarEventSaveDTO dto) {
        event.setTitle(dto.title().trim());
        event.setDescription(dto.description());
        event.setStartTime(dto.startTime());
        event.setEndTime(dto.endTime());
        event.setAllDay(Boolean.TRUE.equals(dto.allDay()) ? 1 : 0);
        event.setLocation(dto.location());
        event.setColor(dto.color() == null || dto.color().isBlank() ? "#35C79B" : dto.color());
    }
}
