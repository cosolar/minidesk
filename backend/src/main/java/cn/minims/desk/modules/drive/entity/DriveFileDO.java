package cn.minims.desk.modules.drive.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 网盘文件 / 目录。parent_id = 0 表示根目录。
 * object_key 是存储层的相对路径，与展示名解耦，重命名不影响对象。
 */
@Data
@TableName("drive_file")
public class DriveFileDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long parentId;

    private String name;

    /** file / folder */
    private String type;

    private Long size;

    private String mime;

    /** local / s3 */
    private String storage;

    private String objectKey;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
