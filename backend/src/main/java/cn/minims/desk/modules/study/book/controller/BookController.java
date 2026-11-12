package cn.minims.desk.modules.study.book.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.minims.desk.common.api.Result;
import cn.minims.desk.modules.study.book.service.BookService;
import cn.minims.desk.modules.study.book.vo.BookVO;
import java.util.List;
import java.util.Map;
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
 * 阅读管理接口，见 docs/接口清单.md §8。
 */
@RestController
@RequestMapping("/api/books")
public class BookController {

    private final BookService bookService;

    public BookController(BookService bookService) {
        this.bookService = bookService;
    }

    @GetMapping
    public Result<List<BookVO>> list(@RequestParam(required = false) String status) {
        return Result.ok(bookService.list(status));
    }

    @PostMapping
    @SaCheckPermission("study:book:manage")
    public Result<BookVO> create(@RequestBody Map<String, Object> body) {
        String title = body.get("title") == null ? null : String.valueOf(body.get("title"));
        String author = body.get("author") == null ? null : String.valueOf(body.get("author"));
        String category = body.get("category") == null ? null : String.valueOf(body.get("category"));
        Integer totalPages = body.get("totalPages") == null
                ? null : Integer.valueOf(String.valueOf(body.get("totalPages")));
        return Result.ok(bookService.create(title, author, category, totalPages));
    }

    @PutMapping("/{id}")
    @SaCheckPermission("study:book:manage")
    public Result<BookVO> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Integer readPages = body.get("readPages") == null
                ? null : Integer.valueOf(String.valueOf(body.get("readPages")));
        String status = body.get("status") == null ? null : String.valueOf(body.get("status"));
        Integer rating = body.get("rating") == null ? null : Integer.valueOf(String.valueOf(body.get("rating")));
        return Result.ok(bookService.updateProgress(id, readPages, status, rating));
    }

    @DeleteMapping("/{id}")
    @SaCheckPermission("study:book:manage")
    public Result<Void> delete(@PathVariable Long id) {
        bookService.delete(id);
        return Result.ok();
    }

    @GetMapping("/{id}/notes")
    public Result<List<BookVO.NoteVO>> notes(@PathVariable Long id) {
        return Result.ok(bookService.notes(id));
    }

    @PostMapping("/{id}/notes")
    @SaCheckPermission("study:book:manage")
    public Result<Void> addNote(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        String content = body.get("content") == null ? null : String.valueOf(body.get("content"));
        Integer pageNo = body.get("pageNo") == null ? null : Integer.valueOf(String.valueOf(body.get("pageNo")));
        bookService.addNote(id, content, pageNo);
        return Result.ok();
    }
}
