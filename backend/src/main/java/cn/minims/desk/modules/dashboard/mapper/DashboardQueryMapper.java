package cn.minims.desk.modules.dashboard.mapper;

import org.apache.ibatis.annotations.Select;

/**
 * 首页计数类查询（只做聚合，不读业务正文）。
 */
public interface DashboardQueryMapper {

    @Select("select count(*) from notification where read_flag = 0")
    long countUnreadNotifications();

    @Select("select count(*) from project where status = 'doing' and deleted = 0")
    long countDoingProjects();
}
