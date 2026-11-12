package cn.minims.desk.modules.study.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.minims.desk.common.api.Result;
import cn.minims.desk.framework.satoken.PermissionCodes;
import cn.minims.desk.modules.study.entity.StudyRecordDO;
import cn.minims.desk.modules.study.service.StudyService;
import cn.minims.desk.modules.study.vo.StudyWeekVO;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 学习记录与统计接口，见 docs/接口清单.md §8。
 */
@RestController
@RequestMapping("/api/study")
public class StudyController {

    private final StudyService studyService;

    public StudyController(StudyService studyService) {
        this.studyService = studyService;
    }

    @GetMapping("/stats/week")
    public Result<StudyWeekVO> week() {
        return Result.ok(studyService.weekStats());
    }

    @GetMapping("/records")
    public Result<List<StudyRecordDO>> records(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        return Result.ok(studyService.range(start, end));
    }

    @PostMapping("/records")
    @SaCheckPermission(PermissionCodes.STUDY_RECORD_ADD)
    public Result<StudyRecordDO> add(@RequestBody Map<String, Object> body) {
        String subject = body.get("subject") == null ? null : String.valueOf(body.get("subject"));
        Integer minutes = body.get("minutes") == null ? null : Integer.valueOf(String.valueOf(body.get("minutes")));
        LocalDate date = body.get("studyDate") == null
                ? null : LocalDate.parse(String.valueOf(body.get("studyDate")));
        Long planId = body.get("planId") == null ? null : Long.valueOf(String.valueOf(body.get("planId")));
        String note = body.get("note") == null ? null : String.valueOf(body.get("note"));
        return Result.ok(studyService.add(subject, minutes, date, planId, note));
    }
}
