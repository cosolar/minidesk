package cn.minims.desk.modules.life.shopping.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.minims.desk.common.api.Result;
import cn.minims.desk.framework.satoken.PermissionCodes;
import cn.minims.desk.modules.life.shopping.service.ShoppingService;
import cn.minims.desk.modules.life.shopping.vo.ShoppingListVO;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 购物清单接口，见 docs/接口清单.md §9。
 */
@RestController
@RequestMapping("/api/shopping")
public class ShoppingController {

    private final ShoppingService shoppingService;

    public ShoppingController(ShoppingService shoppingService) {
        this.shoppingService = shoppingService;
    }

    @GetMapping("/lists")
    public Result<List<ShoppingListVO>> lists() {
        return Result.ok(shoppingService.lists());
    }

    @PostMapping("/lists")
    @SaCheckPermission(PermissionCodes.SHOPPING_MANAGE)
    public Result<ShoppingListVO> createList(@RequestBody Map<String, Object> body) {
        String name = body.get("name") == null ? null : String.valueOf(body.get("name"));
        String icon = body.get("icon") == null ? null : String.valueOf(body.get("icon"));
        Long budget = body.get("budgetCents") == null
                ? null : Long.valueOf(String.valueOf(body.get("budgetCents")));
        return Result.ok(shoppingService.createList(name, icon, budget));
    }

    @DeleteMapping("/lists/{id}")
    @SaCheckPermission(PermissionCodes.SHOPPING_MANAGE)
    public Result<Void> deleteList(@PathVariable Long id) {
        shoppingService.deleteList(id);
        return Result.ok();
    }

    @PostMapping("/lists/{id}/items")
    @SaCheckPermission(PermissionCodes.SHOPPING_MANAGE)
    public Result<Void> addItem(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        String name = body.get("name") == null ? null : String.valueOf(body.get("name"));
        BigDecimal quantity = body.get("quantity") == null
                ? null : new BigDecimal(String.valueOf(body.get("quantity")));
        String unit = body.get("unit") == null ? null : String.valueOf(body.get("unit"));
        Long priceCents = body.get("priceCents") == null
                ? null : Long.valueOf(String.valueOf(body.get("priceCents")));
        String category = body.get("category") == null ? null : String.valueOf(body.get("category"));
        shoppingService.addItem(id, name, quantity, unit, priceCents, category);
        return Result.ok();
    }

    @PatchMapping("/items/{id}/bought")
    @SaCheckPermission(PermissionCodes.SHOPPING_MANAGE)
    public Result<Void> toggleItem(@PathVariable Long id, @RequestBody Map<String, Boolean> body) {
        shoppingService.toggleItem(id, Boolean.TRUE.equals(body.get("bought")));
        return Result.ok();
    }

    @DeleteMapping("/items/{id}")
    @SaCheckPermission(PermissionCodes.SHOPPING_MANAGE)
    public Result<Void> deleteItem(@PathVariable Long id) {
        shoppingService.deleteItem(id);
        return Result.ok();
    }
}
