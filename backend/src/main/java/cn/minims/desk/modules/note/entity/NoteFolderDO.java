package cn.minims.desk.modules.note.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 笔记目录：工作 / 学习 / 生活为固定根（不入库），这里的行都是用户自建目录。
 */
@Data
@TableName("note_folder")
public class NoteFolderDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** work / study / life：所属固定根 */
    private String module;

    /** 父目录；null 表示直接挂在板块根目录下 */
    private Long parentId;

    private String name;

    private Integer sortNo;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
