package cn.minims.desk.modules.work.docasset.vo;

import cn.minims.desk.modules.work.docasset.entity.DocAssetDO;
import java.time.LocalDateTime;

/**
 * 文档资产展示对象。列表不返回正文，详情才带 content。
 */
public record DocAssetVO(
        Long id,
        String title,
        String docType,
        String provider,
        String url,
        String content,
        Long fileId,
        String category,
        String tags,
        String description,
        boolean pinned,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static DocAssetVO from(DocAssetDO item, boolean withContent) {
        return new DocAssetVO(
                item.getId(),
                item.getTitle(),
                item.getDocType(),
                item.getProvider(),
                item.getUrl(),
                withContent ? item.getContent() : null,
                item.getFileId(),
                item.getCategory(),
                item.getTags(),
                item.getDescription(),
                item.getPinned() != null && item.getPinned() == 1,
                item.getCreatedAt(),
                item.getUpdatedAt());
    }
}
