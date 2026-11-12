package cn.minims.desk.modules.stats.controller;

import cn.minims.desk.common.api.Result;
import cn.minims.desk.modules.stats.service.StatsService;
import cn.minims.desk.modules.stats.vo.StatsVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 数据统计：工作效率、学习成长、生活收支。
 */
@RestController
@RequestMapping("/api/stats")
public class StatsController {

    private final StatsService statsService;

    public StatsController(StatsService statsService) {
        this.statsService = statsService;
    }

    @GetMapping("/summary")
    public Result<StatsVO> summary() {
        return Result.ok(statsService.summary());
    }
}
