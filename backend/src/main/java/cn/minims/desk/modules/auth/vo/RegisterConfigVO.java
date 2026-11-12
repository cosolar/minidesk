package cn.minims.desk.modules.auth.vo;

/**
 * 注册页状态：前端据此渲染提示，不自行猜测。
 */
public record RegisterConfigVO(
        boolean allowRegister,
        boolean isFirstUser,
        boolean needClaimToken,
        boolean emailEnabled,
        PasswordPolicy passwordPolicy
) {
    public record PasswordPolicy(int minLength, boolean requireLetter, boolean requireDigit) {
    }
}
