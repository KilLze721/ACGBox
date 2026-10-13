package org.killze.acgbox.controller;

import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.killze.acgbox.dto.user.LoginDTO;
import org.killze.acgbox.result.Result;
import org.killze.acgbox.service.LoginService;
import org.killze.acgbox.vo.user.LoginVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端登录控制器。
 */
@RestController
@RequestMapping("/admin/login")
@Slf4j
public class LoginController {

    @Autowired
    private LoginService loginService;

    /**
     * 用户名密码登录。
     */
    @PostMapping("/username")
    public Result<LoginVO> loginByUsername(@Valid @RequestBody LoginDTO loginDTO) {
        log.info("管理端用户名密码登录");
        return Result.success(loginService.loginByUsername(loginDTO));
    }

    /**
     * 邮箱密码登录。
     */
    @PostMapping("/email-password")
    public Result<LoginVO> loginByEmail(@Valid @RequestBody LoginDTO loginDTO) {
        log.info("管理端邮箱密码登录");
        return Result.success(loginService.loginByEmail(loginDTO));
    }
}
