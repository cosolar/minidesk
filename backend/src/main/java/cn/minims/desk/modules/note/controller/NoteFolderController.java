package cn.minims.desk.modules.note.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.minims.desk.common.api.Result;
import cn.minims.desk.framework.satoken.PermissionCodes;
import cn.minims.desk.modules.note.dto.NoteFolderSaveDTO;
import cn.minims.desk.modules.note.service.NoteFolderService;
import cn.minims.desk.modules.note.vo.NoteFolderVO;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 笔记目录接口。工作 / 学习 / 生活是前端固定根，这里只管理用户自建目录。
 */
@RestController
@RequestMapping("/api/note-folders")
public class NoteFolderController {

    private final NoteFolderService folderService;

    public NoteFolderController(NoteFolderService folderService) {
        this.folderService = folderService;
    }

    @GetMapping
    public Result<List<NoteFolderVO>> list() {
        return Result.ok(folderService.list());
    }

    @PostMapping
    @SaCheckPermission(PermissionCodes.NOTE_MANAGE)
    public Result<NoteFolderVO> create(@Valid @RequestBody NoteFolderSaveDTO dto) {
        return Result.ok(folderService.create(dto));
    }

    @PutMapping("/{id}")
    @SaCheckPermission(PermissionCodes.NOTE_MANAGE)
    public Result<NoteFolderVO> rename(@PathVariable Long id, @Valid @RequestBody NoteFolderSaveDTO dto) {
        return Result.ok(folderService.rename(id, dto));
    }

    @PutMapping("/{id}/move")
    @SaCheckPermission(PermissionCodes.NOTE_MANAGE)
    public Result<NoteFolderVO> move(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Object raw = body.get("parentId");
        Long parentId = raw == null || String.valueOf(raw).isBlank() ? null : Long.valueOf(String.valueOf(raw));
        return Result.ok(folderService.move(id, parentId));
    }

    /** 删除目录：里面的文档移入回收站 */
    @DeleteMapping("/{id}")
    @SaCheckPermission(PermissionCodes.NOTE_MANAGE)
    public Result<Map<String, Integer>> delete(@PathVariable Long id) {
        return Result.ok(Map.of("moved", folderService.delete(id)));
    }
}
