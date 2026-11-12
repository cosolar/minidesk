package cn.minims.desk.modules.console.vo;

import java.util.List;
import java.util.Map;

/**
 * 管理后台视图对象。只含计数与账号信息，不含任何用户正文。
 */
public record ConsoleVO(
        Dashboard dashboard,
        UserRow user
) {
    public record Dashboard(
            long totalUsers,
            long active7d,
            long disabledUsers,
            long tasks,
            long notes,
            long ledgers,
            long lifeLogs,
            double dbSizeMb,
            long uptimeHours,
            boolean partial
    ) {
    }

    public record UserRow(
            String id,
            String username,
            String nickname,
            int status,
            List<String> roles,
            String source,
            long taskCount,
            long noteCount,
            long ledgerCount,
            String lastLoginAt,
            String createdAt
    ) {
    }

    public record ConfigItem(String key, String value, String remark) {
    }

    public record LoginLog(
            String id,
            String username,
            boolean success,
            String failReason,
            String ip,
            String createdAt
    ) {
    }

    public record OperLog(
            String id,
            String username,
            String module,
            String action,
            String targetType,
            String targetId,
            String result,
            String createdAt
    ) {
    }

    public record ResetResult(String tempPassword) {
    }

    public record Stats(Map<String, Long> counts) {
    }
}
