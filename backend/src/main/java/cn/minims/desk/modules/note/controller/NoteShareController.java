package cn.minims.desk.modules.note.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.minims.desk.common.api.Result;
import cn.minims.desk.framework.satoken.PermissionCodes;
import cn.minims.desk.modules.note.dto.NoteShareSaveDTO;
import cn.minims.desk.modules.note.service.NoteShareService;
import cn.minims.desk.modules.note.vo.NoteShareVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 笔记分享设置，仅作者可读写；公开访问见 PublicNoteShareController。
 */
@RestController
@RequestMapping("/api/notes/{noteId}/share")
public class NoteShareController {

    private final NoteShareService noteShareService;

    public NoteShareController(NoteShareService noteShareService) {
        this.noteShareService = noteShareService;
    }

    @GetMapping
    public Result<NoteShareVO> get(@PathVariable Long noteId) {
        return Result.ok(noteShareService.get(noteId));
    }

    @PutMapping
    @SaCheckPermission(PermissionCodes.NOTE_MANAGE)
    public Result<NoteShareVO> save(@PathVariable Long noteId, @Valid @RequestBody NoteShareSaveDTO dto) {
        return Result.ok(noteShareService.save(noteId, dto));
    }

    @DeleteMapping
    @SaCheckPermission(PermissionCodes.NOTE_MANAGE)
    public Result<Void> cancel(@PathVariable Long noteId) {
        noteShareService.cancel(noteId);
        return Result.ok();
    }
}
