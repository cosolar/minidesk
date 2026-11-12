package cn.minims.desk.common.api;

/**
 * 统一错误码。码段约定见 docs/接口清单.md §1.2。
 */
public enum ErrorCode {

    OK(0, "ok"),

    PARAM_ERROR(40001, "参数校验失败"),
    UNAUTHORIZED(40101, "登录已过期，请重新登录"),
    TOKEN_INVALID(40102, "登录凭证无效"),
    FORBIDDEN(40301, "没有操作权限"),
    ACCOUNT_DISABLED(40302, "账号已被禁用"),
    LOGIN_LOCKED(40303, "登录失败次数过多，请稍后再试"),
    NOT_FOUND(40401, "资源不存在"),
    RATE_LIMIT(42901, "操作太频繁，请稍后再试"),
    SERVER_ERROR(50000, "服务异常，请稍后重试"),

    BAD_CREDENTIALS(60010, "用户名或密码错误"),
    USERNAME_EXISTS(60011, "用户名已被占用"),
    EMAIL_EXISTS(60013, "该邮箱已被注册"),
    ACCOUNT_NOT_FOUND(60012, "账号不存在"),
    REGISTER_CLOSED(60020, "暂未开放注册"),
    ADMIN_ALREADY_CLAIMED(60021, "管理员账号已被注册"),
    CLAIM_TOKEN_INVALID(60022, "初始化口令不正确"),
    LAST_ADMIN_PROTECTED(60030, "系统至少需要保留一个管理员"),
    SELF_OPERATION_DENIED(60031, "不能对自己的账号执行该操作"),
    WRONG_PASSWORD(60032, "原密码不正确"),

    MAIL_NOT_CONFIGURED(60040, "管理员尚未配置发件邮箱"),
    EMAIL_CODE_INVALID(60041, "验证码无效或已过期"),
    EMAIL_CODE_LIMIT(60042, "验证码发送过于频繁，请稍后再试"),
    MAIL_SEND_FAILED(60043, "邮件发送失败，请检查发件配置");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int code() {
        return code;
    }

    public String message() {
        return message;
    }
}
