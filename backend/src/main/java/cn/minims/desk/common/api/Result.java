package cn.minims.desk.common.api;

import cn.minims.desk.common.exception.BizException;
import org.slf4j.MDC;

/**
 * 统一响应体。字段与 docs/接口清单.md §1.2 一致。
 */
public record Result<T>(int code, String message, T data, String traceId, long timestamp) {

    public static <T> Result<T> ok(T data) {
        return new Result<>(0, "ok", data, currentTraceId(), System.currentTimeMillis());
    }

    public static Result<Void> ok() {
        return ok(null);
    }

    public static <T> Result<T> error(ErrorCode errorCode) {
        return new Result<>(errorCode.code(), errorCode.message(), null, currentTraceId(), System.currentTimeMillis());
    }

    public static <T> Result<T> error(ErrorCode errorCode, String message) {
        return new Result<>(errorCode.code(), message, null, currentTraceId(), System.currentTimeMillis());
    }

    public static <T> Result<T> error(BizException ex) {
        return new Result<>(ex.getCode(), ex.getMessage(), null, currentTraceId(), System.currentTimeMillis());
    }

    private static String currentTraceId() {
        String id = MDC.get("traceId");
        return id == null ? "" : id;
    }
}
