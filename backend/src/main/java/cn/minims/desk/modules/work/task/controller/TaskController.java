package cn.minims.desk.modules.work.task.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.minims.desk.common.api.PageResult;
import cn.minims.desk.common.api.Result;
import cn.minims.desk.framework.satoken.PermissionCodes;
import cn.minims.desk.modules.work.task.dto.TaskSaveDTO;
import cn.minims.desk.modules.work.task.service.TaskService;
import cn.minims.desk.modules.work.task.vo.TaskVO;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 任务接口，见 docs/接口清单.md §4。
 */
@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @GetMapping
    public Result<PageResult<TaskVO>> page(
            @RequestParam(required = false) String module,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) Long projectId,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        long safeSize = Math.min(Math.max(size, 1), 100);
        return Result.ok(taskService.page(module, status, keyword, date, projectId, Math.max(page, 1), safeSize));
    }

    @GetMapping("/today")
    public Result<List<TaskVO>> today() {
        return Result.ok(taskService.today());
    }

    @PostMapping
    @SaCheckPermission(PermissionCodes.WORK_TASK_ADD)
    public Result<TaskVO> create(@Valid @RequestBody TaskSaveDTO dto) {
        return Result.ok(taskService.create(dto));
    }

    @PutMapping("/{id}")
    @SaCheckPermission(PermissionCodes.WORK_TASK_UPDATE)
    public Result<TaskVO> update(@PathVariable Long id, @Valid @RequestBody TaskSaveDTO dto) {
        return Result.ok(taskService.update(id, dto));
    }

    @PatchMapping("/{id}/done")
    @SaCheckPermission(PermissionCodes.WORK_TASK_UPDATE)
    public Result<TaskVO> toggleDone(@PathVariable Long id, @RequestBody Map<String, Boolean> body) {
        return Result.ok(taskService.toggleDone(id, Boolean.TRUE.equals(body.get("done"))));
    }

    @PatchMapping("/order")
    @SaCheckPermission(PermissionCodes.WORK_TASK_UPDATE)
    public Result<Void> reorder(@RequestBody Map<String, List<Long>> body) {
        taskService.reorder(body.get("ids"));
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    @SaCheckPermission(PermissionCodes.WORK_TASK_DELETE)
    public Result<Void> delete(@PathVariable Long id) {
        taskService.delete(id);
        return Result.ok();
    }
}
