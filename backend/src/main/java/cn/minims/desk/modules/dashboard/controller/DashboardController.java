package cn.minims.desk.modules.dashboard.controller;

import cn.minims.desk.common.api.Result;
import cn.minims.desk.modules.dashboard.service.DashboardService;
import cn.minims.desk.modules.dashboard.vo.DashboardVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 首页聚合接口，见 docs/接口清单.md §3。
 */
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/summary")
    public Result<DashboardVO> summary() {
        return Result.ok(dashboardService.summary());
    }
}
