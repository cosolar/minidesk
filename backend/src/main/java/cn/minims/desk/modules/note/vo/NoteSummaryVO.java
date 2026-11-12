package cn.minims.desk.modules.note.vo;

import cn.minims.desk.modules.note.entity.NoteDO;
import java.time.LocalDateTime;

/**
 * 笔记列表项：不含正文，避免列表接口返回大量 Markdown。
 */
public record NoteSummaryVO(
        String id,
        String title,
        String module,
        String type,
        String tags,
        Integer wordCount,
        boolean pinned,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static NoteSummaryVO from(NoteDO note) {
        return new NoteSummaryVO(
                String.valueOf(note.getId()),
                note.getTitle(),
                note.getModule(),
                note.getType(),
                note.getTags(),
                note.getWordCount(),
                note.getPinned() != null && note.getPinned() == 1,
                note.getCreatedAt(),
                note.getUpdatedAt());
    }
}
