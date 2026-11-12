package cn.minims.desk.modules.work.docasset.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.minims.desk.common.api.Result;
import cn.minims.desk.framework.satoken.PermissionCodes;
import cn.minims.desk.modules.work.docasset.dto.DocAssetSaveDTO;
import cn.minims.desk.modules.work.docasset.service.DocAssetService;
import cn.minims.desk.modules.work.docasset.vo.DocAssetVO;
import jakarta.validation.Valid;
import java.util.List;
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
 * 文档资产接口。
 */
@RestController
@RequestMapping("/api/doc-assets")
public class DocAssetController {

    private final DocAssetService service;

    public DocAssetController(DocAssetService service) {
        this.service = service;
    }

    @GetMapping
    public Result<List<DocAssetVO>> list(
            @RequestParam(required = false) String docType,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String keyword) {
        return Result.ok(service.list(docType, category, keyword));
    }

    @GetMapping("/{id}")
    public Result<DocAssetVO> detail(@PathVariable Long id) {
        return Result.ok(service.detail(id));
    }

    @PostMapping
    @SaCheckPermission(PermissionCodes.WORK_DOC_MANAGE)
    public Result<DocAssetVO> create(@Valid @RequestBody DocAssetSaveDTO dto) {
        return Result.ok(service.create(dto));
    }

    @PutMapping("/{id}")
    @SaCheckPermission(PermissionCodes.WORK_DOC_MANAGE)
    public Result<DocAssetVO> update(@PathVariable Long id, @Valid @RequestBody DocAssetSaveDTO dto) {
        return Result.ok(service.update(id, dto));
    }

    @DeleteMapping("/{id}")
    @SaCheckPermission(PermissionCodes.WORK_DOC_MANAGE)
    public Result<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return Result.ok();
    }
}
