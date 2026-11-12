package cn.minims.desk.modules.user.service;

import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.BCrypt;
import cn.minims.desk.common.api.ErrorCode;
import cn.minims.desk.common.exception.BizException;
import cn.minims.desk.framework.tenant.TenantContext;
import cn.minims.desk.modules.auth.vo.UserVO;
import cn.minims.desk.modules.system.file.service.FileService;
import cn.minims.desk.modules.system.file.vo.FileVO;
import cn.minims.desk.modules.user.dto.PasswordChangeDTO;
import cn.minims.desk.modules.user.dto.ProfileUpdateDTO;
import cn.minims.desk.modules.user.entity.SysUserDO;
import cn.minims.desk.modules.user.mapper.SysUserMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.time.LocalDateTime;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * 个人中心：资料、头像、密码。
 */
@Service
public class UserService {

    private final SysUserMapper userMapper;
    private final FileService fileService;

    public UserService(SysUserMapper userMapper, FileService fileService) {
        this.userMapper = userMapper;
        this.fileService = fileService;
    }

    public UserVO profile() {
        return toVO(requireCurrent());
    }

    @Transactional(rollbackFor = Exception.class)
    public UserVO updateProfile(ProfileUpdateDTO dto) {
        SysUserDO user = requireCurrent();
        if (StrUtil.isNotBlank(dto.nickname())) {
            user.setNickname(dto.nickname().trim());
        }
        if (dto.email() != null) {
            String email = dto.email().trim().toLowerCase(Locale.ROOT);
            if (StrUtil.isBlank(email)) {
                user.setEmail(null);
            } else if (!email.equals(user.getEmail())) {
                Long exists = userMapper.selectCount(Wrappers.<SysUserDO>lambdaQuery()
                        .eq(SysUserDO::getEmail, email)
                        .ne(SysUserDO::getId, user.getId()));
                if (exists != null && exists > 0) {
                    throw new BizException(ErrorCode.EMAIL_EXISTS);
                }
                user.setEmail(email);
            }
        }
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);
        return toVO(user);
    }

    @Transactional(rollbackFor = Exception.class)
    public void changePassword(PasswordChangeDTO dto) {
        if (!dto.newPassword().equals(dto.confirmPassword())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "两次输入的新密码不一致");
        }
        if (dto.oldPassword().equals(dto.newPassword())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "新密码不能与原密码相同");
        }
        SysUserDO user = requireCurrent();
        if (!BCrypt.checkpw(dto.oldPassword(), user.getPasswordHash())) {
            throw new BizException(ErrorCode.WRONG_PASSWORD);
        }
        user.setPasswordHash(BCrypt.hashpw(dto.newPassword(), BCrypt.gensalt()));
        user.setPwdResetRequired(0);
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);
    }

    @Transactional(rollbackFor = Exception.class)
    public UserVO updateAvatar(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "请选择图片");
        }
        FileVO stored = fileService.store(file, "avatar");
        SysUserDO user = requireCurrent();
        user.setAvatarFileId(stored.id());
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);
        return toVO(user);
    }

    private SysUserDO requireCurrent() {
        long userId = TenantContext.requireUserId();
        SysUserDO user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ErrorCode.ACCOUNT_NOT_FOUND);
        }
        return user;
    }

    private UserVO toVO(SysUserDO user) {
        String avatarUrl = user.getAvatarFileId() == null
                ? null : "/api/files/" + user.getAvatarFileId() + "/raw";
        return new UserVO(
                String.valueOf(user.getId()),
                user.getUsername(),
                user.getNickname(),
                avatarUrl,
                user.getEmail(),
                null,
                user.getPwdResetRequired() != null && user.getPwdResetRequired() == 1);
    }
}
