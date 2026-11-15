package cn.minims.desk.modules.integration.weather.vo;

/**
 * 天气展示对象。isMock 为 true 时前端显示「模拟」角标。
 *
 * <p>source 表示实际生效的数据源：{@code cma}（中国天气网）、
 * {@code open-meteo}（Open-Meteo）、{@code mock}（降级模拟数据）。
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
        String updatedAt,
        String source
) {
}
