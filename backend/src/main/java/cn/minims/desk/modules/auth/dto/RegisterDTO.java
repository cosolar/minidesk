package cn.minims.desk.modules.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 注册入参。
 * 用户名限定字母数字下划线，避免出现空白与特殊字符导致的登录歧义。
 */
public record RegisterDTO(
        @NotBlank(message = "请输入用户名")
        @Pattern(regexp = "^[a-zA-Z0-9_]{3,32}$", message = "用户名需为 3-32 位字母、数字或下划线")
        String username,

        @Size(max = 64, message = "昵称过长")
        String nickname,

        @NotBlank(message = "请输入密码")
        @Size(min = 8, max = 64, message = "密码长度需为 8-64 位")
        String password,

        @NotBlank(message = "请再次输入密码")
        String confirmPassword,

        String claimToken
) {
}
