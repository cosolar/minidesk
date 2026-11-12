package cn.minims.desk.common.exception;

import cn.minims.desk.common.api.ErrorCode;

/**
 * 业务异常：可预期、需要给前端明确码与文案的错误。
 */
public class BizException extends RuntimeException {

    private final int code;

    public BizException(ErrorCode errorCode) {
        super(errorCode.message());
        this.code = errorCode.code();
    }

    public BizException(ErrorCode errorCode, String message) {
        super(message);
        this.code = errorCode.code();
    }

    public BizException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.code = errorCode.code();
    }

    public int getCode() {
        return code;
    }
}
