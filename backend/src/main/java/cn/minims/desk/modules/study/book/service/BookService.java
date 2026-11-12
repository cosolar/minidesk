package cn.minims.desk.modules.study.book.service;

import cn.minims.desk.common.api.ErrorCode;
import cn.minims.desk.common.exception.BizException;
import cn.minims.desk.modules.study.book.entity.BookDO;
import cn.minims.desk.modules.study.book.entity.BookNoteDO;
import cn.minims.desk.modules.study.book.mapper.BookMapper;
import cn.minims.desk.modules.study.book.mapper.BookNoteMapper;
import cn.minims.desk.modules.study.book.vo.BookVO;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 阅读管理：书单 + 进度 + 读书笔记。
 */
@Service
public class BookService {

    private final BookMapper bookMapper;
    private final BookNoteMapper noteMapper;

    public BookService(BookMapper bookMapper, BookNoteMapper noteMapper) {
        this.bookMapper = bookMapper;
        this.noteMapper = noteMapper;
    }

    public List<BookVO> list(String status) {
        var query = Wrappers.lambdaQuery(BookDO.class);
        if (StringUtils.hasText(status)) {
            query.eq(BookDO::getStatus, status);
        }
        query.orderByDesc(BookDO::getUpdatedAt).orderByDesc(BookDO::getId);
        return bookMapper.selectList(query).stream().map(this::toVO).toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public BookVO create(String title, String author, String category, Integer totalPages) {
        if (!StringUtils.hasText(title)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "请输入书名");
        }
        BookDO book = new BookDO();
        book.setTitle(title.trim());
        book.setAuthor(author);
        book.setCategory(category);
        book.setTotalPages(totalPages == null ? 0 : totalPages);
        book.setReadPages(0);
        book.setStatus("wish");
        book.setCreatedAt(LocalDateTime.now());
        book.setUpdatedAt(LocalDateTime.now());
        bookMapper.insert(book);
        return toVO(book);
    }

    @Transactional(rollbackFor = Exception.class)
    public BookVO updateProgress(Long id, Integer readPages, String status, Integer rating) {
        BookDO book = require(id);
        if (readPages != null) {
            book.setReadPages(Math.max(readPages, 0));
        }
        if (StringUtils.hasText(status)) {
            book.setStatus(status);
            if ("reading".equals(status) && book.getStartDate() == null) {
                book.setStartDate(LocalDate.now());
            }
            if ("done".equals(status)) {
                book.setFinishDate(LocalDate.now());
                if (book.getTotalPages() != null && book.getTotalPages() > 0) {
                    book.setReadPages(book.getTotalPages());
                }
            }
        }
        if (rating != null) {
            book.setRating(rating);
        }
        book.setUpdatedAt(LocalDateTime.now());
        bookMapper.updateById(book);
        return toVO(book);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        require(id);
        bookMapper.deleteById(id);
        noteMapper.delete(Wrappers.lambdaQuery(BookNoteDO.class).eq(BookNoteDO::getBookId, id));
    }

    public List<BookVO.NoteVO> notes(Long bookId) {
        return noteMapper.selectList(Wrappers.lambdaQuery(BookNoteDO.class)
                        .eq(BookNoteDO::getBookId, bookId)
                        .orderByDesc(BookNoteDO::getId))
                .stream()
                .map(note -> new BookVO.NoteVO(
                        String.valueOf(note.getId()),
                        note.getContent(),
                        note.getPageNo(),
                        note.getCreatedAt() == null ? null : note.getCreatedAt().toString()))
                .toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public void addNote(Long bookId, String content, Integer pageNo) {
        require(bookId);
        if (!StringUtils.hasText(content)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "笔记内容不能为空");
        }
        BookNoteDO note = new BookNoteDO();
        note.setBookId(bookId);
        note.setContent(content.trim());
        note.setPageNo(pageNo);
        note.setCreatedAt(LocalDateTime.now());
        noteMapper.insert(note);
    }

    private BookDO require(Long id) {
        BookDO book = bookMapper.selectById(id);
        if (book == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        return book;
    }

    private BookVO toVO(BookDO book) {
        int total = book.getTotalPages() == null ? 0 : book.getTotalPages();
        int read = book.getReadPages() == null ? 0 : book.getReadPages();
        int progress = total == 0 ? ("done".equals(book.getStatus()) ? 100 : 0)
                : Math.min(100, (int) Math.round(read * 100.0 / total));
        return new BookVO(
                String.valueOf(book.getId()),
                book.getTitle(),
                book.getAuthor(),
                book.getCategory(),
                total,
                read,
                progress,
                book.getStatus(),
                book.getRating(),
                book.getStartDate() == null ? null : book.getStartDate().toString(),
                book.getFinishDate() == null ? null : book.getFinishDate().toString());
    }
}
