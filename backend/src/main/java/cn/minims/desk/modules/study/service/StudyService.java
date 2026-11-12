package cn.minims.desk.modules.study.service;

import cn.minims.desk.common.api.ErrorCode;
import cn.minims.desk.common.exception.BizException;
import cn.minims.desk.modules.study.entity.StudyRecordDO;
import cn.minims.desk.modules.study.mapper.StudyRecordMapper;
import cn.minims.desk.modules.study.vo.StudyWeekVO;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 学习时长与统计。
 */
@Service
public class StudyService {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final String[] WEEKDAYS = {"一", "二", "三", "四", "五", "六", "日"};
    /** 无学习计划时的默认周目标（小时） */
    private static final double DEFAULT_TARGET_HOURS = 16.0;

    private final StudyRecordMapper recordMapper;

    public StudyService(StudyRecordMapper recordMapper) {
        this.recordMapper = recordMapper;
    }

    public StudyWeekVO weekStats() {
        LocalDate monday = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate sunday = monday.plusDays(6);

        Map<LocalDate, Long> current = sumByDay(monday, sunday);
        long total = current.values().stream().mapToLong(Long::longValue).sum();

        Map<LocalDate, Long> previous = sumByDay(monday.minusWeeks(1), sunday.minusWeeks(1));
        long prevTotal = previous.values().stream().mapToLong(Long::longValue).sum();

        // 上周为 0 时无意义，返回 null，前端显示 --
        Double growth = prevTotal == 0 ? null : Math.round((total - prevTotal) * 1000.0 / prevTotal) / 1000.0;

        List<StudyWeekVO.Day> days = new ArrayList<>(7);
        for (int i = 0; i < 7; i++) {
            LocalDate day = monday.plusDays(i);
            days.add(new StudyWeekVO.Day(
                    day.format(DATE), WEEKDAYS[i], current.getOrDefault(day, 0L)));
        }

        double target = DEFAULT_TARGET_HOURS;
        double completion = Math.min(1.0, total / (target * 60));

        return new StudyWeekVO(
                total,
                Math.round(total / 7.0),
                growth,
                Math.round(completion * 100) / 100.0,
                target,
                calcStreak(),
                days);
    }

    public List<StudyRecordDO> range(LocalDate start, LocalDate end) {
        LocalDate from = start == null ? LocalDate.now().withDayOfMonth(1) : start;
        LocalDate to = end == null ? LocalDate.now() : end;
        return recordMapper.selectList(Wrappers.lambdaQuery(StudyRecordDO.class)
                .between(StudyRecordDO::getStudyDate, from, to)
                .orderByDesc(StudyRecordDO::getStudyDate)
                .orderByDesc(StudyRecordDO::getId));
    }

    @Transactional(rollbackFor = Exception.class)
    public StudyRecordDO add(String subject, Integer minutes, LocalDate studyDate, Long planId, String note) {
        if (minutes == null || minutes <= 0 || minutes > 24 * 60) {
            throw new BizException(ErrorCode.PARAM_ERROR, "学习时长需在 1-1440 分钟之间");
        }
        StudyRecordDO record = new StudyRecordDO();
        record.setSubject(subject == null || subject.isBlank() ? "学习" : subject.trim());
        record.setMinutes(minutes);
        record.setStudyDate(studyDate == null ? LocalDate.now() : studyDate);
        record.setPlanId(planId);
        record.setNote(note);
        record.setCreatedAt(LocalDateTime.now());
        recordMapper.insert(record);
        return record;
    }

    /** 连续学习天数：从今天（或昨天）往前逐日比对 */
    public int calcStreak() {
        List<StudyRecordDO> recent = recordMapper.selectList(Wrappers.lambdaQuery(StudyRecordDO.class)
                .select(StudyRecordDO::getStudyDate)
                .orderByDesc(StudyRecordDO::getStudyDate)
                .last("limit 400"));
        Set<LocalDate> days = new LinkedHashSet<>();
        for (StudyRecordDO record : recent) {
            days.add(record.getStudyDate());
        }
        LocalDate cursor = LocalDate.now();
        if (!days.contains(cursor)) {
            cursor = cursor.minusDays(1);
        }
        int streak = 0;
        while (days.contains(cursor)) {
            streak++;
            cursor = cursor.minusDays(1);
        }
        return streak;
    }

    private Map<LocalDate, Long> sumByDay(LocalDate from, LocalDate to) {
        List<StudyRecordDO> records = recordMapper.selectList(Wrappers.lambdaQuery(StudyRecordDO.class)
                .between(StudyRecordDO::getStudyDate, from, to));
        Map<LocalDate, Long> result = new HashMap<>();
        for (StudyRecordDO record : records) {
            result.merge(record.getStudyDate(), record.getMinutes().longValue(), Long::sum);
        }
        return result;
    }
}
