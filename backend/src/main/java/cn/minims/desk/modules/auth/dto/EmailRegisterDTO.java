package cn.minims.desk.modules.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 邮箱注册入参。用户名由邮箱自动生成，用户只需要记住邮箱与密码。
 */
public record EmailRegisterDTO(
        @NotBlank(message = "请输入邮箱")
        @Email(message = "邮箱格式不正确")
        @Size(max = 128, message = "邮箱过长")
        String email,

        @NotBlank(message = "请输入验证码")
        @Size(min = 4, max = 8, message = "验证码不正确")
        String code,

        @Size(max = 64, message = "昵称过长")
        String nickname,

        @NotBlank(message = "请输入密码")
        @Size(min = 8, max = 64, message = "密码长度需为 8-64 位")
        String password,

        @NotBlank(message = "请再次输入密码")
        String confirmPassword
) {
}
