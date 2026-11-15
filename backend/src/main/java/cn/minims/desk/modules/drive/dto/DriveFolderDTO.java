package cn.minims.desk.modules.drive.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 新建目录。 */
public record DriveFolderDTO(
        Long parentId,

        @NotBlank(message = "请输入文件夹名称")
        @Size(max = 120, message = "名称过长")
        String name
) {
}
