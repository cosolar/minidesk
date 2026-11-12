package cn.minims.desk.modules.life.lifelog.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.minims.desk.common.api.PageResult;
import cn.minims.desk.common.api.Result;
import cn.minims.desk.framework.satoken.PermissionCodes;
import cn.minims.desk.modules.life.entity.LifeLogDO;
import cn.minims.desk.modules.life.lifelog.service.LifeLogService;
import java.time.LocalDate;
import java.util.Map;
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
 * 生活记录接口，见 docs/接口清单.md §9。
 */
@RestController
@RequestMapping("/api/life-logs")
public class LifeLogController {

    private final LifeLogService lifeLogService;

    public LifeLogController(LifeLogService lifeLogService) {
        this.lifeLogService = lifeLogService;
    }

    @GetMapping
    public Result<PageResult<LifeLogDO>> page(@RequestParam(required = false) String month,
                                              @RequestParam(defaultValue = "1") long page,
                                              @RequestParam(defaultValue = "20") long size) {
        long safeSize = Math.min(Math.max(size, 1), 100);
        return Result.ok(lifeLogService.page(month, Math.max(page, 1), safeSize));
    }

    @PostMapping
    @SaCheckPermission(PermissionCodes.NOTE_MANAGE)
    public Result<LifeLogDO> create(@RequestBody Map<String, Object> body) {
        String title = body.get("title") == null ? null : String.valueOf(body.get("title"));
        String content = body.get("content") == null ? null : String.valueOf(body.get("content"));
        String mood = body.get("mood") == null ? null : String.valueOf(body.get("mood"));
        LocalDate date = body.get("logDate") == null ? null : LocalDate.parse(String.valueOf(body.get("logDate")));
        return Result.ok(lifeLogService.create(title, content, mood, date));
    }

    @PutMapping("/{id}")
    @SaCheckPermission(PermissionCodes.NOTE_MANAGE)
    public Result<Void> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        lifeLogService.update(id, body);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    @SaCheckPermission(PermissionCodes.NOTE_MANAGE)
    public Result<Void> delete(@PathVariable Long id) {
        lifeLogService.delete(id);
        return Result.ok();
    }
}
