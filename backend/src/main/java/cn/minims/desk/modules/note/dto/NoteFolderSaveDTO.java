package cn.minims.desk.modules.note.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 目录新建 / 重命名入参。挂在已有目录下时模块继承父目录，module 可不传。
 */
public record NoteFolderSaveDTO(
        @NotBlank(message = "目录名不能为空")
        @Size(max = 100, message = "目录名过长")
        String name,

        String module,

        Long parentId
) {
}
