package cn.minims.desk.common.api;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 健康探测。Tauri 启动时轮询此接口确认 sidecar 就绪。
 */
@RestController
public class PingController {

    @GetMapping("/api/ping")
    public Result<Map<String, Object>> ping() {
        return Result.ok(Map.of(
                "app", "minidesk",
                "status", "up",
                "time", System.currentTimeMillis()));
    }
}
