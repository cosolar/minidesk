package cn.minims.desk.modules.note.vo;

import java.time.LocalDateTime;

/**
 * 公开分享页返回。status 表达结果，避免匿名请求触发错误码与全局提示：
 * ok / password_required / wrong_password / expired / not_found。
 * 未解锁时 title 与 content 一律为空，不泄露笔记信息。
 */
public record PublicNoteVO(
        String status,
        String title,
        String content,
        LocalDateTime updatedAt
) {
    public static PublicNoteVO status(String status) {
        return new PublicNoteVO(status, null, null, null);
    }

    public static PublicNoteVO ok(String title, String content, LocalDateTime updatedAt) {
        return new PublicNoteVO("ok", title, content, updatedAt);
    }
}
