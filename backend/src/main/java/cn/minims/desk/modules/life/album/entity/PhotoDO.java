package cn.minims.desk.modules.life.album.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 照片：{@code url} 可以是图床外链，也可以是本地上传后返回的地址。
 */
@Data
@TableName("photo")
public class PhotoDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long albumId;

    private String title;

    private String url;

    private Long fileId;

    private LocalDate takenAt;

    private Integer sortOrder;

    private LocalDateTime createdAt;

    @TableLogic
    private Integer deleted;
}
