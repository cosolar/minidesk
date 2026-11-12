package cn.minims.desk.modules.note.dto;

import jakarta.validation.constraints.Size;

/**
 * 笔记保存入参。标题可为空，由正文首行推导。
 */
public record NoteSaveDTO(
        @Size(max = 200, message = "标题过长") String title,

        String content,

        String module,

        String type,

        @Size(max = 255, message = "标签过长") String tags,

        String folder,

        Boolean pinned
) {
}
