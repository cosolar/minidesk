package cn.minims.desk.modules.drive.vo;

/** 存储配置回显：密钥只给掩码，永不返回明文。 */
public record DriveConfigVO(
        String storageType,
        String endpoint,
        String bucket,
        String region,
        String accessKey,
        String maskedSecret,
        boolean hasSecret,
        boolean pathStyle,
        String prefix,
        String publicUrl,
        String acl,
        boolean ready
) {
}
