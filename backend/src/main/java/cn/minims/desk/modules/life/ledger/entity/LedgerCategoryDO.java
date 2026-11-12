package cn.minims.desk.modules.life.ledger.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 收支分类，按用户隔离。
 */
@Data
@TableName("ledger_category")
public class LedgerCategoryDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String type;

    private String name;

    private String icon;

    private String color;

    private Integer sortOrder;

    private Integer isSystem;
}
