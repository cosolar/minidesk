package cn.minims.desk.modules.note.service;

import cn.minims.desk.common.api.ErrorCode;
import cn.minims.desk.common.api.PageResult;
import cn.minims.desk.common.exception.BizException;
import cn.minims.desk.modules.note.dto.NoteSaveDTO;
import cn.minims.desk.modules.note.entity.NoteDO;
import cn.minims.desk.modules.note.mapper.NoteMapper;
import cn.minims.desk.modules.note.vo.NoteDetailVO;
import cn.minims.desk.modules.note.vo.NoteSummaryVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 笔记业务。列表不返回正文，避免一次拉几百篇 Markdown。
 */
@Service
public class NoteService {

    private final NoteMapper noteMapper;

    public NoteService(NoteMapper noteMapper) {
        this.noteMapper = noteMapper;
    }

    public PageResult<NoteSummaryVO> page(String module, String type, String keyword, long pageNo, long pageSize) {
        LambdaQueryWrapper<NoteDO> query = Wrappers.lambdaQuery(NoteDO.class)
                .select(NoteDO::getId, NoteDO::getTitle, NoteDO::getModule, NoteDO::getType,
                        NoteDO::getTags, NoteDO::getWordCount, NoteDO::getPinned,
                        NoteDO::getCreatedAt, NoteDO::getUpdatedAt)
                .eq(NoteDO::getArchived, 0);
        if (StringUtils.hasText(module)) {
            query.eq(NoteDO::getModule, module);
        }
        if (StringUtils.hasText(type)) {
            query.eq(NoteDO::getType, type);
        }
        if (StringUtils.hasText(keyword)) {
            query.and(w -> w.like(NoteDO::getTitle, keyword.trim())
                    .or().like(NoteDO::getContent, keyword.trim()));
        }
        query.orderByDesc(NoteDO::getPinned).orderByDesc(NoteDO::getUpdatedAt);

        Page<NoteDO> result = noteMapper.selectPage(Page.of(pageNo, pageSize), query);
        List<NoteSummaryVO> list = result.getRecords().stream().map(NoteSummaryVO::from).toList();
        return PageResult.of(list, result.getTotal(), pageNo, pageSize);
    }

    public NoteDetailVO detail(Long id) {
        return NoteDetailVO.from(require(id));
    }

    @Transactional(rollbackFor = Exception.class)
    public NoteDetailVO create(NoteSaveDTO dto) {
        NoteDO note = new NoteDO();
        note.setTitle(resolveTitle(dto));
        note.setContent(dto.content() == null ? "" : dto.content());
        note.setModule(StringUtils.hasText(dto.module()) ? dto.module() : "work");
        note.setType(StringUtils.hasText(dto.type()) ? dto.type() : "note");
        note.setTags(dto.tags());
        note.setFolder(dto.folder());
        note.setPinned(Boolean.TRUE.equals(dto.pinned()) ? 1 : 0);
        note.setArchived(0);
        note.setWordCount(countWords(note.getContent()));
        note.setCreatedAt(LocalDateTime.now());
        note.setUpdatedAt(LocalDateTime.now());
        noteMapper.insert(note);
        return NoteDetailVO.from(note);
    }

    @Transactional(rollbackFor = Exception.class)
    public NoteDetailVO update(Long id, NoteSaveDTO dto) {
        NoteDO note = require(id);
        note.setTitle(resolveTitle(dto));
        note.setContent(dto.content() == null ? "" : dto.content());
        if (StringUtils.hasText(dto.module())) {
            note.setModule(dto.module());
        }
        if (StringUtils.hasText(dto.type())) {
            note.setType(dto.type());
        }
        note.setTags(dto.tags());
        note.setFolder(dto.folder());
        if (dto.pinned() != null) {
            note.setPinned(dto.pinned() ? 1 : 0);
        }
        note.setWordCount(countWords(note.getContent()));
        note.setUpdatedAt(LocalDateTime.now());
        noteMapper.updateById(note);
        return NoteDetailVO.from(note);
    }

    @Transactional(rollbackFor = Exception.class)
    public void togglePin(Long id, boolean pinned) {
        NoteDO note = require(id);
        note.setPinned(pinned ? 1 : 0);
        note.setUpdatedAt(LocalDateTime.now());
        noteMapper.updateById(note);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        require(id);
        noteMapper.deleteById(id);
    }

    private NoteDO require(Long id) {
        NoteDO note = noteMapper.selectById(id);
        if (note == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        return note;
    }

    /** 标题为空时取正文第一行有效文字，仍为空则用「无标题」 */
    private String resolveTitle(NoteSaveDTO dto) {
        if (StringUtils.hasText(dto.title())) {
            return dto.title().trim();
        }
        String content = dto.content() == null ? "" : dto.content();
        for (String line : content.split("\n")) {
            String cleaned = line.replaceAll("^[#>*\\-\\s]+", "").trim();
            if (!cleaned.isEmpty()) {
                return cleaned.length() > 60 ? cleaned.substring(0, 60) : cleaned;
            }
        }
        return "无标题";
    }

    private int countWords(String content) {
        if (content == null || content.isBlank()) {
            return 0;
        }
        return content.replaceAll("\\s", "").length();
    }
}
