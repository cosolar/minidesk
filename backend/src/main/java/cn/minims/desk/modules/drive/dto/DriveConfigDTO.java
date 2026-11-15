package cn.minims.desk.modules.drive.dto;

/** 存储配置入参。secretKey 传回掩码时表示不修改。 */
public record DriveConfigDTO(
        String storageType,
        String endpoint,
        String bucket,
        String region,
        String accessKey,
        String secretKey,
        Boolean pathStyle,
        String prefix,
        String publicUrl,
        String acl
) {
}
