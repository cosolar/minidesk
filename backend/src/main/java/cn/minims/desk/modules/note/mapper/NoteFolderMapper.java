package cn.minims.desk.modules.note.mapper;

import cn.minims.desk.modules.note.entity.NoteFolderDO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 笔记目录 Mapper。user_id 由租户插件自动注入，这里不手写租户条件。
 */
public interface NoteFolderMapper extends BaseMapper<NoteFolderDO> {

    /** 删除目录（含子孙目录）时批量软删；@TableLogic 只作用于内置方法 */
    @Update("""
            <script>
            update note_folder set deleted = 1, updated_at = #{at}
            where deleted = 0 and id in
            <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
            </script>
            """)
    int softDeleteBatch(@Param("ids") List<Long> ids, @Param("at") LocalDateTime at);
}
