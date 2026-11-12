package cn.minims.desk.modules.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

/**
 * 个人资料更新。
 */
public record ProfileUpdateDTO(
        @Size(max = 64, message = "昵称过长")
        String nickname,

        @Email(message = "邮箱格式不正确")
        @Size(max = 128, message = "邮箱过长")
        String email
) {
}
