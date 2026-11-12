package cn.minims.desk.modules.system.file.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 本地文件记录。磁盘路径 + 元信息，业务表只存 file_id。
 */
@Data
@TableName("sys_file")
public class SysFileDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    /** 相对 data-dir 的路径 */
    private String path;

    private String thumbPath;

    private Long size;

    private String mime;

    private String hash;

    /** avatar / album / doc / note */
    private String bizType;

    private Long bizId;

    private LocalDateTime createdAt;

    @TableLogic
    private Integer deleted;
}
