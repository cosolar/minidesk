package cn.minims.desk.modules.study.book.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 书单：想读 / 在读 / 读完。
 */
@Data
@TableName("book")
public class BookDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String title;

    private String author;

    private String category;

    private Integer totalPages;

    private Integer readPages;

    /** wish / reading / done */
    private String status;

    private LocalDate startDate;

    private LocalDate finishDate;

    private Integer rating;

    private Long coverFileId;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
