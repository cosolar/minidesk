package cn.minims.desk.modules.work.project.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * 项目创建 / 编辑入参。
 */
public record ProjectSaveDTO(
        @NotBlank(message = "请输入项目名称")
        @Size(max = 120, message = "名称过长")
        String name,

        String description,

        String color,

        String status,

        Integer priority,

        LocalDate startDate,

        LocalDate endDate,

        Long groupId
) {
}
