package cn.minims.desk.modules.life.contact.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.minims.desk.common.api.Result;
import cn.minims.desk.framework.satoken.PermissionCodes;
import cn.minims.desk.modules.life.contact.dto.ContactSaveDTO;
import cn.minims.desk.modules.life.contact.service.ContactService;
import cn.minims.desk.modules.life.contact.vo.ContactVO;
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
 * 联系人接口。
 */
@RestController
@RequestMapping("/api/contacts")
public class ContactController {

    private final ContactService service;

    public ContactController(ContactService service) {
        this.service = service;
    }

    @GetMapping
    public Result<List<ContactVO>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String type) {
        return Result.ok(service.list(keyword, type));
    }

    @PostMapping
    @SaCheckPermission(PermissionCodes.LIFE_CONTACT_MANAGE)
    public Result<ContactVO> create(@Valid @RequestBody ContactSaveDTO dto) {
        return Result.ok(service.create(dto));
    }

    @PutMapping("/{id}")
    @SaCheckPermission(PermissionCodes.LIFE_CONTACT_MANAGE)
    public Result<ContactVO> update(@PathVariable Long id, @Valid @RequestBody ContactSaveDTO dto) {
        return Result.ok(service.update(id, dto));
    }

    @DeleteMapping("/{id}")
    @SaCheckPermission(PermissionCodes.LIFE_CONTACT_MANAGE)
    public Result<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return Result.ok();
    }
}
