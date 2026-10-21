package org.killze.acgbox.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.killze.acgbox.result.Result;
import org.killze.acgbox.service.LoginService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端退出登录控制器。
 */
@RestController
@RequestMapping("/admin")
@Slf4j
public class LogoutController {

    @Autowired
    private LoginService loginService;

    private final SecurityContextLogoutHandler logoutHandler = new SecurityContextLogoutHandler();

    /**
     * 退出当前账号的全部管理端登录，并清理当前请求的认证状态。
     *
     * @param jwt 当前登录令牌
     * @param authentication 当前认证信息
     * @param request HTTP 请求
     * @param response HTTP 响应
     * @return 操作结果
     */
    @PostMapping("/logout")
    public Result<Void> logout(@AuthenticationPrincipal Jwt jwt, Authentication authentication,
                               HttpServletRequest request, HttpServletResponse response) {
        Long id = Long.valueOf(jwt.getSubject());
        Long tokenVersion = ((Number) jwt.getClaim("token_version")).longValue();
        loginService.logout(id, tokenVersion);
        logoutHandler.logout(request, response, authentication);
        log.info("管理端退出登录，账号 ID：{}", id);
        return Result.success();
    }
}
