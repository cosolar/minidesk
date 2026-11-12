package cn.minims.desk.modules.life.contact.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * 联系人保存入参。
 */
public record ContactSaveDTO(
        @NotBlank(message = "请输入姓名")
        @Size(max = 64, message = "姓名过长")
        String name,

        String type,

        String company,

        String position,

        String phone,

        String email,

        String wechat,

        LocalDate birthday,

        String note
) {
}
