package cn.minims.desk.modules.note.vo;

import cn.minims.desk.modules.note.entity.NoteDO;
import java.time.LocalDateTime;

/**
 * 侧栏文档树节点：只要标题与归属，不带正文，整库一次性取回也不重。
 */
public record NoteOutlineVO(
        String id,
        String title,
        String module,
        String folderId,
        Integer wordCount,
        boolean pinned,
        LocalDateTime updatedAt
) {
    public static NoteOutlineVO from(NoteDO note) {
        return new NoteOutlineVO(
                String.valueOf(note.getId()),
                note.getTitle(),
                note.getModule(),
                note.getFolderId() == null ? null : String.valueOf(note.getFolderId()),
                note.getWordCount(),
                note.getPinned() != null && note.getPinned() == 1,
                note.getUpdatedAt());
    }
}
