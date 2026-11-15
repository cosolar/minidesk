package cn.minims.desk.modules.note.vo;

import cn.minims.desk.modules.note.entity.NoteDO;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * 回收站面板：保留天数 + 条目列表。前端用 keepDays 提示「到期自动清理」。
 */
public record NoteRecycleBoard(int keepDays, List<Item> items) {

    /** 单篇回收站笔记：除列表摘要外带上删除时间与剩余天数 */
    public record Item(
            String id,
            String title,
            String excerpt,
            String module,
            Integer wordCount,
            LocalDateTime deletedAt,
            long remainingDays
    ) {
        public static Item from(NoteDO note, int keepDays) {
            LocalDateTime deletedAt = note.getDeletedAt();
            long used = deletedAt == null ? 0 : ChronoUnit.DAYS.between(deletedAt, LocalDateTime.now());
            long remaining = Math.max(keepDays - used, 0);
            return new Item(
                    String.valueOf(note.getId()),
                    note.getTitle(),
                    NoteSummaryVO.excerptOf(note.getContent(), note.getTitle()),
                    note.getModule(),
                    note.getWordCount(),
                    deletedAt,
                    remaining);
        }
    }

    public static NoteRecycleBoard of(int keepDays, List<NoteDO> notes) {
        return new NoteRecycleBoard(keepDays, notes.stream().map(note -> Item.from(note, keepDays)).toList());
    }
}
