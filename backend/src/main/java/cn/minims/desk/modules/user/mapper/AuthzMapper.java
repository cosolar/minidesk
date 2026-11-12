package cn.minims.desk.modules.user.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 授权查询（全局表，不受租户隔离影响）。
 */
public interface AuthzMapper {

    @Select("select id from sys_role where code = #{code} limit 1")
    Long selectRoleIdByCode(@Param("code") String code);

    @Select("""
            select distinct r.code
            from sys_role r
            join sys_user_role ur on ur.role_id = r.id
            where ur.user_id = #{userId}
            """)
    List<String> selectRoleCodes(@Param("userId") Long userId);

    @Select("""
            select distinct rp.permission_code
            from sys_role_permission rp
            join sys_user_role ur on ur.role_id = rp.role_id
            where ur.user_id = #{userId}
            """)
    List<String> selectPermissionCodes(@Param("userId") Long userId);
}
