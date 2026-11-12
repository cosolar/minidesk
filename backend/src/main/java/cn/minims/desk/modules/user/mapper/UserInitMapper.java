package cn.minims.desk.modules.user.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 新账号初始化数据（与用户创建同事务）。
 */
@Mapper
public interface UserInitMapper {

    @Insert("insert into sys_user_role (user_id, role_id) values (#{userId}, #{roleId})")
    int insertUserRole(@Param("userId") Long userId, @Param("roleId") Long roleId);

    @Insert("""
            insert into sys_user_setting
                (user_id, theme, sidebar_collapsed, city, week_start, created_at, updated_at)
            values
                (#{userId}, 'auto', 0, '北京', 1, #{now}, #{now})
            """)
    int insertDefaultSetting(@Param("userId") Long userId, @Param("now") String now);

    @Insert("""
            insert into sys_login_log (user_id, username, success, fail_reason, ip, user_agent, created_at)
            values (#{userId}, #{username}, #{success}, #{failReason}, #{ip}, #{userAgent}, #{now})
            """)
    int insertLoginLog(@Param("userId") Long userId,
                       @Param("username") String username,
                       @Param("success") int success,
                       @Param("failReason") String failReason,
                       @Param("ip") String ip,
                       @Param("userAgent") String userAgent,
                       @Param("now") String now);
}
