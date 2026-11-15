package cn.minims.desk.modules.note.service;

import cn.minims.desk.common.api.ErrorCode;
import cn.minims.desk.common.exception.BizException;
import cn.minims.desk.modules.note.NoteModules;
import cn.minims.desk.modules.note.dto.NoteFolderSaveDTO;
import cn.minims.desk.modules.note.entity.NoteFolderDO;
import cn.minims.desk.modules.note.mapper.NoteFolderMapper;
import cn.minims.desk.modules.note.mapper.NoteMapper;
import cn.minims.desk.modules.note.vo.NoteFolderVO;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 笔记目录：工作 / 学习 / 生活是固定根，其下目录支持多级嵌套。
 */
@Service
public class NoteFolderService {

    private final NoteFolderMapper folderMapper;
    private final NoteMapper noteMapper;

    public NoteFolderService(NoteFolderMapper folderMapper, NoteMapper noteMapper) {
        this.folderMapper = folderMapper;
        this.noteMapper = noteMapper;
    }

    /** 平铺返回全部目录，前端按 parentId 拼树 */
    public List<NoteFolderVO> list() {
        return folderMapper.selectList(Wrappers.lambdaQuery(NoteFolderDO.class)
                        .orderByAsc(NoteFolderDO::getModule)
                        .orderByAsc(NoteFolderDO::getSortNo)
                        .orderByAsc(NoteFolderDO::getId))
                .stream()
                .map(NoteFolderVO::from)
                .toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public NoteFolderVO create(NoteFolderSaveDTO dto) {
        String name = dto.name().trim();
        Long parentId = dto.parentId();
        String module;
        if (parentId == null) {
            module = NoteModules.supports(dto.module()) ? dto.module() : NoteModules.WORK;
        } else {
            // 挂在已有目录下时继承父目录板块，忽略前端传来的 module
            module = require(parentId).getModule();
        }
        ensureNameFree(module, parentId, name, null);

        NoteFolderDO folder = new NoteFolderDO();
        folder.setName(name);
        folder.setModule(module);
        folder.setParentId(parentId);
        folder.setSortNo(nextSortNo(module, parentId));
        folder.setCreatedAt(LocalDateTime.now());
        folder.setUpdatedAt(LocalDateTime.now());
        folderMapper.insert(folder);
        return NoteFolderVO.from(folder);
    }

    @Transactional(rollbackFor = Exception.class)
    public NoteFolderVO rename(Long id, NoteFolderSaveDTO dto) {
        NoteFolderDO folder = require(id);
        String name = dto.name().trim();
        ensureNameFree(folder.getModule(), folder.getParentId(), name, id);
        folder.setName(name);
        folder.setUpdatedAt(LocalDateTime.now());
        folderMapper.updateById(folder);
        return NoteFolderVO.from(folder);
    }

    /** 目录移动只允许在同一板块内，避免「生活」的目录突然出现在「工作」下 */
    @Transactional(rollbackFor = Exception.class)
    public NoteFolderVO move(Long id, Long parentId) {
        NoteFolderDO folder = require(id);
        if (parentId != null) {
            if (parentId.equals(id)) {
                throw new BizException(ErrorCode.PARAM_ERROR, "不能移动到自身");
            }
            NoteFolderDO parent = require(parentId);
            if (!parent.getModule().equals(folder.getModule())) {
                throw new BizException(ErrorCode.PARAM_ERROR, "目录不能跨板块移动");
            }
            if (isDescendant(id, parentId)) {
                throw new BizException(ErrorCode.PARAM_ERROR, "不能移动到自己的子目录下");
            }
        }
        folder.setParentId(parentId);
        folder.setSortNo(nextSortNo(folder.getModule(), parentId));
        folder.setUpdatedAt(LocalDateTime.now());
        folderMapper.updateById(folder);
        return NoteFolderVO.from(folder);
    }

    /**
     * 删除目录：子孙目录一起删，里面的文档全部移入回收站（保留正文，可恢复）。
     *
     * @return 移入回收站的文档数
     */
    @Transactional(rollbackFor = Exception.class)
    public int delete(Long id) {
        require(id);
        List<Long> ids = collectSubtree(id);
        LocalDateTime now = LocalDateTime.now();
        int moved = noteMapper.moveFoldersToRecycle(ids, now);
        folderMapper.softDeleteBatch(ids, now);
        return moved;
    }

    /** 广度优先收集当前目录与全部子孙目录 id */
    private List<Long> collectSubtree(Long rootId) {
        Map<Long, List<Long>> children = new HashMap<>(16);
        for (NoteFolderDO folder : folderMapper.selectList(null)) {
            Long parentId = folder.getParentId();
            if (parentId != null) {
                children.computeIfAbsent(parentId, key -> new ArrayList<>(4)).add(folder.getId());
            }
        }
        List<Long> ids = new ArrayList<>(8);
        Deque<Long> queue = new ArrayDeque<>();
        queue.add(rootId);
        while (!queue.isEmpty()) {
            Long current = queue.poll();
            ids.add(current);
            queue.addAll(children.getOrDefault(current, List.of()));
        }
        return ids;
    }

    private boolean isDescendant(Long ancestorId, Long candidateId) {
        Map<Long, Long> parents = new HashMap<>(16);
        for (NoteFolderDO folder : folderMapper.selectList(null)) {
            parents.put(folder.getId(), folder.getParentId());
        }
        Long cursor = parents.get(candidateId);
        int guard = 0;
        while (cursor != null && guard++ < 64) {
            if (cursor.equals(ancestorId)) {
                return true;
            }
            cursor = parents.get(cursor);
        }
        return false;
    }

    private void ensureNameFree(String module, Long parentId, String name, Long excludeId) {
        var query = Wrappers.lambdaQuery(NoteFolderDO.class)
                .eq(NoteFolderDO::getModule, module)
                .eq(NoteFolderDO::getName, name);
        if (parentId == null) {
            query.isNull(NoteFolderDO::getParentId);
        } else {
            query.eq(NoteFolderDO::getParentId, parentId);
        }
        if (excludeId != null) {
            query.ne(NoteFolderDO::getId, excludeId);
        }
        if (folderMapper.selectCount(query) > 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "同目录下已存在「" + name + "」");
        }
    }

    /** 同级目录按创建顺序排，直接取同级数量作为下一个序号 */
    private int nextSortNo(String module, Long parentId) {
        var query = Wrappers.lambdaQuery(NoteFolderDO.class).eq(NoteFolderDO::getModule, module);
        if (parentId == null) {
            query.isNull(NoteFolderDO::getParentId);
        } else {
            query.eq(NoteFolderDO::getParentId, parentId);
        }
        return folderMapper.selectCount(query).intValue();
    }

    private NoteFolderDO require(Long id) {
        NoteFolderDO folder = folderMapper.selectById(id);
        if (folder == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        return folder;
    }
}
