package cn.minims.desk.modules.system.mapper;

import cn.minims.desk.modules.system.entity.SysConfigDO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface SysConfigMapper extends BaseMapper<SysConfigDO> {

    @Select("select config_value from sys_config where config_key = #{key}")
    String selectValue(@Param("key") String key);

    /**
     * 抢占式领取首个管理员：影响行数为 1 才代表抢到，天然防并发。
     */
    @Update("update sys_config set config_value = 'true' where config_key = 'install_claimed' and config_value = 'false'")
    int claimInstall();

    @Update("update sys_config set config_value = #{value}, updated_at = #{now} where config_key = #{key}")
    int updateValue(@Param("key") String key, @Param("value") String value, @Param("now") String now);
}
