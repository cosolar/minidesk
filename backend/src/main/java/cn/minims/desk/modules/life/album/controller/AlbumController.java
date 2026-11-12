package cn.minims.desk.modules.life.album.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.minims.desk.common.api.Result;
import cn.minims.desk.framework.satoken.PermissionCodes;
import cn.minims.desk.modules.life.album.dto.AlbumSaveDTO;
import cn.minims.desk.modules.life.album.service.AlbumService;
import cn.minims.desk.modules.life.album.vo.AlbumVO;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 相册接口。
 */
@RestController
@RequestMapping("/api/albums")
public class AlbumController {

    private final AlbumService service;

    public AlbumController(AlbumService service) {
        this.service = service;
    }

    @GetMapping
    public Result<List<AlbumVO>> albums() {
        return Result.ok(service.albums());
    }

    @GetMapping("/{id}")
    public Result<AlbumVO> detail(@PathVariable Long id) {
        return Result.ok(service.detail(id));
    }

    @PostMapping
    @SaCheckPermission(PermissionCodes.LIFE_ALBUM_MANAGE)
    public Result<AlbumVO> create(@Valid @RequestBody AlbumSaveDTO dto) {
        return Result.ok(service.create(dto));
    }

    @PutMapping("/{id}")
    @SaCheckPermission(PermissionCodes.LIFE_ALBUM_MANAGE)
    public Result<AlbumVO> update(@PathVariable Long id, @Valid @RequestBody AlbumSaveDTO dto) {
        return Result.ok(service.update(id, dto));
    }

    @DeleteMapping("/{id}")
    @SaCheckPermission(PermissionCodes.LIFE_ALBUM_MANAGE)
    public Result<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return Result.ok();
    }

    @PostMapping("/{id}/photos")
    @SaCheckPermission(PermissionCodes.LIFE_ALBUM_MANAGE)
    public Result<AlbumVO.PhotoVO> addPhoto(@PathVariable Long id,
                                            @RequestBody AlbumSaveDTO.PhotoSaveDTO dto) {
        return Result.ok(service.addPhoto(id, dto));
    }

    @DeleteMapping("/photos/{photoId}")
    @SaCheckPermission(PermissionCodes.LIFE_ALBUM_MANAGE)
    public Result<Void> deletePhoto(@PathVariable Long photoId) {
        service.deletePhoto(photoId);
        return Result.ok();
    }
}
