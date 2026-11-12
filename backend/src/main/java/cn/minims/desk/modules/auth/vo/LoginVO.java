package cn.minims.desk.modules.auth.vo;

import java.util.List;

/**
 * 登录 / 注册成功后返回，结构与接口清单 §2 一致。
 */
public record LoginVO(
        String tokenName,
        String tokenValue,
        long expiresIn,
        UserVO user,
        List<String> roles,
        List<String> permissions,
        boolean isFirstUser
) {
}
