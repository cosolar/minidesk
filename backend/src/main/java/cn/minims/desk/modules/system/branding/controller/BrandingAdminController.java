package cn.minims.desk.modules.system.branding.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.minims.desk.common.api.Result;
import cn.minims.desk.modules.system.branding.service.BrandingService;
import cn.minims.desk.modules.system.branding.vo.SiteVO;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 后台品牌设置：站点名、方形 logo、横版 logo。
 */
@RestController
@RequestMapping("/api/console/branding")
@SaCheckRole("ADMIN")
public class BrandingAdminController {

    private final BrandingService brandingService;

    public BrandingAdminController(BrandingService brandingService) {
        this.brandingService = brandingService;
    }

    @PutMapping("/name")
    public Result<SiteVO> rename(@RequestBody SiteVO body) {
        return Result.ok(brandingService.rename(body.siteName()));
    }

    @PostMapping("/{kind}")
    public Result<SiteVO> upload(@PathVariable String kind, @RequestParam("file") MultipartFile file) {
        return Result.ok(brandingService.upload(kind, file));
    }

    @DeleteMapping("/{kind}")
    public Result<Void> remove(@PathVariable String kind) {
        brandingService.remove(kind);
        return Result.ok();
    }
}
