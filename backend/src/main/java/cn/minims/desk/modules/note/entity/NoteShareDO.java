package cn.minims.desk.modules.note.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 笔记分享：一篇笔记对应一条可编辑的公开短链。
 */
@Data
@TableName("note_share")
public class NoteShareDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long noteId;

    /** 公开路径 /share/note/{token}，全局唯一 */
    private String token;

    /** BCrypt；为空表示无需密码 */
    private String passwordHash;

    /** 为空表示永久有效 */
    private LocalDateTime expireAt;

    private Integer enabled;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
