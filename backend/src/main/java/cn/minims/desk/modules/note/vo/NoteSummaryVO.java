package cn.minims.desk.modules.note.vo;

import cn.minims.desk.modules.note.entity.NoteDO;
import java.time.LocalDateTime;
import org.springframework.util.StringUtils;

/**
 * 笔记列表项：只返回裁剪后的纯文本摘要，不返回 Markdown 原文。
 */
public record NoteSummaryVO(
        String id,
        String title,
        String excerpt,
        String module,
        String type,
        String tags,
        String folderId,
        Integer wordCount,
        boolean pinned,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    private static final int EXCERPT_LIMIT = 84;

    public static NoteSummaryVO from(NoteDO note) {
        return new NoteSummaryVO(
                String.valueOf(note.getId()),
                note.getTitle(),
                excerptOf(note.getContent(), note.getTitle()),
                note.getModule(),
                note.getType(),
                note.getTags(),
                note.getFolderId() == null ? null : String.valueOf(note.getFolderId()),
                note.getWordCount(),
                note.getPinned() != null && note.getPinned() == 1,
                note.getCreatedAt(),
                note.getUpdatedAt());
    }

    /** 列表卡片用：剥掉 Markdown 标记、标题行与代码块，压成一行短摘要 */
    public static String excerptOf(String content, String title) {
        if (!StringUtils.hasText(content)) {
            return "";
        }
        String text = content
                .replaceAll("```[\\s\\S]*?```", " ")
                .replaceAll("!\\[[^\\]]*\\]\\([^)]*\\)", " ")
                .replaceAll("\\[([^\\]]*)\\]\\([^)]*\\)", "$1")
                .replaceAll("(?m)^\\s{0,3}#{1,6}\\s+.*$", " ")
                .replaceAll("(?m)^\\s{0,3}(>|[-*+]\\s|\\d+\\.\\s)", " ")
                .replaceAll("[`*_~|]", " ")
                .replaceAll("\\s+", " ")
                .trim();
        if (StringUtils.hasText(title) && text.startsWith(title)) {
            text = text.substring(title.length()).trim();
        }
        if (text.isEmpty()) {
            return "";
        }
        return text.length() > EXCERPT_LIMIT ? text.substring(0, EXCERPT_LIMIT) + "…" : text;
    }
}
