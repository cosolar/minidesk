package cn.minims.desk.modules.common.reminder.controller;

import cn.minims.desk.common.api.Result;
import cn.minims.desk.modules.common.reminder.service.ReminderService;
import cn.minims.desk.modules.common.reminder.vo.ReminderVO;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 统一提醒中心：任务截止、生日、纪念日、倒数日、学习计划。
 */
@RestController
@RequestMapping("/api/reminders")
public class ReminderController {

    private final ReminderService reminderService;

    public ReminderController(ReminderService reminderService) {
        this.reminderService = reminderService;
    }

    @GetMapping
    public Result<List<ReminderVO>> list() {
        return Result.ok(reminderService.list());
    }
}
