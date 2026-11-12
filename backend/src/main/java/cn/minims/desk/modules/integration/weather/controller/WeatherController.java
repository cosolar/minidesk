package cn.minims.desk.modules.integration.weather.controller;

import cn.minims.desk.common.api.Result;
import cn.minims.desk.modules.integration.weather.service.WeatherService;
import cn.minims.desk.modules.integration.weather.vo.WeatherVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 天气接口，见 docs/接口清单.md §11。
 */
@RestController
@RequestMapping("/api/weather")
public class WeatherController {

    private final WeatherService weatherService;

    public WeatherController(WeatherService weatherService) {
        this.weatherService = weatherService;
    }

    @GetMapping("/now")
    public Result<WeatherVO> now(@RequestParam(required = false) String city) {
        return Result.ok(weatherService.now(city));
    }
}
