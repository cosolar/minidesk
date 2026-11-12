package cn.minims.desk.modules.system.branding.controller;

import cn.minims.desk.common.api.Result;
import cn.minims.desk.modules.system.branding.service.BrandingService;
import cn.minims.desk.modules.system.branding.vo.SiteVO;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 站点公开信息：登录页与侧栏都要用，不需要登录。
 */
@RestController
@RequestMapping("/api/site")
public class SiteController {

    private final BrandingService brandingService;

    public SiteController(BrandingService brandingService) {
        this.brandingService = brandingService;
    }

    @GetMapping
    public Result<SiteVO> site() {
        return Result.ok(brandingService.site());
    }

    @GetMapping("/branding/{kind}")
    public ResponseEntity<Resource> branding(@PathVariable String kind) {
        BrandingService.StoredFile file = brandingService.read(kind);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.contentType()))
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=86400")
                .body(file.resource());
    }
}
