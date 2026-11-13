package cn.minims.desk.modules.system.backup.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.minims.desk.common.api.Result;
import cn.minims.desk.framework.audit.AuditLog;
import cn.minims.desk.modules.system.backup.service.BackupService;
import cn.minims.desk.modules.system.backup.vo.BackupVO;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** 后台备份与恢复接口，见 docs/接口清单.md。 */
@RestController
@RequestMapping("/api/console/backups")
@SaCheckRole("ADMIN")
public class BackupController {

    private final BackupService backupService;

    public BackupController(BackupService backupService) {
        this.backupService = backupService;
    }

    @GetMapping
    public Result<List<BackupVO.BackupFile>> list() {
        return Result.ok(backupService.list());
    }

    @PostMapping
    public Result<BackupVO.BackupFile> create() {
        return Result.ok(backupService.create("manual"));
    }

    @GetMapping("/restore/status")
    public Result<BackupVO.RestoreStatus> restoreStatus() {
        return Result.ok(backupService.status());
    }

    @PostMapping("/restore")
    @AuditLog(module = "console-backup", action = "restore", targetType = "backup", ignoreParams = true)
    public Result<BackupVO.RestoreResult> restore(@RequestParam("file") MultipartFile file) {
        return Result.ok(backupService.queue(file));
    }

    @GetMapping("/{fileName}/download")
    public ResponseEntity<Resource> download(@PathVariable String fileName) {
        Path path = backupService.locate(fileName);
        try {
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                    .contentLength(java.nio.file.Files.size(path))
                    .body(new FileSystemResource(path));
        } catch (IOException ex) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @DeleteMapping("/{fileName}")
    public Result<Void> delete(@PathVariable String fileName) {
        backupService.delete(fileName);
        return Result.ok();
    }
}
