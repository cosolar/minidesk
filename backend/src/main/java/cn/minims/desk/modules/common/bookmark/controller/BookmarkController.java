package cn.minims.desk.modules.common.bookmark.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.minims.desk.common.api.Result;
import cn.minims.desk.framework.satoken.PermissionCodes;
import cn.minims.desk.modules.common.bookmark.dto.BookmarkCategorySaveDTO;
import cn.minims.desk.modules.common.bookmark.dto.BookmarkReorderDTO;
import cn.minims.desk.modules.common.bookmark.dto.BookmarkSiteSaveDTO;
import cn.minims.desk.modules.common.bookmark.service.BookmarkService;
import cn.minims.desk.modules.common.bookmark.vo.BookmarkOverviewVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 网站导航接口，见 docs/接口清单.md §19。
 */
@RestController
@RequestMapping("/api/bookmarks")
public class BookmarkController {

    private final BookmarkService service;

    public BookmarkController(BookmarkService service) {
        this.service = service;
    }

    @GetMapping("/overview")
    public Result<BookmarkOverviewVO> overview() {
        return Result.ok(service.overview());
    }

    @PostMapping("/categories")
    @SaCheckPermission(PermissionCodes.SHARED_BOOKMARK_MANAGE)
    public Result<BookmarkOverviewVO> createCategory(@Valid @RequestBody BookmarkCategorySaveDTO dto) {
        return Result.ok(service.saveCategory(null, dto));
    }

    @PutMapping("/categories/{id}")
    @SaCheckPermission(PermissionCodes.SHARED_BOOKMARK_MANAGE)
    public Result<BookmarkOverviewVO> updateCategory(@PathVariable Long id,
                                                     @Valid @RequestBody BookmarkCategorySaveDTO dto) {
        return Result.ok(service.saveCategory(id, dto));
    }

    @DeleteMapping("/categories/{id}")
    @SaCheckPermission(PermissionCodes.SHARED_BOOKMARK_MANAGE)
    public Result<BookmarkOverviewVO> deleteCategory(@PathVariable Long id) {
        return Result.ok(service.deleteCategory(id));
    }

    @PostMapping("/sites")
    @SaCheckPermission(PermissionCodes.SHARED_BOOKMARK_MANAGE)
    public Result<BookmarkOverviewVO> createSite(@Valid @RequestBody BookmarkSiteSaveDTO dto) {
        return Result.ok(service.saveSite(null, dto));
    }

    @PutMapping("/sites/{id}")
    @SaCheckPermission(PermissionCodes.SHARED_BOOKMARK_MANAGE)
    public Result<BookmarkOverviewVO> updateSite(@PathVariable Long id,
                                                 @Valid @RequestBody BookmarkSiteSaveDTO dto) {
        return Result.ok(service.saveSite(id, dto));
    }

    @DeleteMapping("/sites/{id}")
    @SaCheckPermission(PermissionCodes.SHARED_BOOKMARK_MANAGE)
    public Result<BookmarkOverviewVO> deleteSite(@PathVariable Long id) {
        return Result.ok(service.deleteSite(id));
    }

    @PostMapping("/sites/{id}/pin")
    @SaCheckPermission(PermissionCodes.SHARED_BOOKMARK_MANAGE)
    public Result<BookmarkOverviewVO> togglePin(@PathVariable Long id) {
        return Result.ok(service.togglePin(id));
    }

    @PostMapping("/sites/{id}/click")
    public Result<Void> click(@PathVariable Long id) {
        service.click(id);
        return Result.ok();
    }

    @PostMapping("/reorder")
    @SaCheckPermission(PermissionCodes.SHARED_BOOKMARK_MANAGE)
    public Result<BookmarkOverviewVO> reorder(@Valid @RequestBody BookmarkReorderDTO dto) {
        return Result.ok(service.reorder(dto));
    }

    @PostMapping("/seed")
    @SaCheckPermission(PermissionCodes.SHARED_BOOKMARK_MANAGE)
    public Result<BookmarkOverviewVO> seed() {
        return Result.ok(service.seed());
    }
}
