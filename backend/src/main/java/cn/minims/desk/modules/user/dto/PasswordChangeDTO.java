package cn.minims.desk.modules.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 修改密码。必须提供原密码，防止 token 泄露后被直接改密。
 */
public record PasswordChangeDTO(
        @NotBlank(message = "请输入原密码")
        String oldPassword,

        @NotBlank(message = "请输入新密码")
        @Size(min = 8, max = 64, message = "密码长度需为 8-64 位")
        String newPassword,

        @NotBlank(message = "请再次输入新密码")
        String confirmPassword
) {
}
