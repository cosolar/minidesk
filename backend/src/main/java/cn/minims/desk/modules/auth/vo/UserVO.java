package cn.minims.desk.modules.auth.vo;

/**
 * 当前账号信息。
 */
public record UserVO(
        String id,
        String username,
        String nickname,
        String avatarUrl,
        String email,
        String city,
        boolean pwdResetRequired
) {
}
