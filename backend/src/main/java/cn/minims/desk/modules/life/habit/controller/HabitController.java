package cn.minims.desk.modules.life.habit.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.minims.desk.common.api.Result;
import cn.minims.desk.framework.satoken.PermissionCodes;
import cn.minims.desk.modules.life.habit.service.HabitService;
import cn.minims.desk.modules.life.habit.vo.HabitVO;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 打卡接口，见 docs/接口清单.md §8（学习打卡）与 §9（生活习惯）。
 */
@RestController
@RequestMapping("/api/habits")
public class HabitController {

    private final HabitService habitService;

    public HabitController(HabitService habitService) {
        this.habitService = habitService;
    }

    @GetMapping
    public Result<List<HabitVO>> list(@RequestParam(required = false) String module) {
        return Result.ok(habitService.list(module));
    }

    @PostMapping
    @SaCheckPermission(PermissionCodes.HABIT_MANAGE)
    public Result<HabitVO> create(@RequestBody Map<String, Object> body) {
        String module = body.get("module") == null ? null : String.valueOf(body.get("module"));
        String name = body.get("name") == null ? null : String.valueOf(body.get("name"));
        String icon = body.get("icon") == null ? null : String.valueOf(body.get("icon"));
        String color = body.get("color") == null ? null : String.valueOf(body.get("color"));
        String type = body.get("type") == null ? null : String.valueOf(body.get("type"));
        Integer target = body.get("targetValue") == null
                ? null : Integer.valueOf(String.valueOf(body.get("targetValue")));
        String unit = body.get("unit") == null ? null : String.valueOf(body.get("unit"));
        return Result.ok(habitService.create(module, name, icon, color, type, target, unit));
    }

    @PostMapping("/{id}/checkin")
    @SaCheckPermission(PermissionCodes.HABIT_MANAGE)
    public Result<Void> checkin(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        LocalDate date = body.get("date") == null ? null : LocalDate.parse(String.valueOf(body.get("date")));
        Integer value = body.get("value") == null ? null : Integer.valueOf(String.valueOf(body.get("value")));
        habitService.checkin(id, date, value);
        return Result.ok();
    }

    @GetMapping("/{id}/heatmap")
    public Result<HabitVO.Heatmap> heatmap(@PathVariable Long id,
                                           @RequestParam(required = false) Integer year) {
        return Result.ok(habitService.heatmap(id, year));
    }

    @DeleteMapping("/{id}")
    @SaCheckPermission(PermissionCodes.HABIT_MANAGE)
    public Result<Void> archive(@PathVariable Long id) {
        habitService.archive(id);
        return Result.ok();
    }
}
