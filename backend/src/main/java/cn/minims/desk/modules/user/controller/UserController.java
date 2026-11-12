package cn.minims.desk.modules.user.controller;

import cn.minims.desk.common.api.Result;
import cn.minims.desk.modules.auth.service.AuthService;
import cn.minims.desk.modules.auth.vo.SessionVO;
import cn.minims.desk.modules.auth.vo.UserVO;
import cn.minims.desk.modules.user.dto.PasswordChangeDTO;
import cn.minims.desk.modules.user.dto.ProfileUpdateDTO;
import cn.minims.desk.modules.user.service.UserService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 当前账号信息与个人中心。
 */
@RestController
@RequestMapping("/api/user")
public class UserController {

    private final AuthService authService;
    private final UserService userService;

    public UserController(AuthService authService, UserService userService) {
        this.authService = authService;
        this.userService = userService;
    }

    @GetMapping("/me")
    public Result<UserVO> me() {
        return Result.ok(authService.currentUser());
    }

    /** 刷新页面后恢复登录态（含角色与权限码） */
    @GetMapping("/session")
    public Result<SessionVO> session() {
        return Result.ok(authService.currentSession());
    }

    @GetMapping("/profile")
    public Result<UserVO> profile() {
        return Result.ok(userService.profile());
    }

    @PutMapping("/profile")
    public Result<UserVO> updateProfile(@Valid @RequestBody ProfileUpdateDTO dto) {
        return Result.ok(userService.updateProfile(dto));
    }

    @PutMapping("/password")
    public Result<Void> changePassword(@Valid @RequestBody PasswordChangeDTO dto) {
        userService.changePassword(dto);
        return Result.ok();
    }

    @PostMapping("/avatar")
    public Result<UserVO> uploadAvatar(@RequestParam("file") MultipartFile file) {
        return Result.ok(userService.updateAvatar(file));
    }
}
