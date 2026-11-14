package cn.minims.desk.modules.note.controller;

import cn.minims.desk.common.api.Result;
import cn.minims.desk.modules.note.dto.NoteShareUnlockDTO;
import cn.minims.desk.modules.note.service.NoteShareService;
import cn.minims.desk.modules.note.vo.PublicNoteVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 公开分享页接口，登录白名单内。按 token 解析，不依赖登录态与租户上下文。
 */
@RestController
@RequestMapping("/api/share/note")
public class PublicNoteShareController {

    private final NoteShareService noteShareService;

    public PublicNoteShareController(NoteShareService noteShareService) {
        this.noteShareService = noteShareService;
    }

    @GetMapping("/{token}")
    public Result<PublicNoteVO> view(@PathVariable String token) {
        return Result.ok(noteShareService.publicView(token, null));
    }

    @PostMapping("/{token}/unlock")
    public Result<PublicNoteVO> unlock(@PathVariable String token,
                                       @Valid @RequestBody NoteShareUnlockDTO dto) {
        return Result.ok(noteShareService.publicView(token, dto.password()));
    }
}
