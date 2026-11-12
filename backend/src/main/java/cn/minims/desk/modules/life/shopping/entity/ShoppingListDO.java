package cn.minims.desk.modules.life.shopping.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 购物清单（一个清单下多条商品）。
 */
@Data
@TableName("shopping_list")
public class ShoppingListDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private String icon;

    private Long budgetCents;

    private String status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
