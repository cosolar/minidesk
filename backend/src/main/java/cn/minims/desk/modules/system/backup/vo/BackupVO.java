package cn.minims.desk.modules.system.backup.vo;

/** 备份与恢复相关的视图对象。 */
public final class BackupVO {

    private BackupVO() {
    }

    public record BackupFile(
            String fileName,
            long size,
            String sizeText,
            String triggerBy,
            String status,
            String createdAt
    ) {
    }

    public record RestoreResult(String status, String message) {
    }

    public record RestoreStatus(boolean pending, String fileName, String queuedAt, String lastResult) {
    }
}
