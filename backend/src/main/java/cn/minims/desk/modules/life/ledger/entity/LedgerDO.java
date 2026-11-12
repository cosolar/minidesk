package cn.minims.desk.modules.life.ledger.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 记账流水。金额以「分」存储，避免各库浮点精度差异。
 */
@Data
@TableName("ledger")
public class LedgerDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** income / expense */
    private String type;

    private Long amountCents;

    private Long categoryId;

    private Long accountId;

    private String remark;

    private LocalDate occurDate;

    private String tags;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
