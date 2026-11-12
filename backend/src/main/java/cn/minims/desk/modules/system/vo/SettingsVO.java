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

    public record TestResult(boolean ok, String message, Integer temp, String text) {
    }
}
