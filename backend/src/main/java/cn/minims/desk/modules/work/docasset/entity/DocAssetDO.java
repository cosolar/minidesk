package cn.minims.desk.modules.work.docasset.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 文档资产：三类统一管理。
 *
 * <ul>
 *   <li>{@code link} —— 飞书 / 腾讯文档等外部链接，只存地址；</li>
 *   <li>{@code markdown} —— 系统内自建文档，正文存 content；</li>
 *   <li>{@code file} —— 上传的附件，正文在磁盘，这里存 file_id。</li>
 * </ul>
 *
 * 不把外部文档抓成本地副本：飞书文档权限、协作、版本都在对方系统里，
 * 抄一份过来只会变成过期数据。
 */
@Data
@TableName("doc_asset")
public class DocAssetDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String title;

    /** markdown / link / file */
    private String docType;

    /** feishu / tencent / yuque / other */
    private String provider;

    private String url;

    private String content;

    private Long fileId;

    private String category;

    private String tags;

    private String description;

    private Integer pinned;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
