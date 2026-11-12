package cn.minims.desk.modules.work.review.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.minims.desk.common.api.Result;
import cn.minims.desk.modules.work.review.service.ReviewService;
import cn.minims.desk.modules.work.review.vo.ReviewVO;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 复盘接口，见 docs/接口清单.md §7。
 */
@RestController
@RequestMapping("/api/reviews")
public class ReviewController {

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @GetMapping
    public Result<List<ReviewVO>> list(@RequestParam(defaultValue = "daily") String type,
                                       @RequestParam(required = false) String module,
                                       @RequestParam(defaultValue = "30") int limit) {
        return Result.ok(reviewService.list(type, module, limit));
    }

    @GetMapping("/today")
    public Result<ReviewVO> today(@RequestParam(defaultValue = "daily") String type) {
        return Result.ok(reviewService.find(type, LocalDate.now()));
    }

    @PostMapping
    @SaCheckPermission("work:review:manage")
    public Result<ReviewVO> save(@RequestBody Map<String, Object> body) {
        String type = body.get("type") == null ? "daily" : String.valueOf(body.get("type"));
        LocalDate date = body.get("date") == null
                ? LocalDate.now() : LocalDate.parse(String.valueOf(body.get("date")));
        String module = body.get("module") == null ? null : String.valueOf(body.get("module"));
        String summary = body.get("summary") == null ? null : String.valueOf(body.get("summary"));
        String highlights = body.get("highlights") == null ? null : String.valueOf(body.get("highlights"));
        String issues = body.get("issues") == null ? null : String.valueOf(body.get("issues"));
        String nextPlan = body.get("nextPlan") == null ? null : String.valueOf(body.get("nextPlan"));
        return Result.ok(reviewService.save(type, date, module, summary, highlights, issues, nextPlan));
    }

    @PostMapping("/generate")
    @SaCheckPermission("work:review:manage")
    public Result<ReviewVO> generate(@RequestBody Map<String, Object> body) {
        String type = body.get("type") == null ? "daily" : String.valueOf(body.get("type"));
        LocalDate date = body.get("date") == null
                ? LocalDate.now() : LocalDate.parse(String.valueOf(body.get("date")));
        String module = body.get("module") == null ? null : String.valueOf(body.get("module"));
        return Result.ok(reviewService.generate(type, date, module));
    }
}
