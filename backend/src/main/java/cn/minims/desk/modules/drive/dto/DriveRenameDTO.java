package cn.minims.desk.modules.drive.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 重命名。 */
public record DriveRenameDTO(
        @NotBlank(message = "请输入名称")
        @Size(max = 255, message = "名称过长")
        String name
) {
}
