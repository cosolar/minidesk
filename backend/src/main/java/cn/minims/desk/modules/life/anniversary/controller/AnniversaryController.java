package cn.minims.desk.modules.life.anniversary.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.minims.desk.common.api.Result;
import cn.minims.desk.framework.satoken.PermissionCodes;
import cn.minims.desk.modules.life.anniversary.service.AnniversaryService;
import cn.minims.desk.modules.life.anniversary.vo.AnniversaryVO;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 纪念日与倒数日接口，见 docs/接口清单.md §9。
 */
@RestController
@RequestMapping("/api")
public class AnniversaryController {

    private final AnniversaryService anniversaryService;

    public AnniversaryController(AnniversaryService anniversaryService) {
        this.anniversaryService = anniversaryService;
    }

    @GetMapping("/anniversaries")
    public Result<List<AnniversaryVO>> anniversaries() {
        return Result.ok(anniversaryService.listAnniversaries());
    }

    @PostMapping("/anniversaries")
    @SaCheckPermission(PermissionCodes.ANNIVERSARY_MANAGE)
    public Result<AnniversaryVO> createAnniversary(@RequestBody Map<String, Object> body) {
        String title = body.get("title") == null ? null : String.valueOf(body.get("title"));
        String type = body.get("type") == null ? null : String.valueOf(body.get("type"));
        String person = body.get("person") == null ? null : String.valueOf(body.get("person"));
        LocalDate date = body.get("date") == null ? null : LocalDate.parse(String.valueOf(body.get("date")));
        Integer advanceDays = body.get("advanceDays") == null
                ? null : Integer.valueOf(String.valueOf(body.get("advanceDays")));
        Boolean repeat = body.get("repeatYearly") == null
                ? null : Boolean.valueOf(String.valueOf(body.get("repeatYearly")));
        return Result.ok(anniversaryService.createAnniversary(title, type, person, date, advanceDays, repeat));
    }

    @DeleteMapping("/anniversaries/{id}")
    @SaCheckPermission(PermissionCodes.ANNIVERSARY_MANAGE)
    public Result<Void> deleteAnniversary(@PathVariable Long id) {
        anniversaryService.deleteAnniversary(id);
        return Result.ok();
    }

    @GetMapping("/countdowns")
    public Result<List<AnniversaryVO.CountdownVO>> countdowns() {
        return Result.ok(anniversaryService.listCountdowns());
    }

    @PostMapping("/countdowns")
    @SaCheckPermission(PermissionCodes.ANNIVERSARY_MANAGE)
    public Result<AnniversaryVO.CountdownVO> createCountdown(@RequestBody Map<String, Object> body) {
        String title = body.get("title") == null ? null : String.valueOf(body.get("title"));
        LocalDate target = body.get("targetDate") == null
                ? null : LocalDate.parse(String.valueOf(body.get("targetDate")));
        String type = body.get("type") == null ? null : String.valueOf(body.get("type"));
        String color = body.get("color") == null ? null : String.valueOf(body.get("color"));
        return Result.ok(anniversaryService.createCountdown(title, target, type, color));
    }

    @DeleteMapping("/countdowns/{id}")
    @SaCheckPermission(PermissionCodes.ANNIVERSARY_MANAGE)
    public Result<Void> deleteCountdown(@PathVariable Long id) {
        anniversaryService.deleteCountdown(id);
        return Result.ok();
    }
}
