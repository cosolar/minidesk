package cn.minims.desk.modules.work.meeting.service;

import cn.minims.desk.common.api.ErrorCode;
import cn.minims.desk.common.exception.BizException;
import cn.minims.desk.modules.work.meeting.dto.MeetingSaveDTO;
import cn.minims.desk.modules.work.meeting.entity.MeetingActionDO;
import cn.minims.desk.modules.work.meeting.entity.MeetingAttendeeDO;
import cn.minims.desk.modules.work.meeting.entity.MeetingDO;
import cn.minims.desk.modules.work.meeting.mapper.MeetingActionMapper;
import cn.minims.desk.modules.work.meeting.mapper.MeetingAttendeeMapper;
import cn.minims.desk.modules.work.meeting.mapper.MeetingMapper;
import cn.minims.desk.modules.work.meeting.vo.MeetingVO;
import cn.minims.desk.modules.work.task.entity.TaskDO;
import cn.minims.desk.modules.work.task.mapper.TaskMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 会议纪要。
 *
 * <p>设计要点：跟进事项是「会议的产出」，必须能一键变任务。这里保留 task_id，
 * 转换后按钮变「已建任务」，避免点两次生成两条重复待办。
 */
@Service
public class MeetingService {

    private final MeetingMapper meetingMapper;
    private final MeetingAttendeeMapper attendeeMapper;
    private final MeetingActionMapper actionMapper;
    private final TaskMapper taskMapper;

    public MeetingService(MeetingMapper meetingMapper, MeetingAttendeeMapper attendeeMapper,
                          MeetingActionMapper actionMapper, TaskMapper taskMapper) {
        this.meetingMapper = meetingMapper;
        this.attendeeMapper = attendeeMapper;
        this.actionMapper = actionMapper;
        this.taskMapper = taskMapper;
    }

    public List<MeetingVO> list(String keyword) {
        var query = Wrappers.lambdaQuery(MeetingDO.class);
        if (StringUtils.hasText(keyword)) {
            query.and(w -> w.like(MeetingDO::getTitle, keyword.trim())
                    .or().like(MeetingDO::getContent, keyword.trim()));
        }
        query.orderByDesc(MeetingDO::getMeetingDate).orderByDesc(MeetingDO::getId).last("limit 200");
        return meetingMapper.selectList(query).stream().map(item -> toVO(item, false)).toList();
    }

    public MeetingVO detail(Long id) {
        return toVO(require(id), true);
    }

    @Transactional(rollbackFor = Exception.class)
    public MeetingVO create(MeetingSaveDTO dto) {
        MeetingDO item = new MeetingDO();
        item.setMeetingDate(dto.meetingDate() == null ? LocalDate.now() : dto.meetingDate());
        apply(item, dto);
        item.setCreatedAt(LocalDateTime.now());
        item.setUpdatedAt(LocalDateTime.now());
        meetingMapper.insert(item);
        saveChildren(item.getId(), dto);
        return toVO(item, true);
    }

    @Transactional(rollbackFor = Exception.class)
    public MeetingVO update(Long id, MeetingSaveDTO dto) {
        MeetingDO item = require(id);
        apply(item, dto);
        item.setUpdatedAt(LocalDateTime.now());
        meetingMapper.updateById(item);
        attendeeMapper.delete(Wrappers.lambdaQuery(MeetingAttendeeDO.class)
                .eq(MeetingAttendeeDO::getMeetingId, id));
        actionMapper.delete(Wrappers.lambdaQuery(MeetingActionDO.class)
                .eq(MeetingActionDO::getMeetingId, id));
        saveChildren(id, dto);
        return toVO(item, true);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        require(id);
        meetingMapper.deleteById(id);
        attendeeMapper.delete(Wrappers.lambdaQuery(MeetingAttendeeDO.class)
                .eq(MeetingAttendeeDO::getMeetingId, id));
        actionMapper.delete(Wrappers.lambdaQuery(MeetingActionDO.class)
                .eq(MeetingActionDO::getMeetingId, id));
    }

    /** 跟进事项转任务：已转过则直接返回原任务 id */
    @Transactional(rollbackFor = Exception.class)
    public Long actionToTask(Long actionId) {
        MeetingActionDO action = actionMapper.selectById(actionId);
        if (action == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        if (action.getTaskId() != null) {
            return action.getTaskId();
        }
        TaskDO task = new TaskDO();
        task.setTitle(action.getContent());
        task.setModule("work");
        task.setTaskType("followup");
        task.setPriority(2);
        task.setStatus("todo");
        task.setDueDate(action.getDueDate());
        task.setRefType("meeting");
        task.setRefId(action.getMeetingId());
        task.setSortOrder(0);
        task.setCreatedAt(LocalDateTime.now());
        task.setUpdatedAt(LocalDateTime.now());
        taskMapper.insert(task);

        action.setTaskId(task.getId());
        actionMapper.updateById(action);
        return task.getId();
    }

    private void apply(MeetingDO item, MeetingSaveDTO dto) {
        item.setTitle(dto.title().trim());
        item.setStartTime(dto.startTime());
        item.setEndTime(dto.endTime());
        item.setLocation(dto.location());
        item.setHost(dto.host());
        item.setContent(dto.content());
        item.setProjectId(dto.projectId());
    }

    private void saveChildren(Long meetingId, MeetingSaveDTO dto) {
        if (dto.attendees() != null) {
            dto.attendees().stream()
                    .filter(StringUtils::hasText)
                    .forEach(name -> {
                        MeetingAttendeeDO attendee = new MeetingAttendeeDO();
                        attendee.setMeetingId(meetingId);
                        attendee.setName(name.trim());
                        attendeeMapper.insert(attendee);
                    });
        }
        if (dto.actions() != null) {
            dto.actions().stream()
                    .filter(action -> action != null && StringUtils.hasText(action.content()))
                    .forEach(action -> {
                        MeetingActionDO entity = new MeetingActionDO();
                        entity.setMeetingId(meetingId);
                        entity.setContent(action.content().trim());
                        entity.setOwner(action.owner());
                        entity.setDueDate(action.dueDate());
                        entity.setStatus("open");
                        actionMapper.insert(entity);
                    });
        }
    }

    private MeetingDO require(Long id) {
        MeetingDO item = meetingMapper.selectById(id);
        if (item == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        return item;
    }

    private MeetingVO toVO(MeetingDO item, boolean withContent) {
        List<String> attendees = attendeeMapper.selectList(Wrappers.lambdaQuery(MeetingAttendeeDO.class)
                        .eq(MeetingAttendeeDO::getMeetingId, item.getId()))
                .stream().map(MeetingAttendeeDO::getName).toList();
        List<MeetingVO.ActionVO> actions = new ArrayList<>(actionMapper.selectList(
                        Wrappers.lambdaQuery(MeetingActionDO.class)
                                .eq(MeetingActionDO::getMeetingId, item.getId())
                                .orderByAsc(MeetingActionDO::getId))
                .stream()
                .map(a -> new MeetingVO.ActionVO(a.getId(), a.getContent(), a.getOwner(),
                        a.getDueDate(), a.getTaskId(), a.getStatus()))
                .toList());
        return new MeetingVO(item.getId(), item.getTitle(), item.getMeetingDate(), item.getStartTime(),
                item.getEndTime(), item.getLocation(), item.getHost(),
                withContent ? item.getContent() : null, item.getProjectId(),
                attendees, actions, item.getCreatedAt());
    }
}
