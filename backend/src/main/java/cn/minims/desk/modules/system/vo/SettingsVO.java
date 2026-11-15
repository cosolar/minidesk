package cn.minims.desk.modules.system.vo;

/**
 * 个人设置。
 */
public record SettingsVO(
        String city,
        String theme,
        Integer weekStart,
        boolean sidebarCollapsed
) {
    /** 第三方集成状态：只回显掩码，永远不返回明文 */
    public record IntegrationVO(
            String provider,
            String apiHost,
            boolean enabled,
            boolean hasKey,
            String maskedKey
    ) {
    }

    /** 天气连通性测试：source 为实际生效的数据源（cma / open-meteo / mock），city 为实际查询的城市。 */
    public record TestResult(boolean ok, String message, String city, Integer temp, String text, String source) {
    }
}
