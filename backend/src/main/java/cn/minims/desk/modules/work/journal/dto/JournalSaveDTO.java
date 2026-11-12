package cn.minims.desk.modules.work.journal.dto;

import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * 日志保存入参。周期由 service 按类型推导，前端只需给一个「基准日期」。
 */
public record JournalSaveDTO(
        String journalType,

        @Size(max = 200, message = "标题过长")
        String title,

        LocalDate date,

        String templateKey,

        String content
) {
}
