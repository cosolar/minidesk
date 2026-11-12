package cn.minims.desk.framework.tenant;

import cn.dev33.satoken.stp.StpUtil;
import cn.minims.desk.common.api.ErrorCode;
import cn.minims.desk.common.exception.BizException;
import java.util.function.Supplier;

/**
 * 租户上下文。
 * 登录 / 注册 / 定时任务等无登录态场景，必须用 ignore 包裹，否则租户插件拿不到 userId。
 */
public final class TenantContext {

    private static final ThreadLocal<Boolean> IGNORED = ThreadLocal.withInitial(() -> Boolean.FALSE);

    private TenantContext() {
    }

    public static void ignore(Runnable action) {
        boolean previous = IGNORED.get();
        IGNORED.set(Boolean.TRUE);
        try {
            action.run();
        } finally {
            IGNORED.set(previous);
            if (!previous) {
                IGNORED.remove();
            }
        }
    }

    public static <T> T ignore(Supplier<T> supplier) {
        boolean previous = IGNORED.get();
        IGNORED.set(Boolean.TRUE);
        try {
            return supplier.get();
        } finally {
            IGNORED.set(previous);
            if (!previous) {
                IGNORED.remove();
            }
        }
    }

    public static boolean isIgnored() {
        return Boolean.TRUE.equals(IGNORED.get());
    }

    /** 当前登录用户 id；未登录直接抛 401，由全局异常处理转换 */
    public static long requireUserId() {
        Object loginId = StpUtil.getLoginIdDefaultNull();
        if (loginId == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        return Long.parseLong(loginId.toString());
    }

    public static Long currentUserIdOrNull() {
        Object loginId = StpUtil.getLoginIdDefaultNull();
        return loginId == null ? null : Long.parseLong(loginId.toString());
    }
}
