package cn.minims.desk.modules.study.plan.service;

import cn.minims.desk.common.api.ErrorCode;
import cn.minims.desk.common.exception.BizException;
import cn.minims.desk.modules.study.plan.entity.StudyPlanDO;
import cn.minims.desk.modules.study.plan.mapper.StudyPlanMapper;
import cn.minims.desk.modules.study.plan.vo.StudyPlanVO;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.math.BigDecimal;
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
 * 学习计划：三级树 + 进度自底向上汇总。
 */
@Service
public class StudyPlanService {

    private final StudyPlanMapper planMapper;

    public StudyPlanService(StudyPlanMapper planMapper) {
        this.planMapper = planMapper;
    }

    public List<StudyPlanVO> forest() {
        List<StudyPlanDO> plans = planMapper.selectList(Wrappers.lambdaQuery(StudyPlanDO.class)
                .orderByAsc(StudyPlanDO::getLevel)
                .orderByAsc(StudyPlanDO::getStartDate)
                .orderByAsc(StudyPlanDO::getId));

        Map<Long, long[]> tasks = new HashMap<>();
        for (Map<String, Object> row : planMapper.countTasksByPlan()) {
            long total = ((Number) row.get("total")).longValue();
            long done = row.get("done") == null ? 0 : ((Number) row.get("done")).longValue();
            tasks.put(((Number) row.get("planId")).longValue(), new long[]{total, done});
        }

        Map<Long, List<StudyPlanDO>> byParent = new HashMap<>();
        for (StudyPlanDO plan : plans) {
            byParent.computeIfAbsent(plan.getParentId() == null ? 0L : plan.getParentId(),
                    key -> new ArrayList<>()).add(plan);
        }

        List<StudyPlanVO> roots = new ArrayList<>();
        for (StudyPlanDO plan : byParent.getOrDefault(0L, List.of())) {
            roots.add(build(plan, byParent, tasks));
        }
        return roots;
    }

    @Transactional(rollbackFor = Exception.class)
    public StudyPlanVO create(String title, Integer level, Long parentId, Double targetHours,
                              LocalDate startDate, LocalDate endDate) {
        if (!StringUtils.hasText(title)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "请输入计划名称");
        }
        int finalLevel = level == null ? 1 : Math.min(Math.max(level, 1), 3);
        StudyPlanDO plan = new StudyPlanDO();
        plan.setLevel(finalLevel);
        plan.setParentId(parentId);
        plan.setTitle(title.trim());
        plan.setTargetHours(BigDecimal.valueOf(targetHours == null ? 0 : targetHours));
        plan.setDoneHoursCache(BigDecimal.ZERO);
        plan.setStartDate(startDate == null ? LocalDate.now() : startDate);
        plan.setEndDate(endDate);
        plan.setStatus("doing");
        plan.setSortOrder(0);
        plan.setCreatedAt(LocalDateTime.now());
        plan.setUpdatedAt(LocalDateTime.now());
        planMapper.insert(plan);
        return toVO(plan, new long[]{0, 0}, List.of());
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, String title, Double targetHours, LocalDate startDate,
                       LocalDate endDate, String status) {
        StudyPlanDO plan = require(id);
        if (StringUtils.hasText(title)) {
            plan.setTitle(title.trim());
        }
        if (targetHours != null) {
            plan.setTargetHours(BigDecimal.valueOf(targetHours));
        }
        if (startDate != null) {
            plan.setStartDate(startDate);
        }
        if (endDate != null) {
            plan.setEndDate(endDate);
        }
        if (StringUtils.hasText(status)) {
            plan.setStatus(status);
        }
        plan.setUpdatedAt(LocalDateTime.now());
        planMapper.updateById(plan);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        require(id);
        // 级联删除子计划，避免留下孤儿节点
        List<StudyPlanDO> children = planMapper.selectList(
                Wrappers.lambdaQuery(StudyPlanDO.class).eq(StudyPlanDO::getParentId, id));
        for (StudyPlanDO child : children) {
            delete(child.getId());
        }
        planMapper.deleteById(id);
    }

    private StudyPlanVO build(StudyPlanDO plan, Map<Long, List<StudyPlanDO>> byParent,
                              Map<Long, long[]> tasks) {
        List<StudyPlanDO> childDOs = byParent.getOrDefault(plan.getId(), List.of());
        List<StudyPlanVO> children = new ArrayList<>(childDOs.size());
        long total = 0;
        long done = 0;
        int progress;

        if (childDOs.isEmpty()) {
            long[] counts = tasks.getOrDefault(plan.getId(), new long[]{0, 0});
            total = counts[0];
            done = counts[1];
            progress = total == 0 ? 0 : (int) Math.round(done * 100.0 / total);
        } else {
            int sum = 0;
            for (StudyPlanDO child : childDOs) {
                StudyPlanVO childVO = build(child, byParent, tasks);
                children.add(childVO);
                sum += childVO.progress();
                total += childVO.taskTotal();
                done += childVO.taskDone();
            }
            progress = Math.round((float) sum / children.size());
        }

        return toVO(plan, new long[]{total, done}, children).withProgress(progress);
    }

    private StudyPlanVO toVO(StudyPlanDO plan, long[] counts, List<StudyPlanVO> children) {
        return new StudyPlanVO(
                String.valueOf(plan.getId()),
                plan.getParentId() == null ? null : String.valueOf(plan.getParentId()),
                plan.getLevel(),
                plan.getTitle(),
                plan.getTargetHours() == null ? 0 : plan.getTargetHours().doubleValue(),
                0,
                counts[0],
                counts[1],
                plan.getStartDate() == null ? null : plan.getStartDate().toString(),
                plan.getEndDate() == null ? null : plan.getEndDate().toString(),
                plan.getStatus(),
                children);
    }

    private StudyPlanDO require(Long id) {
        StudyPlanDO plan = planMapper.selectById(id);
        if (plan == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        return plan;
    }
}
