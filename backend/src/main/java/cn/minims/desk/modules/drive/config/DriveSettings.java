package cn.minims.desk.modules.drive.config;

import org.springframework.util.StringUtils;

/**
 * 网盘存储配置的运行期视图（含明文密钥，只在服务端内存里流转）。
 */
public record DriveSettings(
        String storageType,
        String endpoint,
        String bucket,
        String region,
        String accessKey,
        String secretKey,
        boolean pathStyle,
        String prefix,
        String publicUrl,
        String acl
) {

    public static DriveSettings local() {
        return new DriveSettings("local", "", "", "us-east-1", "", "", true, "", "", "");
    }

    public boolean isS3() {
        return "s3".equalsIgnoreCase(storageType);
    }

    public boolean ready() {
        if (!isS3()) {
            return true;
        }
        return StringUtils.hasText(endpoint) && StringUtils.hasText(bucket)
                && StringUtils.hasText(accessKey) && StringUtils.hasText(secretKey);
    }

    /** 对象键前缀：统一成以 / 结尾或空串，避免出现双斜杠 */
    public String normalizedPrefix() {
        if (!StringUtils.hasText(prefix)) {
            return "";
        }
        String value = prefix.trim().replaceAll("^/+", "");
        return value.endsWith("/") ? value : value + "/";
    }

    public String fullKey(String objectKey) {
        return normalizedPrefix() + objectKey;
    }
}
