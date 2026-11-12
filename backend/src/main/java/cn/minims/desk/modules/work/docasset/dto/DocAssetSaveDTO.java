package cn.minims.desk.modules.work.docasset.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 文档资产保存入参。
 */
public record DocAssetSaveDTO(
        @NotBlank(message = "请输入标题")
        @Size(max = 200, message = "标题过长")
        String title,

        String docType,

        String provider,

        @Size(max = 1000, message = "链接过长")
        String url,

        String content,

        Long fileId,

        String category,

        String tags,

        String description,

        Boolean pinned
) {
}
