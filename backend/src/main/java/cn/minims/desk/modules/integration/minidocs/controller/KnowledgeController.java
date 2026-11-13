package cn.minims.desk.modules.integration.minidocs.controller;

import cn.minims.desk.common.api.Result;
import cn.minims.desk.framework.audit.AuditLog;
import cn.minims.desk.modules.integration.minidocs.service.MiniDocsService;
import cn.minims.desk.modules.integration.minidocs.vo.MiniDocsVO;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 知识库（MiniDocs）代理接口，见 docs/接口清单.md §10。
 *
 * <p>访问令牌只存在于服务端 integration_config 的密文里，本控制器既不接收也不回传明文。
 */
@RestController
@RequestMapping("/api/kb")
public class KnowledgeController {

    private final MiniDocsService miniDocsService;

    public KnowledgeController(MiniDocsService miniDocsService) {
        this.miniDocsService = miniDocsService;
    }

    @GetMapping("/status")
    public Result<MiniDocsVO.Status> status() {
        return Result.ok(miniDocsService.status());
    }

    /** 设置页「测试连接」：失败会以业务错误码返回，便于前端提示 */
    @PostMapping("/test")
    public Result<MiniDocsVO.Status> test() {
        return Result.ok(miniDocsService.test());
    }

    @GetMapping("/tenants")
    public Result<List<MiniDocsVO.Tenant>> tenants() {
        return Result.ok(miniDocsService.tenants());
    }

    @GetMapping("/books")
    public Result<MiniDocsVO.BookPage> books(
            @RequestParam(defaultValue = "mine") String scope,
            @RequestParam(required = false) String org,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        return Result.ok(miniDocsService.books(scope, org, keyword, page, size));
    }

    @GetMapping("/tree")
    public Result<List<MiniDocsVO.DocNode>> tree(
            @RequestParam(defaultValue = "mine") String scope,
            @RequestParam(required = false) String org,
            @RequestParam String slug) {
        return Result.ok(miniDocsService.tree(scope, org, slug));
    }

    @GetMapping("/doc")
    public Result<MiniDocsVO.DocContent> doc(
            @RequestParam(defaultValue = "mine") String scope,
            @RequestParam(required = false) String org,
            @RequestParam String slug,
            @RequestParam String path) {
        return Result.ok(miniDocsService.doc(scope, org, slug, path));
    }

    @GetMapping("/search")
    public Result<List<MiniDocsVO.SearchHit>> search(
            @RequestParam(defaultValue = "mine") String scope,
            @RequestParam(required = false) String org,
            @RequestParam String slug,
            @RequestParam String q,
            @RequestParam(required = false) String bookName,
            @RequestParam(defaultValue = "30") int limit) {
        return Result.ok(miniDocsService.search(scope, org, slug, q, bookName, limit));
    }

    @PostMapping("/books")
    @AuditLog(module = "study-kb", action = "create-book", targetType = "kb")
    public Result<MiniDocsVO.Book> createBook(@RequestBody Map<String, String> body) {
        return Result.ok(miniDocsService.createBook(body.get("org"), body.get("name"),
                body.get("description"), body.get("visibility")));
    }

    /** 正文可能很长，不写进操作审计的参数字段 */
    @PostMapping("/docs")
    @AuditLog(module = "study-kb", action = "create-doc", targetType = "doc", ignoreParams = true)
    public Result<MiniDocsVO.CreateResult> createDoc(@RequestBody Map<String, String> body) {
        return Result.ok(miniDocsService.createDoc(body.get("org"), body.get("slug"),
                body.get("path"), body.get("content")));
    }
}
