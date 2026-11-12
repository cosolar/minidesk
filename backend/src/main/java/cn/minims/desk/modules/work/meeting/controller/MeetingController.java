package cn.minims.desk.modules.work.meeting.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.minims.desk.common.api.Result;
import cn.minims.desk.framework.satoken.PermissionCodes;
import cn.minims.desk.modules.work.meeting.dto.MeetingSaveDTO;
import cn.minims.desk.modules.work.meeting.service.MeetingService;
import cn.minims.desk.modules.work.meeting.vo.MeetingVO;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 会议纪要接口。
 */
@RestController
@RequestMapping("/api/meetings")
public class MeetingController {

    private final MeetingService service;

    public MeetingController(MeetingService service) {
        this.service = service;
    }

    @GetMapping
    public Result<List<MeetingVO>> list(@RequestParam(required = false) String keyword) {
        return Result.ok(service.list(keyword));
    }

    @GetMapping("/{id}")
    public Result<MeetingVO> detail(@PathVariable Long id) {
        return Result.ok(service.detail(id));
    }

    @PostMapping
    @SaCheckPermission(PermissionCodes.WORK_MEETING_MANAGE)
    public Result<MeetingVO> create(@Valid @RequestBody MeetingSaveDTO dto) {
        return Result.ok(service.create(dto));
    }

    @PutMapping("/{id}")
    @SaCheckPermission(PermissionCodes.WORK_MEETING_MANAGE)
    public Result<MeetingVO> update(@PathVariable Long id, @Valid @RequestBody MeetingSaveDTO dto) {
        return Result.ok(service.update(id, dto));
    }

    @DeleteMapping("/{id}")
    @SaCheckPermission(PermissionCodes.WORK_MEETING_MANAGE)
    public Result<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return Result.ok();
    }

    /** 跟进事项转任务 */
    @PostMapping("/actions/{actionId}/task")
    @SaCheckPermission(PermissionCodes.WORK_TASK_ADD)
    public Result<Long> actionToTask(@PathVariable Long actionId) {
        return Result.ok(service.actionToTask(actionId));
    }
}
