package cn.minims.desk.modules.life.shopping.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 清单条目。价格按「分」存。
 */
@Data
@TableName("shopping_item")
public class ShoppingItemDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long listId;

    private String name;

    private BigDecimal quantity;

    private String unit;

    private Long priceCents;

    private String category;

    private Integer bought;

    private LocalDateTime boughtAt;

    private Integer sortOrder;

    private LocalDateTime createdAt;
}
