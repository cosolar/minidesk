package cn.minims.desk.modules.common.calendar.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.minims.desk.common.api.Result;
import cn.minims.desk.framework.satoken.PermissionCodes;
import cn.minims.desk.modules.common.calendar.dto.CalendarEventSaveDTO;
import cn.minims.desk.modules.common.calendar.service.CalendarService;
import cn.minims.desk.modules.common.calendar.vo.CalendarItemVO;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 日历聚合与日程事件接口，见 docs/接口清单.md §6。
 */
@RestController
@RequestMapping("/api/calendar")
public class CalendarController {

    private final CalendarService calendarService;

    public CalendarController(CalendarService calendarService) {
        this.calendarService = calendarService;
    }

    @GetMapping("/events")
    public Result<List<CalendarItemVO>> events(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        return Result.ok(calendarService.range(start, end));
    }

    @PostMapping("/events")
    @SaCheckPermission(PermissionCodes.SHARED_CALENDAR_MANAGE)
    public Result<Long> createEvent(@Valid @RequestBody CalendarEventSaveDTO dto) {
        return Result.ok(calendarService.createEvent(dto));
    }

    @PutMapping("/events/{id}")
    @SaCheckPermission(PermissionCodes.SHARED_CALENDAR_MANAGE)
    public Result<Void> updateEvent(@PathVariable Long id, @Valid @RequestBody CalendarEventSaveDTO dto) {
        calendarService.updateEvent(id, dto);
        return Result.ok();
    }

    @DeleteMapping("/events/{id}")
    @SaCheckPermission(PermissionCodes.SHARED_CALENDAR_MANAGE)
    public Result<Void> deleteEvent(@PathVariable Long id) {
        calendarService.deleteEvent(id);
        return Result.ok();
    }
}
