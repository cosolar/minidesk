package cn.minims.desk.modules.life.album.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * 相册 / 照片保存入参。
 */
public record AlbumSaveDTO(
        @NotBlank(message = "请输入相册名")
        @Size(max = 120, message = "相册名过长")
        String name,

        @Size(max = 500, message = "描述过长")
        String description
) {
    public record PhotoSaveDTO(
            String title,
            String url,
            Long fileId,
            LocalDate takenAt
    ) {
    }
}
