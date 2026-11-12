package cn.minims.desk.modules.common.navlink.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.minims.desk.common.api.Result;
import cn.minims.desk.framework.satoken.PermissionCodes;
import cn.minims.desk.modules.common.navlink.dto.NavLinkSaveDTO;
import cn.minims.desk.modules.common.navlink.service.NavLinkService;
import cn.minims.desk.modules.common.navlink.vo.NavLinkVO;
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
 * 导航链接接口：工作「工具」与学习「资源」共用。
 */
@RestController
@RequestMapping("/api/nav-links")
public class NavLinkController {

    private final NavLinkService service;

    public NavLinkController(NavLinkService service) {
        this.service = service;
    }

    @GetMapping
    public Result<List<NavLinkVO>> list(
            @RequestParam(defaultValue = "work") String module,
            @RequestParam(required = false) String keyword) {
        return Result.ok(service.list(module, keyword));
    }

    @PostMapping
    @SaCheckPermission(PermissionCodes.SHARED_NAV_MANAGE)
    public Result<NavLinkVO> create(@Valid @RequestBody NavLinkSaveDTO dto) {
        return Result.ok(service.create(dto));
    }

    @PutMapping("/{id}")
    @SaCheckPermission(PermissionCodes.SHARED_NAV_MANAGE)
    public Result<NavLinkVO> update(@PathVariable Long id, @Valid @RequestBody NavLinkSaveDTO dto) {
        return Result.ok(service.update(id, dto));
    }

    @PostMapping("/{id}/click")
    public Result<Void> click(@PathVariable Long id) {
        service.click(id);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    @SaCheckPermission(PermissionCodes.SHARED_NAV_MANAGE)
    public Result<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return Result.ok();
    }
}
