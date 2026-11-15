package cn.minims.desk.modules.common.bookmark.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 站点保存入参。 */
public record BookmarkSiteSaveDTO(
        Long categoryId,

        @NotBlank(message = "请输入站点名称")
        @Size(max = 120, message = "站点名过长")
        String name,

        @NotBlank(message = "请输入站点地址")
        @Size(max = 1000, message = "地址过长")
        String url,

        @Size(max = 255, message = "描述过长")
        String description,

        @Size(max = 500, message = "图标地址过长")
        String icon,

        @Size(max = 16, message = "颜色值过长")
        String color,

        Boolean pinned
) {
}
