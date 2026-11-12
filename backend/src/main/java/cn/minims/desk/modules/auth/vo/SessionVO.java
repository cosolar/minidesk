package cn.minims.desk.modules.auth.vo;

import java.util.List;

/**
 * 刷新页面后恢复会话用：不含新 token，只回填用户信息与权限。
 */
public record SessionVO(
        UserVO user,
        List<String> roles,
        List<String> permissions
) {
}
