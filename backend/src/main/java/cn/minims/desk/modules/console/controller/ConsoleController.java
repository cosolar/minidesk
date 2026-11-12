package cn.minims.desk.modules.console.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.minims.desk.common.api.PageResult;
import cn.minims.desk.common.api.Result;
import cn.minims.desk.modules.console.service.ConsoleService;
import cn.minims.desk.modules.console.vo.ConsoleVO;
import java.util.List;
import java.util.Map;
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
 * 管理后台接口，见 docs/接口清单.md §14。
 * 整组 ADMIN 角色；只做账号管理与计数，不返回任何用户业务正文。
 */
@RestController
@RequestMapping("/api/console")
@SaCheckRole("ADMIN")
public class ConsoleController {

    private final ConsoleService consoleService;

    public ConsoleController(ConsoleService consoleService) {
        this.consoleService = consoleService;
    }

    @GetMapping("/dashboard")
    public Result<ConsoleVO.Dashboard> dashboard() {
        return Result.ok(consoleService.dashboard());
    }

    @GetMapping("/users")
    public Result<PageResult<ConsoleVO.UserRow>> users(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        long safeSize = Math.min(Math.max(size, 1), 100);
        return Result.ok(consoleService.users(keyword, Math.max(page, 1), safeSize));
    }

    @PostMapping("/users")
    public Result<Void> createUser(@RequestBody Map<String, Object> body) {
        consoleService.createUser(
                body.get("username") == null ? null : String.valueOf(body.get("username")),
                body.get("nickname") == null ? null : String.valueOf(body.get("nickname")),
                body.get("password") == null ? null : String.valueOf(body.get("password")),
                body.get("roleCode") == null ? null : String.valueOf(body.get("roleCode")));
        return Result.ok();
    }

    @PatchMapping("/users/{id}/status")
    public Result<Void> updateStatus(@PathVariable Long id, @RequestBody Map<String, Boolean> body) {
        consoleService.updateStatus(id, Boolean.TRUE.equals(body.get("enabled")));
        return Result.ok();
    }

    @PostMapping("/users/{id}/reset-password")
    public Result<ConsoleVO.ResetResult> resetPassword(@PathVariable Long id) {
        return Result.ok(consoleService.resetPassword(id));
    }

    @DeleteMapping("/users/{id}")
    public Result<Void> deleteUser(@PathVariable Long id) {
        consoleService.deleteUser(id);
        return Result.ok();
    }

    @GetMapping("/roles")
    public Result<List<Map<String, Object>>> roles() {
        return Result.ok(consoleService.roles());
    }

    @GetMapping("/config")
    public Result<List<ConsoleVO.ConfigItem>> config() {
        return Result.ok(consoleService.config());
    }

    @PutMapping("/config")
    public Result<List<ConsoleVO.ConfigItem>> updateConfig(@RequestBody Map<String, String> body) {
        return Result.ok(consoleService.updateConfig(body));
    }

    @GetMapping("/logs/login")
    public Result<PageResult<ConsoleVO.LoginLog>> loginLogs(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        return Result.ok(consoleService.loginLogs(Math.max(page, 1), Math.min(size, 100)));
    }

    @GetMapping("/logs/oper")
    public Result<PageResult<ConsoleVO.OperLog>> operLogs(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        return Result.ok(consoleService.operLogs(Math.max(page, 1), Math.min(size, 100)));
    }
}
