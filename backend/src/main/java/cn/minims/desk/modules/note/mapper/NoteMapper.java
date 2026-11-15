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

    /** 侧栏文档树：全库文档的标题与归属，不带正文，一次取回也不重 */
    @Select("""
            select id, module, type, title, folder_id, word_count, pinned, updated_at
            from note
            where deleted = 0 and archived = 0
            order by pinned desc, updated_at desc
            """)
    List<NoteDO> selectOutline();

    /** 删除目录时把目录（含子孙）里的文档移入回收站，保留正文可恢复 */
    @Update("""
            <script>
            update note set deleted = 1, deleted_at = #{at}
            where deleted = 0 and folder_id in
            <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
            </script>
            """)
    int moveFoldersToRecycle(@Param("ids") List<Long> ids, @Param("at") LocalDateTime at);

    @Delete("delete from note where id = #{id} and deleted = 1")
    int purge(@Param("id") Long id);

    /** 清空当前账号的回收站 */
    @Delete("delete from note where deleted = 1")
    int purgeRecycled();
}
