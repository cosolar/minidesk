package cn.minims.desk.modules.study.book.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 读书笔记。
 */
@Data
@TableName("book_note")
public class BookNoteDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long bookId;

    private String content;

    private Integer pageNo;

    private LocalDateTime createdAt;
}
