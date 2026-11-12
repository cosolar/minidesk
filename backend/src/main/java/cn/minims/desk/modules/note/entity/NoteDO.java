package cn.minims.desk.modules.note.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 笔记：工作日志 / 灵感 / 备忘录 / 日记共用，用 module + type 区分。
 */
@Data
@TableName("note")
public class NoteDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** work / study / life */
    private String module;

    /** note / work_log / idea / memo / diary */
    private String type;

    private String title;

    /** Markdown 原文，由 Vditor 产出 */
    private String content;

    private String tags;

    private String folder;

    private Integer pinned;

    private Integer archived;

    private Integer wordCount;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
