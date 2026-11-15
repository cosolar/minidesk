package cn.minims.desk.modules.common.bookmark.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 分类保存入参。 */
public record BookmarkCategorySaveDTO(
        @NotBlank(message = "请输入分类名称")
        @Size(max = 64, message = "分类名过长")
        String name,

        @Size(max = 64, message = "图标过长")
        String icon,

        @Size(max = 16, message = "颜色值过长")
        String color
) {
}
