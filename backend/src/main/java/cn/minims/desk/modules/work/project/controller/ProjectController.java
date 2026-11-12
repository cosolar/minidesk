package cn.minims.desk.modules.work.project.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.minims.desk.common.api.Result;
import cn.minims.desk.modules.work.project.dto.ProjectSaveDTO;
import cn.minims.desk.modules.work.project.service.ProjectService;
import cn.minims.desk.modules.work.project.vo.ProjectVO;
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
 * 项目接口，见 docs/接口清单.md §5。
 */
@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @GetMapping
    public Result<List<ProjectVO>> list(@RequestParam(required = false) String status,
                                        @RequestParam(required = false) String keyword) {
        return Result.ok(projectService.list(status, keyword));
    }

    @GetMapping("/{id}")
    public Result<ProjectVO> detail(@PathVariable Long id) {
        return Result.ok(projectService.detail(id));
    }

    @PostMapping
    @SaCheckPermission("work:project:add")
    public Result<ProjectVO> create(@Valid @RequestBody ProjectSaveDTO dto) {
        return Result.ok(projectService.create(dto));
    }

    @PutMapping("/{id}")
    @SaCheckPermission("work:project:update")
    public Result<ProjectVO> update(@PathVariable Long id, @Valid @RequestBody ProjectSaveDTO dto) {
        return Result.ok(projectService.update(id, dto));
    }

    @DeleteMapping("/{id}")
    @SaCheckPermission("work:project:delete")
    public Result<Void> delete(@PathVariable Long id) {
        projectService.delete(id);
        return Result.ok();
    }
}
