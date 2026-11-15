package cn.minims.desk.modules.note.mapper;

import cn.minims.desk.modules.note.entity.NoteDO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 笔记 Mapper。
 *
 * <p>回收站相关语句必须自定义：{@code @TableLogic} 只作用于内置方法，
 * 自定义 SQL 才能读到 deleted = 1 的行。user_id 仍由租户插件自动注入，
 * 这里不手写租户条件。
 */
public interface NoteMapper extends BaseMapper<NoteDO> {

    /** 软删除：写回收站时间，保留正文以便恢复 */
    @Update("update note set deleted = 1, deleted_at = #{at} where id = #{id} and deleted = 0")
    int moveToRecycle(@Param("id") Long id, @Param("at") LocalDateTime at);

    /** 回收站列表，最近删除的排前面 */
    @Select("""
            select id, module, type, title, content, tags, word_count,
                   pinned, created_at, updated_at, deleted_at
            from note
            where deleted = 1
            order by deleted_at desc, id desc
            limit 200
            """)
    List<NoteDO> selectRecycled();

    @Update("update note set deleted = 0, deleted_at = null where id = #{id} and deleted = 1")
    int restore(@Param("id") Long id);

    @Delete("delete from note where id = #{id} and deleted = 1")
    int purge(@Param("id") Long id);

    /** 清空当前账号的回收站 */
    @Delete("delete from note where deleted = 1")
    int purgeRecycled();
}
