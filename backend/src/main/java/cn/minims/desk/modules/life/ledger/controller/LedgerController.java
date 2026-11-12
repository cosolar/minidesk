package cn.minims.desk.modules.life.ledger.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.minims.desk.common.api.PageResult;
import cn.minims.desk.common.api.Result;
import cn.minims.desk.framework.satoken.PermissionCodes;
import cn.minims.desk.modules.life.ledger.entity.LedgerCategoryDO;
import cn.minims.desk.modules.life.ledger.service.LedgerService;
import cn.minims.desk.modules.life.ledger.vo.LedgerVO;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 记账接口，见 docs/接口清单.md §9。
 */
@RestController
@RequestMapping("/api/ledger")
public class LedgerController {

    private final LedgerService ledgerService;

    public LedgerController(LedgerService ledgerService) {
        this.ledgerService = ledgerService;
    }

    @GetMapping
    public Result<PageResult<LedgerVO>> page(
            @RequestParam(required = false) String month,
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "30") long size) {
        long safeSize = Math.min(Math.max(size, 1), 100);
        return Result.ok(ledgerService.page(month, type, Math.max(page, 1), safeSize));
    }

    @GetMapping("/summary")
    public Result<LedgerVO.Summary> summary(@RequestParam(required = false) String month) {
        return Result.ok(ledgerService.summary(month));
    }

    @GetMapping("/categories")
    public Result<List<LedgerCategoryDO>> categories() {
        return Result.ok(ledgerService.categories());
    }

    @PostMapping
    @SaCheckPermission(PermissionCodes.LIFE_LEDGER_ADD)
    public Result<LedgerVO> create(@RequestBody Map<String, Object> body) {
        String type = body.get("type") == null ? "expense" : String.valueOf(body.get("type"));
        long amountCents = body.get("amountCents") == null
                ? 0L : Long.parseLong(String.valueOf(body.get("amountCents")));
        Long categoryId = body.get("categoryId") == null
                ? null : Long.valueOf(String.valueOf(body.get("categoryId")));
        String remark = body.get("remark") == null ? null : String.valueOf(body.get("remark"));
        LocalDate occurDate = body.get("occurDate") == null
                ? null : LocalDate.parse(String.valueOf(body.get("occurDate")));
        return Result.ok(ledgerService.create(type, amountCents, categoryId, remark, occurDate));
    }

    @DeleteMapping("/{id}")
    @SaCheckPermission(PermissionCodes.LIFE_LEDGER_ADD)
    public Result<Void> delete(@PathVariable Long id) {
        ledgerService.delete(id);
        return Result.ok();
    }
}
