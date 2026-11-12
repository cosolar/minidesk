package cn.minims.desk.modules.life.album.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 相册。
 */
@Data
@TableName("album")
public class AlbumDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private String description;

    /** 封面图地址（图床外链或本地文件地址） */
    private String coverUrl;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
