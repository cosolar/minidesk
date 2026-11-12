package cn.minims.desk.modules.auth.controller;

import cn.minims.desk.common.api.Result;
import cn.minims.desk.modules.auth.dto.EmailRegisterDTO;
import cn.minims.desk.modules.auth.dto.LoginDTO;
import cn.minims.desk.modules.auth.dto.RegisterDTO;
import cn.minims.desk.modules.auth.service.AuthService;
import cn.minims.desk.modules.auth.vo.LoginVO;
import cn.minims.desk.modules.auth.vo.RegisterConfigVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证接口，见 docs/接口清单.md §2。
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody LoginDTO dto, HttpServletRequest request) {
        return Result.ok(authService.login(dto, resolveIp(request), request.getHeader("User-Agent")));
    }

    @PostMapping("/register")
    public Result<LoginVO> register(@Valid @RequestBody RegisterDTO dto, HttpServletRequest request) {
        return Result.ok(authService.register(dto, resolveIp(request), request.getHeader("User-Agent")));
    }

    /** 发送邮箱验证码（注册用） */
    @PostMapping("/email-code")
    public Result<Void> emailCode(@RequestBody Map<String, String> body) {
        authService.sendRegisterCode(body.get("email"));
        return Result.ok();
    }

    @PostMapping("/register-email")
    public Result<LoginVO> registerByEmail(@Valid @RequestBody EmailRegisterDTO dto, HttpServletRequest request) {
        return Result.ok(authService.registerByEmail(dto, resolveIp(request), request.getHeader("User-Agent")));
    }

    @GetMapping("/register-config")
    public Result<RegisterConfigVO> registerConfig() {
        return Result.ok(authService.registerConfig());
    }

    @PostMapping("/logout")
    public Result<Void> logout() {
        authService.logout();
        return Result.ok();
    }

    @PostMapping("/renew")
    public Result<Void> renew() {
        authService.renew();
        return Result.ok();
    }

    /** 取真实客户端 IP，兼容反向代理 */
    private String resolveIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwarded)) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
