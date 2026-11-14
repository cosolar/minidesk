package cn.minims.desk.modules.note.service;

import cn.hutool.crypto.digest.BCrypt;
import cn.minims.desk.common.api.ErrorCode;
import cn.minims.desk.common.exception.BizException;
import cn.minims.desk.framework.tenant.TenantContext;
import cn.minims.desk.modules.note.dto.NoteShareSaveDTO;
import cn.minims.desk.modules.note.entity.NoteDO;
import cn.minims.desk.modules.note.entity.NoteShareDO;
import cn.minims.desk.modules.note.mapper.NoteMapper;
import cn.minims.desk.modules.note.mapper.NoteShareMapper;
import cn.minims.desk.modules.note.vo.NoteShareVO;
import cn.minims.desk.modules.note.vo.PublicNoteVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 笔记分享：一篇笔记一条可编辑短链；公开访问按 token 解析，未登录也能读。
 */
@Service
public class NoteShareService {

    private static final String STATUS_PASSWORD_REQUIRED = "password_required";
    private static final String STATUS_WRONG_PASSWORD = "wrong_password";
    private static final String STATUS_EXPIRED = "expired";
    private static final String STATUS_NOT_FOUND = "not_found";

    private final NoteShareMapper noteShareMapper;
    private final NoteMapper noteMapper;

    public NoteShareService(NoteShareMapper noteShareMapper, NoteMapper noteMapper) {
        this.noteShareMapper = noteShareMapper;
        this.noteMapper = noteMapper;
    }

    /** 当前用户对这篇笔记的分享配置，没有则返回 null */
    public NoteShareVO get(Long noteId) {
        requireNote(noteId);
        NoteShareDO share = findByNote(noteId);
        return share == null ? null : NoteShareVO.from(share);
    }

    @Transactional(rollbackFor = Exception.class)
    public NoteShareVO save(Long noteId, NoteShareSaveDTO dto) {
        requireNote(noteId);
        String token = dto.token().trim();
        NoteShareDO existing = findByNote(noteId);
        Long selfId = existing == null ? null : existing.getId();
        // token 是公开路径，必须全局唯一；租户插件只限本用户，校验时要忽略
        Long duplicated = TenantContext.ignore(() -> noteShareMapper.selectCount(
                Wrappers.lambdaQuery(NoteShareDO.class)
                        .eq(NoteShareDO::getToken, token)
                        .ne(selfId != null, NoteShareDO::getId, selfId)));
        if (duplicated != null && duplicated > 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "该分享链接已被占用，请换一个");
        }
        LocalDateTime now = LocalDateTime.now();
        if (dto.expireAt() != null && !dto.expireAt().isAfter(now)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "有效期必须晚于当前时间");
        }

        NoteShareDO share = existing == null ? new NoteShareDO() : existing;
        share.setNoteId(noteId);
        share.setToken(token);
        if (dto.clearPassword()) {
            share.setPasswordHash(null);
        } else if (StringUtils.hasText(dto.password())) {
            share.setPasswordHash(BCrypt.hashpw(dto.password(), BCrypt.gensalt()));
        }
        share.setExpireAt(dto.expireAt());
        share.setEnabled(dto.enabled() == null || dto.enabled() ? 1 : 0);
        share.setUpdatedAt(now);
        if (existing == null) {
            share.setCreatedAt(now);
            noteShareMapper.insert(share);
        } else {
            noteShareMapper.updateById(share);
        }
        return NoteShareVO.from(share);
    }

    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long noteId) {
        requireNote(noteId);
        noteShareMapper.delete(Wrappers.lambdaQuery(NoteShareDO.class).eq(NoteShareDO::getNoteId, noteId));
    }

    /**
     * 公开解析。结果用 status 表达，不抛业务异常：
     * 匿名页需要区分未找到、过期、待解锁，而全局异常处理会弹 toast。
     */
    public PublicNoteVO publicView(String token, String password) {
        if (!StringUtils.hasText(token)) {
            return PublicNoteVO.status(STATUS_NOT_FOUND);
        }
        NoteShareDO share = TenantContext.ignore(() -> noteShareMapper.selectOne(
                Wrappers.lambdaQuery(NoteShareDO.class).eq(NoteShareDO::getToken, token)));
        if (share == null || share.getEnabled() == null || share.getEnabled() != 1) {
            return PublicNoteVO.status(STATUS_NOT_FOUND);
        }
        if (share.getExpireAt() != null && !share.getExpireAt().isAfter(LocalDateTime.now())) {
            return PublicNoteVO.status(STATUS_EXPIRED);
        }
        boolean hasPassword = StringUtils.hasText(share.getPasswordHash());
        if (hasPassword) {
            if (!StringUtils.hasText(password)) {
                return PublicNoteVO.status(STATUS_PASSWORD_REQUIRED);
            }
            if (!BCrypt.checkpw(password, share.getPasswordHash())) {
                return PublicNoteVO.status(STATUS_WRONG_PASSWORD);
            }
        }
        NoteDO note = TenantContext.ignore(() -> noteMapper.selectById(share.getNoteId()));
        if (note == null) {
            return PublicNoteVO.status(STATUS_NOT_FOUND);
        }
        return PublicNoteVO.ok(note.getTitle(), note.getContent(), note.getUpdatedAt());
    }

    private NoteDO requireNote(Long noteId) {
        NoteDO note = noteMapper.selectById(noteId);
        if (note == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        return note;
    }

    private NoteShareDO findByNote(Long noteId) {
        return noteShareMapper.selectOne(
                Wrappers.lambdaQuery(NoteShareDO.class).eq(NoteShareDO::getNoteId, noteId));
    }
}
