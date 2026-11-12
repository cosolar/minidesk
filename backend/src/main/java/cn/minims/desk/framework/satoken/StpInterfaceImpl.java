package cn.minims.desk.framework.satoken;

import cn.dev33.satoken.stp.StpInterface;
import cn.minims.desk.modules.user.mapper.AuthzMapper;
import java.util.List;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

/**
 * Sa-Token 权限数据源。
 * 结果缓存 10 分钟，角色变更需主动 evict（见 AuthService#evictAuthz）。
 */
@Component
public class StpInterfaceImpl implements StpInterface {

    private final AuthzMapper authzMapper;

    public StpInterfaceImpl(AuthzMapper authzMapper) {
        this.authzMapper = authzMapper;
    }

    @Override
    @Cacheable(cacheNames = "authz-permission", key = "#loginId")
    public List<String> getPermissionList(Object loginId, String loginType) {
        return authzMapper.selectPermissionCodes(Long.parseLong(loginId.toString()));
    }

    @Override
    @Cacheable(cacheNames = "authz-role", key = "#loginId")
    public List<String> getRoleList(Object loginId, String loginType) {
        return authzMapper.selectRoleCodes(Long.parseLong(loginId.toString()));
    }
}
