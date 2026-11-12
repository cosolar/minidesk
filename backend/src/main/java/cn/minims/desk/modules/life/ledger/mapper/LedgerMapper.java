package cn.minims.desk.modules.life.ledger.mapper;

import cn.minims.desk.modules.life.ledger.entity.LedgerCategoryDO;
import cn.minims.desk.modules.life.ledger.entity.LedgerDO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface LedgerMapper extends BaseMapper<LedgerDO> {

    /** 月度分类汇总 */
    @Select("""
            select category_id as categoryId, type, sum(amount_cents) as amountCents
            from ledger
            where deleted = 0 and occur_date >= #{start} and occur_date <= #{end}
            group by category_id, type
            """)
    List<Map<String, Object>> sumByCategory(@Param("start") String start, @Param("end") String end);
}
