package cn.minims.desk.modules.integration.weather.vo;

/**
 * 天气展示对象。isMock 为 true 时前端显示「模拟」角标。
 */
public record WeatherVO(
        String city,
        int temp,
        String text,
        String icon,
        int humidity,
        String windDir,
        String windScale,
        boolean isMock,
        boolean stale,
        String updatedAt
) {
}
