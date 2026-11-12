package cn.minims.desk.modules.system.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.minims.desk.common.api.Result;
import cn.minims.desk.framework.satoken.PermissionCodes;
import cn.minims.desk.modules.system.service.SettingsService;
import cn.minims.desk.modules.system.vo.SettingsVO;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 设置与集成配置接口，见 docs/接口清单.md §12。
 */
@RestController
@RequestMapping("/api/settings")
public class SettingsController {

    private static final List<String> PROVIDERS = List.of("qweather", "minidocs");

    private final SettingsService settingsService;

    public SettingsController(SettingsService settingsService) {
        this.settingsService = settingsService;
    }

    @GetMapping
    public Result<SettingsVO> get() {
        return Result.ok(settingsService.get());
    }

    @PutMapping
    @SaCheckPermission(PermissionCodes.SYSTEM_SETTING_UPDATE)
    public Result<SettingsVO> update(@RequestBody Map<String, String> body) {
        return Result.ok(settingsService.update(body.get("city"), body.get("theme")));
    }

    @GetMapping("/integrations")
    public Result<List<SettingsVO.IntegrationVO>> integrations() {
        return Result.ok(PROVIDERS.stream().map(settingsService::integration).toList());
    }

    @PutMapping("/integrations/{provider}")
    @SaCheckPermission(PermissionCodes.SYSTEM_SETTING_UPDATE)
    public Result<SettingsVO.IntegrationVO> saveIntegration(
            @org.springframework.web.bind.annotation.PathVariable String provider,
            @RequestBody Map<String, Object> body) {
        String apiKey = body.get("apiKey") == null ? null : String.valueOf(body.get("apiKey"));
        String apiHost = body.get("apiHost") == null ? null : String.valueOf(body.get("apiHost"));
        Boolean enabled = body.get("enabled") == null ? null : Boolean.valueOf(String.valueOf(body.get("enabled")));
        return Result.ok(settingsService.saveIntegration(provider, apiKey, apiHost, enabled));
    }

    @PostMapping("/weather/test")
    public Result<SettingsVO.TestResult> testWeather(@RequestParam(required = false) String city) {
        return Result.ok(settingsService.testWeather(city));
    }
}
