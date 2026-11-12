package cn.minims.desk.modules.study.plan.mapper;

import cn.minims.desk.modules.study.plan.entity.StudyPlanDO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Select;

public interface StudyPlanMapper extends BaseMapper<StudyPlanDO> {

    /** 各计划下的任务完成情况，一次查完避免递归查询 */
    @Select("""
            select plan_id as planId,
                   count(*) as total,
                   sum(case when status = 'done' then 1 else 0 end) as done
            from task
            where deleted = 0 and plan_id is not null
            group by plan_id
            """)
    List<Map<String, Object>> countTasksByPlan();
}
