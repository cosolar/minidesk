package cn.minims.desk.modules.auth.service;

import cn.minims.desk.common.api.ErrorCode;
import cn.minims.desk.common.exception.BizException;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import org.springframework.stereotype.Service;

/**
 * 邮箱验证码。
 *
 * <p>放内存缓存而不是数据库：验证码 10 分钟就失效，落库只会产生大量垃圾行；
 * 单机部署下重启丢失可接受（用户重新获取即可）。
 */
@Service
public class EmailCodeService {

    private static final Duration TTL = Duration.ofMinutes(10);
    private static final Duration RESEND = Duration.ofSeconds(60);

    private final Cache<String, String> codes = Caffeine.newBuilder()
            .maximumSize(2_000)
            .expireAfterWrite(TTL)
            .build();

    private final Cache<String, Long> lastSent = Caffeine.newBuilder()
            .maximumSize(2_000)
            .expireAfterWrite(RESEND)
            .build();

    /** 生成并缓存验证码；60 秒内重复请求直接拒绝，避免被当作邮件轰炸工具 */
    public String issue(String email) {
        String key = normalize(email);
        if (lastSent.getIfPresent(key) != null) {
            throw new BizException(ErrorCode.EMAIL_CODE_LIMIT);
        }
        String code = String.valueOf((int) ((Math.random() * 900000) + 100000));
        codes.put(key, code);
        lastSent.put(key, System.currentTimeMillis());
        return code;
    }

    public void verify(String email, String code) {
        String key = normalize(email);
        String cached = codes.getIfPresent(key);
        if (cached == null || code == null || !cached.equals(code.trim())) {
            throw new BizException(ErrorCode.EMAIL_CODE_INVALID);
        }
        codes.invalidate(key);
    }

    private String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }
}
