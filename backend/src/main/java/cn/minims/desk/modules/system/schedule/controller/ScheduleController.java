package cn.minims.desk.modules.system.schedule.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.minims.desk.common.api.PageResult;
import cn.minims.desk.common.api.Result;
import cn.minims.desk.modules.system.schedule.service.ScheduleService;
import cn.minims.desk.modules.system.schedule.vo.ScheduleVO;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 后台定时任务接口，见 docs/接口清单.md。 */
@RestController
@RequestMapping("/api/console/schedule")
@SaCheckRole("ADMIN")
public class ScheduleController {

    private final ScheduleService scheduleService;

    public ScheduleController(ScheduleService scheduleService) {
        this.scheduleService = scheduleService;
    }

    @GetMapping("/jobs")
    public Result<List<ScheduleVO.JobItem>> jobs() {
        return Result.ok(scheduleService.list());
    }

    @PatchMapping("/jobs/{code}")
    public Result<List<ScheduleVO.JobItem>> update(
            @PathVariable String code, @RequestBody ScheduleVO.JobUpdate body) {
        return Result.ok(scheduleService.update(code, body.cronExpr(), body.enabled()));
    }

    @PostMapping("/jobs/{code}/trigger")
    public Result<ScheduleVO.RunResult> trigger(@PathVariable String code) {
        return Result.ok(scheduleService.trigger(code));
    }

    @GetMapping("/logs")
    public Result<PageResult<ScheduleVO.JobLog>> logs(
            @RequestParam(required = false) String jobCode,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        return Result.ok(scheduleService.logs(jobCode, Math.max(page, 1), Math.min(Math.max(size, 1), 100)));
    }
}
