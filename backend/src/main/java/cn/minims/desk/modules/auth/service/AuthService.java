package cn.minims.desk.modules.auth.service;

import cn.dev33.satoken.stp.SaTokenInfo;
import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.BCrypt;
import cn.minims.desk.common.api.ErrorCode;
import cn.minims.desk.common.exception.BizException;
import cn.minims.desk.framework.tenant.TenantContext;
import cn.minims.desk.modules.auth.dto.EmailRegisterDTO;
import cn.minims.desk.modules.auth.dto.LoginDTO;
import cn.minims.desk.modules.auth.dto.RegisterDTO;
import cn.minims.desk.modules.auth.vo.LoginVO;
import cn.minims.desk.modules.auth.vo.RegisterConfigVO;
import cn.minims.desk.modules.auth.vo.SessionVO;
import cn.minims.desk.modules.auth.vo.UserVO;
import cn.minims.desk.modules.system.branding.service.BrandingService;
import cn.minims.desk.modules.system.mail.service.MailConfigService;
import cn.minims.desk.modules.system.mail.service.MailService;
import cn.minims.desk.modules.system.mapper.SysConfigMapper;
import cn.minims.desk.modules.user.entity.SysUserDO;
import cn.minims.desk.modules.user.mapper.AuthzMapper;
import cn.minims.desk.modules.user.mapper.SysUserMapper;
import cn.minims.desk.modules.user.mapper.UserInitMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 认证与注册。
 *
 * <p>登录 / 注册发生时没有登录态，所有 DB 访问必须包在 TenantContext.ignore 内，
 * 否则租户插件取不到 userId 会直接抛异常。
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final DateTimeFormatter DATETIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int MAX_FAIL_TIMES = 5;

    private final SysUserMapper userMapper;
    private final SysConfigMapper configMapper;
    private final AuthzMapper authzMapper;
    private final UserInitMapper initMapper;
    private final EmailCodeService emailCodeService;
    private final MailService mailService;
    private final MailConfigService mailConfigService;
    private final BrandingService brandingService;

    /** 登录失败计数：5 次 / 15 分钟窗口，成功即清零 */
    private final Cache<String, Integer> loginFailCache = Caffeine.newBuilder()
            .maximumSize(2_000)
            .expireAfterWrite(Duration.ofMinutes(15))
            .build();

    @Value("${minidesk.register.claim-token:}")
    private String claimToken;

    public AuthService(SysUserMapper userMapper,
                       SysConfigMapper configMapper,
                       AuthzMapper authzMapper,
                       UserInitMapper initMapper,
                       EmailCodeService emailCodeService,
                       MailService mailService,
                       MailConfigService mailConfigService,
                       BrandingService brandingService) {
        this.userMapper = userMapper;
        this.configMapper = configMapper;
        this.authzMapper = authzMapper;
        this.initMapper = initMapper;
        this.emailCodeService = emailCodeService;
        this.mailService = mailService;
        this.mailConfigService = mailConfigService;
        this.brandingService = brandingService;
    }

    public LoginVO login(LoginDTO dto, String ip, String userAgent) {
        String username = dto.username().trim();

        Integer fails = loginFailCache.getIfPresent(username);
        if (fails != null && fails >= MAX_FAIL_TIMES) {
            throw new BizException(ErrorCode.LOGIN_LOCKED);
        }

        SysUserDO user = TenantContext.ignore(() -> findByAccount(username));
        if (user == null || !BCrypt.checkpw(dto.password(), user.getPasswordHash())) {
            loginFailCache.put(username, fails == null ? 1 : fails + 1);
            recordLogin(null, username, false, "用户名或密码错误", ip, userAgent);
            throw new BizException(ErrorCode.BAD_CREDENTIALS);
        }

        if (user.getStatus() != null && user.getStatus() == 0) {
            recordLogin(user.getId(), username, false, "账号已禁用", ip, userAgent);
            throw new BizException(ErrorCode.ACCOUNT_DISABLED);
        }

        loginFailCache.invalidate(username);
        touchLogin(user, ip);
        recordLogin(user.getId(), username, true, null, ip, userAgent);

        TenantContext.ignore(() -> {
            StpUtil.login(user.getId());
            return null;
        });

        log.info("user login, userId={}, username={}, ip={}", user.getId(), username, ip);
        return buildLoginVO(user, false);
    }

    @Transactional(rollbackFor = Exception.class)
    public LoginVO register(RegisterDTO dto, String ip, String userAgent) {
        if (!isRegisterAllowed()) {
            throw new BizException(ErrorCode.REGISTER_CLOSED);
        }
        if (!dto.password().equals(dto.confirmPassword())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "两次输入的密码不一致");
        }

        String username = dto.username().trim();
        String now = LocalDateTime.now().format(DATETIME);

        return TenantContext.ignore(() -> {
            if (findByUsername(username) != null) {
                throw new BizException(ErrorCode.USERNAME_EXISTS);
            }

            boolean firstUser = userMapper.selectCount(null) == 0;
            boolean grantedAdmin = firstUser && configMapper.claimInstall() == 1;

            if (grantedAdmin && StrUtil.isNotBlank(claimToken)
                    && !claimToken.equals(StrUtil.trim(dto.claimToken()))) {
                throw new BizException(ErrorCode.CLAIM_TOKEN_INVALID);
            }

            SysUserDO user = new SysUserDO();
            user.setUsername(username);
            user.setPasswordHash(BCrypt.hashpw(dto.password(), BCrypt.gensalt()));
            user.setNickname(StrUtil.isBlank(dto.nickname()) ? "朋友" : dto.nickname().trim());
            user.setStatus(1);
            user.setSource("self");
            user.setPwdResetRequired(0);
            user.setLoginCount(1);
            user.setLastLoginAt(LocalDateTime.now());
            user.setLastLoginIp(ip);
            user.setCreatedAt(LocalDateTime.now());
            user.setUpdatedAt(LocalDateTime.now());
            userMapper.insert(user);

            String roleCode = grantedAdmin ? "ADMIN" : "MEMBER";
            Long roleId = authzMapper.selectRoleIdByCode(roleCode);
            initMapper.insertUserRole(user.getId(), roleId);
            initMapper.insertDefaultSetting(user.getId(), now);
            recordLogin(user.getId(), username, true, null, ip, userAgent);

            StpUtil.login(user.getId());
            log.info("user registered, userId={}, username={}, role={}", user.getId(), username, roleCode);
            return buildLoginVO(user, grantedAdmin);
        });
    }

    public RegisterConfigVO registerConfig() {
        return TenantContext.ignore(() -> {
            boolean allow = isRegisterAllowed();
            boolean firstUser = userMapper.selectCount(null) == 0;
            boolean needClaim = firstUser && StrUtil.isNotBlank(claimToken);
            return new RegisterConfigVO(
                    allow,
                    firstUser,
                    needClaim,
                    mailConfigService.isReady(),
                    new RegisterConfigVO.PasswordPolicy(8, true, true));
        });
    }

    public void logout() {
        StpUtil.logout();
    }

    public void renew() {
        StpUtil.renewTimeout(StpUtil.getTokenTimeout());
    }

    public UserVO currentUser() {
        long userId = TenantContext.requireUserId();
        SysUserDO user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ErrorCode.ACCOUNT_NOT_FOUND);
        }
        return toUserVO(user);
    }

    /**
     * 刷新页面后恢复会话：token 存在但内存中的用户态已丢失，前端用它回填。
     */
    public SessionVO currentSession() {
        long userId = TenantContext.requireUserId();
        SysUserDO user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ErrorCode.ACCOUNT_NOT_FOUND);
        }
        return new SessionVO(
                toUserVO(user),
                authzMapper.selectRoleCodes(userId),
                authzMapper.selectPermissionCodes(userId));
    }

    private boolean isRegisterAllowed() {
        String value = configMapper.selectValue("allow_register");
        return !"false".equalsIgnoreCase(value);
    }

    private SysUserDO findByUsername(String username) {
        return userMapper.selectOne(Wrappers.<SysUserDO>lambdaQuery()
                .eq(SysUserDO::getUsername, username)
                .last("limit 1"));
    }

    /** 登录支持用户名或邮箱，用户不必记两套账号 */
    private SysUserDO findByAccount(String account) {
        SysUserDO byName = findByUsername(account);
        if (byName != null) {
            return byName;
        }
        return userMapper.selectOne(Wrappers.<SysUserDO>lambdaQuery()
                .eq(SysUserDO::getEmail, account.toLowerCase(Locale.ROOT))
                .last("limit 1"));
    }

    private SysUserDO findByEmail(String email) {
        return userMapper.selectOne(Wrappers.<SysUserDO>lambdaQuery()
                .eq(SysUserDO::getEmail, email)
                .last("limit 1"));
    }

    /** 发注册验证码；未配置发件邮箱时直接给出明确错误 */
    public void sendRegisterCode(String email) {
        if (!isRegisterAllowed()) {
            throw new BizException(ErrorCode.REGISTER_CLOSED);
        }
        String normalized = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        if (normalized.isBlank() || !normalized.contains("@")) {
            throw new BizException(ErrorCode.PARAM_ERROR, "邮箱格式不正确");
        }
        SysUserDO exists = TenantContext.ignore(() -> findByEmail(normalized));
        if (exists != null) {
            throw new BizException(ErrorCode.EMAIL_EXISTS);
        }
        String code = emailCodeService.issue(normalized);
        mailService.sendCode(normalized, code, brandingService.site().siteName());
        log.info("register code sent, email={}", normalized);
    }

    /**
     * 邮箱注册：用邮箱作为身份，用户名自动从邮箱前缀生成（冲突时加序号）。
     */
    @Transactional(rollbackFor = Exception.class)
    public LoginVO registerByEmail(EmailRegisterDTO dto, String ip, String userAgent) {
        if (!isRegisterAllowed()) {
            throw new BizException(ErrorCode.REGISTER_CLOSED);
        }
        if (!dto.password().equals(dto.confirmPassword())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "两次输入的密码不一致");
        }
        String email = dto.email().trim().toLowerCase(Locale.ROOT);

        return TenantContext.ignore(() -> {
            if (findByEmail(email) != null) {
                throw new BizException(ErrorCode.EMAIL_EXISTS);
            }
            emailCodeService.verify(email, dto.code());

            boolean firstUser = userMapper.selectCount(null) == 0;
            boolean grantedAdmin = firstUser && configMapper.claimInstall() == 1;

            SysUserDO user = new SysUserDO();
            user.setUsername(uniqueUsername(email));
            user.setEmail(email);
            user.setPasswordHash(BCrypt.hashpw(dto.password(), BCrypt.gensalt()));
            user.setNickname(StrUtil.isBlank(dto.nickname()) ? email.substring(0, email.indexOf('@')) : dto.nickname().trim());
            user.setStatus(1);
            user.setSource("self");
            user.setPwdResetRequired(0);
            user.setLoginCount(1);
            user.setLastLoginAt(LocalDateTime.now());
            user.setLastLoginIp(ip);
            user.setCreatedAt(LocalDateTime.now());
            user.setUpdatedAt(LocalDateTime.now());
            userMapper.insert(user);

            String roleCode = grantedAdmin ? "ADMIN" : "MEMBER";
            Long roleId = authzMapper.selectRoleIdByCode(roleCode);
            initMapper.insertUserRole(user.getId(), roleId);
            initMapper.insertDefaultSetting(user.getId(), LocalDateTime.now().format(DATETIME));

            StpUtil.login(user.getId());
            log.info("user registered by email, userId={}, email={}", user.getId(), email);
            return buildLoginVO(user, grantedAdmin);
        });
    }

    /** 由邮箱前缀生成用户名，保证 3-32 位且不重复 */
    private String uniqueUsername(String email) {
        String base = email.substring(0, email.indexOf('@'))
                .replaceAll("[^a-zA-Z0-9_]", "_");
        if (base.length() < 3) {
            base = "u_" + base;
        }
        if (base.length() > 28) {
            base = base.substring(0, 28);
        }
        String candidate = base;
        int suffix = 1;
        while (findByUsername(candidate) != null) {
            candidate = base + "_" + suffix++;
        }
        return candidate;
    }

    private void touchLogin(SysUserDO user, String ip) {
        SysUserDO update = new SysUserDO();
        update.setId(user.getId());
        update.setLastLoginAt(LocalDateTime.now());
        update.setLastLoginIp(ip);
        update.setLoginCount((user.getLoginCount() == null ? 0 : user.getLoginCount()) + 1);
        update.setUpdatedAt(LocalDateTime.now());
        TenantContext.ignore(() -> userMapper.updateById(update));
    }

    private void recordLogin(Long userId, String username, boolean success, String reason, String ip, String agent) {
        try {
            initMapper.insertLoginLog(userId, username, success ? 1 : 0, reason, ip, agent,
                    LocalDateTime.now().format(DATETIME));
        } catch (RuntimeException ex) {
            // 日志失败不能影响主流程
            log.warn("write login log failed, username={}", username, ex);
        }
    }

    private LoginVO buildLoginVO(SysUserDO user, boolean firstUser) {
        SaTokenInfo tokenInfo = StpUtil.getTokenInfo();
        List<String> roles = authzMapper.selectRoleCodes(user.getId());
        List<String> permissions = authzMapper.selectPermissionCodes(user.getId());
        return new LoginVO(
                StpUtil.getTokenName(),
                tokenInfo.getTokenValue(),
                StpUtil.getTokenTimeout(),
                toUserVO(user),
                roles,
                permissions,
                firstUser);
    }

    private UserVO toUserVO(SysUserDO user) {
        String avatarUrl = user.getAvatarFileId() == null ? null : "/api/files/" + user.getAvatarFileId() + "/raw";
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
