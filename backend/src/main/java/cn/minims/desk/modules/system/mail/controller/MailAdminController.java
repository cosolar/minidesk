package cn.minims.desk.modules.system.mail.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.minims.desk.common.api.Result;
import cn.minims.desk.modules.system.mail.service.MailConfigService;
import cn.minims.desk.modules.system.mail.service.MailService;
import cn.minims.desk.modules.system.mail.vo.MailConfigVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 后台发件邮箱配置。
 */
@RestController
@RequestMapping("/api/console/mail")
@SaCheckRole("ADMIN")
public class MailAdminController {

    private final MailConfigService configService;
    private final MailService mailService;

    public MailAdminController(MailConfigService configService, MailService mailService) {
        this.configService = configService;
        this.mailService = mailService;
    }

    @GetMapping
    public Result<MailConfigVO> get() {
        return Result.ok(configService.get());
    }

    @PutMapping
    public Result<MailConfigVO> save(@RequestBody MailConfigVO body) {
        return Result.ok(configService.save(body));
    }

    @PostMapping("/test")
    public Result<Void> test(@RequestParam String to) {
        mailService.sendTest(to, "MiniDesk");
        return Result.ok();
    }
}
