package cn.minims.desk.modules.common.bookmark.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/** 拖拽排序：type = category / site，ids 为目标顺序。 */
public record BookmarkReorderDTO(
        String type,
        @NotEmpty(message = "排序列表不能为空") List<Long> ids
) {
}
