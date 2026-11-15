package cn.minims.desk.modules.note.service;

import cn.minims.desk.common.api.ErrorCode;
import cn.minims.desk.common.api.PageResult;
import cn.minims.desk.common.exception.BizException;
import cn.minims.desk.modules.note.NoteModules;
import cn.minims.desk.modules.note.dto.NoteSaveDTO;
import cn.minims.desk.modules.note.entity.NoteDO;
import cn.minims.desk.modules.note.entity.NoteFolderDO;
import cn.minims.desk.modules.note.mapper.NoteFolderMapper;
import cn.minims.desk.modules.note.mapper.NoteMapper;
import cn.minims.desk.modules.note.vo.NoteDetailVO;
import cn.minims.desk.modules.note.vo.NoteOutlineVO;
import cn.minims.desk.modules.note.vo.NoteRecycleBoard;
import cn.minims.desk.modules.note.vo.NoteSummaryVO;
import cn.minims.desk.modules.system.mapper.SysConfigMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 笔记业务。列表只带裁剪后的纯文本摘要，避免一次拉几百篇 Markdown 原文。
 */
@Service
public class NoteService {

    private final NoteMapper noteMapper;
    private final NoteFolderMapper folderMapper;
    private final SysConfigMapper configMapper;

    /** 回收站默认保留天数；后台可通过 note_recycle_days 覆盖 */
    private static final String CONFIG_RECYCLE_DAYS = "note_recycle_days";
    private static final int DEFAULT_RECYCLE_DAYS = 30;

    public NoteService(NoteMapper noteMapper, NoteFolderMapper folderMapper, SysConfigMapper configMapper) {
        this.noteMapper = noteMapper;
        this.folderMapper = folderMapper;
        this.configMapper = configMapper;
    }

    public PageResult<NoteSummaryVO> page(String module, String type, String keyword, long pageNo, long pageSize) {
        LambdaQueryWrapper<NoteDO> query = Wrappers.lambdaQuery(NoteDO.class)
                .select(NoteDO::getId, NoteDO::getTitle, NoteDO::getContent, NoteDO::getModule,
                        NoteDO::getType, NoteDO::getTags, NoteDO::getFolderId, NoteDO::getWordCount,
                        NoteDO::getPinned, NoteDO::getCreatedAt, NoteDO::getUpdatedAt)
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

    /** 侧栏文档树：全库文档的标题与归属 */
    public List<NoteOutlineVO> outline() {
        return noteMapper.selectOutline().stream().map(NoteOutlineVO::from).toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public NoteDetailVO create(NoteSaveDTO dto) {
        NoteDO note = new NoteDO();
        note.setTitle(resolveTitle(dto));
        note.setContent(dto.content() == null ? "" : dto.content());
        note.setModule(StringUtils.hasText(dto.module()) ? dto.module() : NoteModules.WORK);
        note.setType(StringUtils.hasText(dto.type()) ? dto.type() : "note");
        note.setTags(dto.tags());
        note.setFolder(dto.folder());
        if (dto.folderId() != null) {
            NoteFolderDO folder = requireFolder(dto.folderId());
            note.setFolderId(folder.getId());
            // 目录归属板块优先，避免前端把文档塞进别的板块的目录
            note.setModule(folder.getModule());
        }
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

    /** 文档移动到目录；folderId 为空表示移到板块根目录，此时用 module 指定板块 */
    @Transactional(rollbackFor = Exception.class)
    public void moveToFolder(Long id, Long folderId, String module) {
        require(id);
        String targetModule;
        if (folderId != null) {
            // 板块跟随目标目录，保证树里不会出现「学习的目录里放工作文档」
            targetModule = requireFolder(folderId).getModule();
        } else {
            if (!NoteModules.supports(module)) {
                throw new BizException(ErrorCode.PARAM_ERROR, "目标板块不正确");
            }
            targetModule = module;
        }
        // updateById 默认忽略 null 字段，移到根目录必须用 set 显式清掉 folder_id
        noteMapper.update(null, Wrappers.lambdaUpdate(NoteDO.class)
                .eq(NoteDO::getId, id)
                .set(NoteDO::getFolderId, folderId)
                .set(NoteDO::getModule, targetModule)
                .set(NoteDO::getUpdatedAt, LocalDateTime.now()));
    }

    /** 删除 = 移入回收站，保留正文与删除时间，到期由定时任务彻底删除 */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        require(id);
        noteMapper.moveToRecycle(id, LocalDateTime.now());
    }

    public NoteRecycleBoard recycle() {
        int keepDays = recycleKeepDays();
        return NoteRecycleBoard.of(keepDays, noteMapper.selectRecycled());
    }

    @Transactional(rollbackFor = Exception.class)
    public void restore(Long id) {
        if (noteMapper.restore(id) == 0) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        NoteDO note = noteMapper.selectById(id);
        // 目录可能已被删除，恢复后把文档放回板块根目录，否则在树里找不到
        if (note != null && note.getFolderId() != null && folderMapper.selectById(note.getFolderId()) == null) {
            noteMapper.update(null, Wrappers.lambdaUpdate(NoteDO.class)
                    .eq(NoteDO::getId, id)
                    .set(NoteDO::getFolderId, null));
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void purge(Long id) {
        if (noteMapper.purge(id) == 0) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public int clearRecycle() {
        return noteMapper.purgeRecycled();
    }

    /** 后台配置 note_recycle_days；缺失或非法值退回默认 30 天 */
    private int recycleKeepDays() {
        try {
            String value = configMapper.selectValue(CONFIG_RECYCLE_DAYS);
            int days = value == null ? DEFAULT_RECYCLE_DAYS : Integer.parseInt(value.trim());
            return Math.max(days, 1);
        } catch (RuntimeException ex) {
            return DEFAULT_RECYCLE_DAYS;
        }
    }

    private NoteDO require(Long id) {
        NoteDO note = noteMapper.selectById(id);
        if (note == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        return note;
    }

    private NoteFolderDO requireFolder(Long folderId) {
        NoteFolderDO folder = folderMapper.selectById(folderId);
        if (folder == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        return folder;
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
