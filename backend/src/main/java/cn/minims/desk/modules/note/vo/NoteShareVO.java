package cn.minims.desk.modules.note.vo;

import cn.minims.desk.modules.note.entity.NoteShareDO;
import java.time.LocalDateTime;

/**
 * 笔记当前分享配置。hasPassword 只暴露是否设密，密码哈希不出后端。
 */
public record NoteShareVO(
        String noteId,
        String token,
        boolean hasPassword,
        boolean enabled,
        LocalDateTime expireAt,
        LocalDateTime updatedAt
) {
    public static NoteShareVO from(NoteShareDO share) {
        return new NoteShareVO(
                String.valueOf(share.getNoteId()),
                share.getToken(),
                share.getPasswordHash() != null && !share.getPasswordHash().isBlank(),
                share.getEnabled() != null && share.getEnabled() == 1,
                share.getExpireAt(),
                share.getUpdatedAt());
    }
}
