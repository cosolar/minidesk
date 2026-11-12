package cn.minims.desk.modules.system.file.controller;

import cn.dev33.satoken.stp.StpUtil;
import cn.minims.desk.common.api.Result;
import cn.minims.desk.modules.system.file.service.FileService;
import cn.minims.desk.modules.system.file.vo.FileVO;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文件上传与读取。
 *
 * <p>{@code /raw} 在登录拦截器白名单里（img 标签带不了请求头），改为校验查询参数里的 token。
 * 校验通过后按 token 的 loginId 过滤文件归属，读不到直接 404。
 */
@RestController
@RequestMapping("/api/files")
public class FileController {

    private final FileService fileService;

    public FileController(FileService fileService) {
        this.fileService = fileService;
    }

    @PostMapping
    public Result<FileVO> upload(@RequestParam("file") MultipartFile file,
                                 @RequestParam(required = false) String bizType) {
        return Result.ok(fileService.store(file, bizType));
    }

    @GetMapping("/{id}/raw")
    public ResponseEntity<Resource> raw(@PathVariable Long id,
                                        @RequestParam(required = false) String token) {
        Object loginId = StringUtils.hasText(token) ? StpUtil.getLoginIdByToken(token) : null;
        if (loginId == null) {
            return ResponseEntity.status(401).build();
        }
        FileService.OwnedFile owned = fileService.loadOwned(id, Long.parseLong(loginId.toString()));
        MediaType mediaType = StringUtils.hasText(owned.mime())
                ? MediaType.parseMediaType(owned.mime())
                : MediaType.APPLICATION_OCTET_STREAM;
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=86400")
                .body(owned.resource());
    }
}
