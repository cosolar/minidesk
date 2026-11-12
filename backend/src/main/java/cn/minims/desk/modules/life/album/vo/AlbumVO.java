package cn.minims.desk.modules.life.album.vo;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 相册与照片展示对象。
 */
public record AlbumVO(
        Long id,
        String name,
        String description,
        String coverUrl,
        long photoCount,
        LocalDateTime createdAt,
        List<PhotoVO> photos
) {
    public record PhotoVO(
            Long id,
            Long albumId,
            String title,
            String url,
            Long fileId,
            LocalDate takenAt
    ) {
    }
}
