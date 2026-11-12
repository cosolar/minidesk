package cn.minims.desk.modules.note.vo;

import cn.minims.desk.modules.note.entity.NoteDO;
import java.time.LocalDateTime;

/**
 * 笔记详情：含 Markdown 正文。
 */
public record NoteDetailVO(
        String id,
        String title,
        String module,
        String type,
        String content,
        String tags,
        String folder,
        boolean pinned,
        Integer wordCount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static NoteDetailVO from(NoteDO note) {
        return new NoteDetailVO(
                String.valueOf(note.getId()),
                note.getTitle(),
                note.getModule(),
                note.getType(),
                note.getContent(),
                note.getTags(),
                note.getFolder(),
                note.getPinned() != null && note.getPinned() == 1,
                note.getWordCount(),
                note.getCreatedAt(),
                note.getUpdatedAt());
    }
}
