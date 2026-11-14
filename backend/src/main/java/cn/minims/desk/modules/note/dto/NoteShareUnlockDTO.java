package cn.minims.desk.modules.note.dto;

import jakarta.validation.constraints.Size;

/**
 * 公开页解锁入参：只需要访问密码。
 */
public record NoteShareUnlockDTO(
        @Size(max = 64, message = "密码过长") String password
) {
}
