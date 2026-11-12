package cn.minims.desk.modules.common.navlink.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 导航链接保存入参。
 */
public record NavLinkSaveDTO(
        String module,

        @Size(max = 64, message = "分组名过长")
        String groupName,

        @NotBlank(message = "请输入名称")
        @Size(max = 120, message = "名称过长")
        String title,

        @NotBlank(message = "请输入地址")
        @Size(max = 1000, message = "地址过长")
        String url,

        String icon,

        @Size(max = 255, message = "描述过长")
        String description,

        Integer sortOrder
) {
}
