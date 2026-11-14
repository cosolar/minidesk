package cn.minims.desk.modules.note.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

/**
 * 笔记分享保存入参。
 *
 * <p>password 为 null 表示保持原密码不变，空串表示清除密码，非空表示设置新密码；
 * clearPassword 为 true 时忽略 password，直接清除。
 */
public record NoteShareSaveDTO(
        @NotBlank(message = "分享链接不能为空")
        @Pattern(regexp = "^[A-Za-z0-9_-]{4,32}$",
                message = "链接只能包含字母、数字、中划线或下划线，长度 4-32 位")
        String token,

        @Size(max = 64, message = "密码过长")
        String password,

        boolean clearPassword,

        LocalDateTime expireAt,

        Boolean enabled
) {
}
