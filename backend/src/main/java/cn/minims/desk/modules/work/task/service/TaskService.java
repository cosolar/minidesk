package cn.minims.desk.modules.work.task.service;

import cn.minims.desk.common.api.ErrorCode;
import cn.minims.desk.common.api.PageResult;
import cn.minims.desk.common.exception.BizException;
import cn.minims.desk.modules.work.task.dto.TaskSaveDTO;
import cn.minims.desk.modules.work.task.entity.TaskDO;
import cn.minims.desk.modules.work.task.mapper.TaskMapper;
import cn.minims.desk.modules.work.task.vo.TaskVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 任务（统一事项）业务。
 */
@Service
public class TaskService {

    private static final String STATUS_DONE = "done";

    private final TaskMapper taskMapper;

    public TaskService(TaskMapper taskMapper) {
        this.taskMapper = taskMapper;
    }

    public PageResult<TaskVO> page(String module, String status, String keyword, LocalDate date,
                                   Long projectId, long pageNo, long pageSize) {
        LambdaQueryWrapper<TaskDO> query = Wrappers.lambdaQuery(TaskDO.class);
        if (StringUtils.hasText(module)) {
            query.eq(TaskDO::getModule, module);
        }
        if (StringUtils.hasText(status)) {
            query.eq(TaskDO::getStatus, status);
        }
        if (StringUtils.hasText(keyword)) {
            query.like(TaskDO::getTitle, keyword.trim());
        }
        if (date != null) {
            query.eq(TaskDO::getDueDate, date);
        }
        if (projectId != null) {
            query.eq(TaskDO::getProjectId, projectId);
        }
        // 未完成的排前面：status 是字符串，字母序会把 done 排到最前，这里显式按「是否完成」排序
        query.last("order by case when status = 'done' then 1 else 0 end, "
                + "sort_order asc, id desc");

        Page<TaskDO> result = taskMapper.selectPage(Page.of(pageNo, pageSize), query);
        List<TaskVO> list = result.getRecords().stream().map(TaskVO::from).toList();
        return PageResult.of(list, result.getTotal(), pageNo, pageSize);
    }

    /** 今日待办：按截止日等于今天，未完成的排前面 */
    public List<TaskVO> today() {
        List<TaskDO> list = taskMapper.selectList(Wrappers.lambdaQuery(TaskDO.class)
                .eq(TaskDO::getDueDate, LocalDate.now())
                .ne(TaskDO::getStatus, "cancelled")
                .orderByAsc(TaskDO::getStatus)
                .orderByAsc(TaskDO::getDueTime)
                .orderByAsc(TaskDO::getSortOrder));
        return list.stream().map(TaskVO::from).toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public TaskVO create(TaskSaveDTO dto) {
        TaskDO task = new TaskDO();
        apply(task, dto);
        task.setStatus(StringUtils.hasText(dto.status()) ? dto.status() : "todo");
        task.setSortOrder(0);
        task.setCreatedAt(LocalDateTime.now());
        task.setUpdatedAt(LocalDateTime.now());
        taskMapper.insert(task);
        return TaskVO.from(task);
    }

    @Transactional(rollbackFor = Exception.class)
    public TaskVO update(Long id, TaskSaveDTO dto) {
        TaskDO task = require(id);
        apply(task, dto);
        if (StringUtils.hasText(dto.status())) {
            task.setStatus(dto.status());
            if (STATUS_DONE.equals(dto.status()) && task.getDoneAt() == null) {
                task.setDoneAt(LocalDateTime.now());
            }
        }
        task.setUpdatedAt(LocalDateTime.now());
        taskMapper.updateById(task);
        return TaskVO.from(task);
    }

    @Transactional(rollbackFor = Exception.class)
    public TaskVO toggleDone(Long id, boolean done) {
        TaskDO task = require(id);
        task.setStatus(done ? STATUS_DONE : "todo");
        task.setDoneAt(done ? LocalDateTime.now() : null);
        task.setUpdatedAt(LocalDateTime.now());
        taskMapper.updateById(task);
        return TaskVO.from(task);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        require(id);
        taskMapper.deleteById(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public void reorder(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        for (int i = 0; i < ids.size(); i++) {
            TaskDO update = new TaskDO();
            update.setId(ids.get(i));
            update.setSortOrder(i);
            update.setUpdatedAt(now);
            taskMapper.updateById(update);
        }
    }

    private TaskDO require(Long id) {
        TaskDO task = taskMapper.selectById(id);
        if (task == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        return task;
    }

    private void apply(TaskDO task, TaskSaveDTO dto) {
        task.setTitle(dto.title().trim());
        task.setNote(dto.note());
        task.setModule(StringUtils.hasText(dto.module()) ? dto.module() : "work");
        task.setTaskType(StringUtils.hasText(dto.taskType()) ? dto.taskType() : "todo");
        task.setPriority(dto.priority() == null ? 2 : dto.priority());
        task.setStartDate(dto.startDate());
        task.setStartTime(dto.startTime());
        task.setDueDate(dto.dueDate());
        task.setDueTime(dto.dueTime());
        task.setRepeatRule(dto.repeatRule());
        task.setProjectId(dto.projectId());
        task.setPlanId(dto.planId());
        task.setParentId(dto.parentId());
        task.setTags(dto.tags());
        task.setEstimateMinutes(dto.estimateMinutes());
    }
}
