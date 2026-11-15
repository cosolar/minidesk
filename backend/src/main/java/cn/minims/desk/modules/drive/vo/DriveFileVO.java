package cn.minims.desk.modules.drive.vo;

import java.time.LocalDateTime;

/** 网盘条目。link 为可直接打开的地址（S3 配了公链前缀时返回直链）。 */
public record DriveFileVO(
        Long id,
        Long parentId,
        String name,
        String type,
        long size,
        String mime,
        String storage,
        int childCount,
        String link,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
