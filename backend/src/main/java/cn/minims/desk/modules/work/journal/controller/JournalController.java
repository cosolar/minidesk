package cn.minims.desk.modules.work.journal.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.minims.desk.common.api.PageResult;
import cn.minims.desk.common.api.Result;
import cn.minims.desk.framework.satoken.PermissionCodes;
import cn.minims.desk.modules.work.journal.dto.JournalSaveDTO;
import cn.minims.desk.modules.work.journal.service.JournalService;
import cn.minims.desk.modules.work.journal.vo.JournalVO;
import jakarta.validation.Valid;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 工作日志接口。
 */
@RestController
@RequestMapping("/api/journals")
public class JournalController {

    private final JournalService journalService;

    public JournalController(JournalService journalService) {
        this.journalService = journalService;
    }

    @GetMapping
    public Result<PageResult<JournalVO>> page(
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        return Result.ok(journalService.page(type, Math.max(page, 1), Math.min(Math.max(size, 1), 100)));
    }

    /** 生成草稿：带真实统计数据，不落库 */
    @GetMapping("/draft")
    public Result<JournalVO> draft(
            @RequestParam(defaultValue = "daily") String type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return Result.ok(journalService.draft(type, date));
    }

    @GetMapping("/{id}")
    public Result<JournalVO> detail(@PathVariable Long id) {
        return Result.ok(journalService.detail(id));
    }

    @PostMapping
    @SaCheckPermission(PermissionCodes.WORK_JOURNAL_MANAGE)
    public Result<JournalVO> create(@Valid @RequestBody JournalSaveDTO dto) {
        return Result.ok(journalService.create(dto));
    }

    @PutMapping("/{id}")
    @SaCheckPermission(PermissionCodes.WORK_JOURNAL_MANAGE)
    public Result<JournalVO> update(@PathVariable Long id, @Valid @RequestBody JournalSaveDTO dto) {
        return Result.ok(journalService.update(id, dto));
    }

    @DeleteMapping("/{id}")
    @SaCheckPermission(PermissionCodes.WORK_JOURNAL_MANAGE)
    public Result<Void> delete(@PathVariable Long id) {
        journalService.delete(id);
        return Result.ok();
    }
}
