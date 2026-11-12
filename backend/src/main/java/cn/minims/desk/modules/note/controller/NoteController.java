package cn.minims.desk.modules.note.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.minims.desk.common.api.PageResult;
import cn.minims.desk.common.api.Result;
import cn.minims.desk.framework.satoken.PermissionCodes;
import cn.minims.desk.modules.note.dto.NoteSaveDTO;
import cn.minims.desk.modules.note.service.NoteService;
import cn.minims.desk.modules.note.vo.NoteDetailVO;
import cn.minims.desk.modules.note.vo.NoteSummaryVO;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 笔记接口，见 docs/接口清单.md §7。
 */
@RestController
@RequestMapping("/api/notes")
public class NoteController {

    private final NoteService noteService;

    public NoteController(NoteService noteService) {
        this.noteService = noteService;
    }

    @GetMapping
    public Result<PageResult<NoteSummaryVO>> page(
            @RequestParam(required = false) String module,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "30") long size) {
        long safeSize = Math.min(Math.max(size, 1), 100);
        return Result.ok(noteService.page(module, type, keyword, Math.max(page, 1), safeSize));
    }

    @GetMapping("/{id}")
    public Result<NoteDetailVO> detail(@PathVariable Long id) {
        return Result.ok(noteService.detail(id));
    }

    @PostMapping
    @SaCheckPermission(PermissionCodes.NOTE_MANAGE)
    public Result<NoteDetailVO> create(@Valid @RequestBody NoteSaveDTO dto) {
        return Result.ok(noteService.create(dto));
    }

    @PutMapping("/{id}")
    @SaCheckPermission(PermissionCodes.NOTE_MANAGE)
    public Result<NoteDetailVO> update(@PathVariable Long id, @Valid @RequestBody NoteSaveDTO dto) {
        return Result.ok(noteService.update(id, dto));
    }

    @PatchMapping("/{id}/pin")
    @SaCheckPermission(PermissionCodes.NOTE_MANAGE)
    public Result<Void> pin(@PathVariable Long id, @RequestBody Map<String, Boolean> body) {
        noteService.togglePin(id, Boolean.TRUE.equals(body.get("pinned")));
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    @SaCheckPermission(PermissionCodes.NOTE_MANAGE)
    public Result<Void> delete(@PathVariable Long id) {
        noteService.delete(id);
        return Result.ok();
    }
}
