package cn.minims.desk.modules.work.project.mapper;

import cn.minims.desk.modules.work.project.entity.ProjectDO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface ProjectMapper extends BaseMapper<ProjectDO> {

    /** 各状态任务数：一次查完，避免按项目 N+1 */
    @Select("""
            select project_id as projectId, status, count(*) as cnt
            from task
            where deleted = 0 and project_id is not null
            group by project_id, status
            """)
    List<Map<String, Object>> countTasksByStatus();

    /** 今天由调用方传入，避免在 SQL 里写 SQLite 专属的 date('now')。 */
    @Select("""
            select count(*) from task
            where deleted = 0 and project_id = #{projectId}
              and status <> 'done' and due_date is not null and due_date < #{today}
            """)
    long countOverdue(@Param("projectId") Long projectId, @Param("today") LocalDate today);
}
