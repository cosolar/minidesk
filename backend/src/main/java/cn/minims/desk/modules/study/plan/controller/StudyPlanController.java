package cn.minims.desk.modules.study.plan.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.minims.desk.common.api.Result;
import cn.minims.desk.modules.study.plan.service.StudyPlanService;
import cn.minims.desk.modules.study.plan.vo.StudyPlanVO;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 学习计划接口，见 docs/接口清单.md §8。
 */
@RestController
@RequestMapping("/api/study/plans")
public class StudyPlanController {

    private final StudyPlanService planService;

    public StudyPlanController(StudyPlanService planService) {
        this.planService = planService;
    }

    @GetMapping
    public Result<List<StudyPlanVO>> forest() {
        return Result.ok(planService.forest());
    }

    @PostMapping
    @SaCheckPermission("study:plan:update")
    public Result<StudyPlanVO> create(@RequestBody Map<String, Object> body) {
        String title = body.get("title") == null ? null : String.valueOf(body.get("title"));
        Integer level = body.get("level") == null ? null : Integer.valueOf(String.valueOf(body.get("level")));
        Long parentId = body.get("parentId") == null ? null : Long.valueOf(String.valueOf(body.get("parentId")));
        Double targetHours = body.get("targetHours") == null
                ? null : Double.valueOf(String.valueOf(body.get("targetHours")));
        LocalDate start = body.get("startDate") == null ? null : LocalDate.parse(String.valueOf(body.get("startDate")));
        LocalDate end = body.get("endDate") == null ? null : LocalDate.parse(String.valueOf(body.get("endDate")));
        return Result.ok(planService.create(title, level, parentId, targetHours, start, end));
    }

    @PutMapping("/{id}")
    @SaCheckPermission("study:plan:update")
    public Result<Void> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        String title = body.get("title") == null ? null : String.valueOf(body.get("title"));
        Double targetHours = body.get("targetHours") == null
                ? null : Double.valueOf(String.valueOf(body.get("targetHours")));
        LocalDate start = body.get("startDate") == null ? null : LocalDate.parse(String.valueOf(body.get("startDate")));
        LocalDate end = body.get("endDate") == null ? null : LocalDate.parse(String.valueOf(body.get("endDate")));
        String status = body.get("status") == null ? null : String.valueOf(body.get("status"));
        planService.update(id, title, targetHours, start, end, status);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    @SaCheckPermission("study:plan:update")
    public Result<Void> delete(@PathVariable Long id) {
        planService.delete(id);
        return Result.ok();
    }
}
