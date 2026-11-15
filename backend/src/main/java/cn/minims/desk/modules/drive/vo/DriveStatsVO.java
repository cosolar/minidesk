package cn.minims.desk.modules.drive.vo;

/** 网盘用量概览。capacityBytes = 0 表示对象存储不提供容量信息。 */
public record DriveStatsVO(
        int fileCount,
        int folderCount,
        long usedBytes,
        long capacityBytes,
        int imageCount,
        int documentCount,
        int otherCount,
        String storageType,
        String storageLabel,
        boolean ready
) {
}
