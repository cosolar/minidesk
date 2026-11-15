package cn.minims.desk.modules.drive.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.stp.StpUtil;
import cn.minims.desk.common.api.Result;
import cn.minims.desk.framework.satoken.PermissionCodes;
import cn.minims.desk.modules.drive.config.DriveConfigService;
import cn.minims.desk.modules.drive.dto.DriveConfigDTO;
import cn.minims.desk.modules.drive.dto.DriveFolderDTO;
import cn.minims.desk.modules.drive.dto.DriveRenameDTO;
import cn.minims.desk.modules.drive.service.DriveService;
import cn.minims.desk.modules.drive.vo.DriveConfigVO;
import cn.minims.desk.modules.drive.vo.DriveFileVO;
import cn.minims.desk.modules.drive.vo.DriveStatsVO;
import cn.minims.desk.modules.drive.vo.DriveTestResultVO;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 网盘接口，见 docs/接口清单.md §20。
 *
 * <p>raw 在登录白名单里（img 标签带不了请求头），改为校验查询参数里的 token。
 */
@RestController
@RequestMapping("/api/drive")
public class DriveController {

    private final DriveService driveService;
    private final DriveConfigService configService;

    public DriveController(DriveService driveService, DriveConfigService configService) {
        this.driveService = driveService;
        this.configService = configService;
    }

    @GetMapping("/files")
    public Result<List<DriveFileVO>> list(@RequestParam(defaultValue = "0") Long parentId,
                                          @RequestParam(required = false) String keyword) {
        return Result.ok(driveService.list(parentId, keyword));
    }

    @GetMapping("/stats")
    public Result<DriveStatsVO> stats() {
        return Result.ok(driveService.stats());
    }

    @PostMapping("/folders")
    @SaCheckPermission(PermissionCodes.SHARED_DRIVE_MANAGE)
    public Result<DriveFileVO> createFolder(@Valid @RequestBody DriveFolderDTO dto) {
        return Result.ok(driveService.createFolder(dto));
    }

    @PostMapping("/files")
    @SaCheckPermission(PermissionCodes.SHARED_DRIVE_MANAGE)
    public Result<DriveFileVO> upload(@RequestParam("file") MultipartFile file,
                                      @RequestParam(defaultValue = "0") Long parentId) {
        return Result.ok(driveService.upload(file, parentId));
    }

    @PutMapping("/files/{id}")
    @SaCheckPermission(PermissionCodes.SHARED_DRIVE_MANAGE)
    public Result<DriveFileVO> rename(@PathVariable Long id, @Valid @RequestBody DriveRenameDTO dto) {
        return Result.ok(driveService.rename(id, dto.name()));
    }

    @DeleteMapping("/files/{id}")
    @SaCheckPermission(PermissionCodes.SHARED_DRIVE_MANAGE)
    public Result<Void> remove(@PathVariable Long id) {
        driveService.remove(id);
        return Result.ok();
    }

    @GetMapping("/files/{id}/raw")
    public ResponseEntity<Resource> raw(@PathVariable Long id,
                                        @RequestParam(required = false) String token,
                                        @RequestParam(required = false, defaultValue = "false") boolean download) {
        Object loginId = StringUtils.hasText(token) ? StpUtil.getLoginIdByToken(token) : null;
        if (loginId == null) {
            return ResponseEntity.status(401).build();
        }
        DriveService.DriveDownload file = driveService.openOwned(id, Long.parseLong(loginId.toString()));
        MediaType mediaType = MediaType.APPLICATION_OCTET_STREAM;
        if (StringUtils.hasText(file.mime())) {
            try {
                mediaType = MediaType.parseMediaType(file.mime());
            } catch (RuntimeException ignored) {
                mediaType = MediaType.APPLICATION_OCTET_STREAM;
            }
        }
        ContentDisposition disposition = (download ? ContentDisposition.attachment() : ContentDisposition.inline())
                .filename(file.name(), StandardCharsets.UTF_8).build();
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=86400")
                .body(new InputStreamResource(file.stream()));
    }

    @GetMapping("/config")
    public Result<DriveConfigVO> config() {
        return Result.ok(configService.view());
    }

    @PutMapping("/config")
    @SaCheckPermission(PermissionCodes.SHARED_DRIVE_MANAGE)
    public Result<DriveConfigVO> saveConfig(@RequestBody DriveConfigDTO dto) {
        return Result.ok(configService.save(dto));
    }

    @PostMapping("/config/test")
    public Result<DriveTestResultVO> testConfig() {
        return Result.ok(driveService.testStorage());
    }
}
