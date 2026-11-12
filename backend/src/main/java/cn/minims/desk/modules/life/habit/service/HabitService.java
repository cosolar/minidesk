package cn.minims.desk.modules.life.habit.service;

import cn.minims.desk.common.api.ErrorCode;
import cn.minims.desk.common.exception.BizException;
import cn.minims.desk.modules.life.habit.entity.HabitDO;
import cn.minims.desk.modules.life.habit.entity.HabitLogDO;
import cn.minims.desk.modules.life.habit.mapper.HabitLogMapper;
import cn.minims.desk.modules.life.habit.mapper.HabitMapper;
import cn.minims.desk.modules.life.habit.vo.HabitVO;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 打卡业务：学习打卡与生活习惯共用一套表，用 module 区分。
 */
@Service
public class HabitService {

    private final HabitMapper habitMapper;
    private final HabitLogMapper logMapper;

    public HabitService(HabitMapper habitMapper, HabitLogMapper logMapper) {
        this.habitMapper = habitMapper;
        this.logMapper = logMapper;
    }

    public List<HabitVO> list(String module) {
        var query = Wrappers.lambdaQuery(HabitDO.class).eq(HabitDO::getArchived, 0);
        if (StringUtils.hasText(module)) {
            query.eq(HabitDO::getModule, module);
        }
        query.orderByAsc(HabitDO::getSortOrder).orderByAsc(HabitDO::getId);
        List<HabitDO> habits = habitMapper.selectList(query);
        if (habits.isEmpty()) {
            return List.of();
        }

        LocalDate today = LocalDate.now();
        List<Long> ids = habits.stream().map(HabitDO::getId).toList();
        // 一次取近 90 天记录，够算连续天数，避免按习惯逐个查
        List<HabitLogDO> logs = logMapper.selectList(Wrappers.lambdaQuery(HabitLogDO.class)
                .in(HabitLogDO::getHabitId, ids)
                .ge(HabitLogDO::getLogDate, today.minusDays(90)));

        Map<Long, List<HabitLogDO>> byHabit = new HashMap<>();
        for (HabitLogDO log : logs) {
            byHabit.computeIfAbsent(log.getHabitId(), key -> new ArrayList<>()).add(log);
        }

        List<HabitVO> result = new ArrayList<>(habits.size());
        for (HabitDO habit : habits) {
            List<HabitLogDO> own = byHabit.getOrDefault(habit.getId(), List.of());
            int todayValue = own.stream()
                    .filter(log -> today.equals(log.getLogDate()))
                    .mapToInt(log -> log.getValue() == null ? 0 : log.getValue())
                    .sum();
            int target = habit.getTargetValue() == null ? 1 : habit.getTargetValue();
            result.add(new HabitVO(
                    String.valueOf(habit.getId()),
                    habit.getModule(),
                    habit.getName(),
                    habit.getIcon(),
                    habit.getColor(),
                    habit.getType(),
                    target,
                    habit.getUnit(),
                    todayValue,
                    todayValue >= target,
                    streakOf(own, today)));
        }
        return result;
    }

    public HabitVO.Heatmap heatmap(Long habitId, Integer year) {
        HabitDO habit = require(habitId);
        int targetYear = year == null ? LocalDate.now().getYear() : year;
        LocalDate from = LocalDate.of(targetYear, 1, 1);
        LocalDate to = LocalDate.of(targetYear, 12, 31);

        List<HabitLogDO> logs = logMapper.selectList(Wrappers.lambdaQuery(HabitLogDO.class)
                .eq(HabitLogDO::getHabitId, habitId)
                .between(HabitLogDO::getLogDate, from, to)
                .orderByAsc(HabitLogDO::getLogDate));

        List<HabitVO.Heatmap.Day> days = new ArrayList<>(logs.size());
        for (HabitLogDO log : logs) {
            days.add(new HabitVO.Heatmap.Day(log.getLogDate().toString(),
                    log.getValue() == null ? 0 : log.getValue()));
        }

        int longest = longestStreak(logs);
        int current = streakOf(logs, LocalDate.now());

        return new HabitVO.Heatmap(
                String.valueOf(habitId),
                habit.getName(),
                targetYear,
                current,
                longest,
                logs.size(),
                days);
    }

    @Transactional(rollbackFor = Exception.class)
    public HabitVO create(String module, String name, String icon, String color,
                          String type, Integer targetValue, String unit) {
        if (!StringUtils.hasText(name)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "请输入打卡名称");
        }
        HabitDO habit = new HabitDO();
        habit.setModule(StringUtils.hasText(module) ? module : "life");
        habit.setName(name.trim());
        habit.setIcon(icon);
        habit.setColor(StringUtils.hasText(color) ? color : "#35C79B");
        habit.setType(StringUtils.hasText(type) ? type : "boolean");
        habit.setTargetValue(targetValue == null ? 1 : Math.max(targetValue, 1));
        habit.setUnit(unit);
        habit.setArchived(0);
        habit.setSortOrder(0);
        habit.setCreatedAt(LocalDateTime.now());
        habitMapper.insert(habit);
        return list(habit.getModule()).stream()
                .filter(item -> item.id().equals(String.valueOf(habit.getId())))
                .findFirst()
                .orElseThrow();
    }

    @Transactional(rollbackFor = Exception.class)
    public void archive(Long id) {
        HabitDO habit = require(id);
        habit.setArchived(1);
        habitMapper.updateById(habit);
    }

    /** 打卡 / 取消打卡：value 为 0 视为取消 */
    @Transactional(rollbackFor = Exception.class)
    public void checkin(Long habitId, LocalDate date, Integer value) {
        HabitDO habit = require(habitId);
        LocalDate logDate = date == null ? LocalDate.now() : date;
        int target = habit.getTargetValue() == null ? 1 : habit.getTargetValue();
        int finalValue = value == null ? target : value;

        HabitLogDO existing = logMapper.selectOne(Wrappers.lambdaQuery(HabitLogDO.class)
                .eq(HabitLogDO::getHabitId, habitId)
                .eq(HabitLogDO::getLogDate, logDate)
                .last("limit 1"));

        if (finalValue <= 0) {
            if (existing != null) {
                logMapper.deleteById(existing.getId());
            }
            return;
        }
        if (existing != null) {
            existing.setValue(finalValue);
            logMapper.updateById(existing);
            return;
        }
        HabitLogDO log = new HabitLogDO();
        log.setHabitId(habitId);
        log.setLogDate(logDate);
        log.setValue(finalValue);
        log.setCreatedAt(LocalDateTime.now());
        logMapper.insert(log);
    }

    private HabitDO require(Long id) {
        HabitDO habit = habitMapper.selectById(id);
        if (habit == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        return habit;
    }

    private int streakOf(List<HabitLogDO> logs, LocalDate today) {
        java.util.Set<LocalDate> dates = new java.util.HashSet<>();
        for (HabitLogDO log : logs) {
            dates.add(log.getLogDate());
        }
        LocalDate cursor = today;
        if (!dates.contains(cursor)) {
            cursor = cursor.minusDays(1);
        }
        int streak = 0;
        while (dates.contains(cursor)) {
            streak++;
            cursor = cursor.minusDays(1);
        }
        return streak;
    }

    private int longestStreak(List<HabitLogDO> logs) {
        if (logs.isEmpty()) {
            return 0;
        }
        int longest = 1;
        int current = 1;
        LocalDate prev = logs.get(0).getLogDate();
        for (int i = 1; i < logs.size(); i++) {
            LocalDate date = logs.get(i).getLogDate();
            if (date.equals(prev.plusDays(1))) {
                current++;
            } else if (!date.equals(prev)) {
                current = 1;
            }
            longest = Math.max(longest, current);
            prev = date;
        }
        return longest;
    }
}
