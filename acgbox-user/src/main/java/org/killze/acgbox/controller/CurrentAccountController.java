package org.killze.acgbox.controller;

import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.killze.acgbox.dto.user.UserAccountChangePasswordDTO;
import org.killze.acgbox.dto.user.UserAccountEmailCodeDTO;
import org.killze.acgbox.dto.user.UserAccountEmailDTO;
import org.killze.acgbox.dto.user.UserAccountUnbindEmailDTO;
import org.killze.acgbox.dto.user.UserAccountVerifyEmailDTO;
import org.killze.acgbox.result.Result;
import org.killze.acgbox.service.UserAccountService;
import org.killze.acgbox.vo.user.UserAccountVO;
import org.killze.acgbox.vo.user.UserAccountEmailChangeVO;
import org.killze.acgbox.vo.user.LoginVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端当前登录账号控制器，仅操作令牌对应的账号。
 */
@RestController
@RequestMapping("/admin/account")
@Slf4j
public class CurrentAccountController {

    @Autowired
    private UserAccountService userAccountService;

    /**
     * 获取自己的账号信息，不返回密码哈希。
     */
    @GetMapping
    public Result<UserAccountVO> getCurrentUserAccount(@AuthenticationPrincipal Jwt jwt) {
        return Result.success(userAccountService.getUserAccountById(Long.valueOf(jwt.getSubject())));
    }

    /**
     * 校验当前密码后修改自己的密码，返回新登录令牌供当前页面继续使用。
     */
    @PostMapping("/change-password")
    public Result<LoginVO> changeCurrentUserAccountPassword(
            @Valid @RequestBody UserAccountChangePasswordDTO passwordDTO,
            @AuthenticationPrincipal Jwt jwt) {
        Long id = Long.valueOf(jwt.getSubject());
        LoginVO loginVO = userAccountService.changeCurrentUserAccountPassword(passwordDTO, id, getTokenVersion(jwt));
        log.info("当前账号修改密码，账号 ID：{}", id);
        return Result.success(loginVO);
    }

    /**
     * 发送绑定、换绑或解绑邮箱验证码，不记录密码和验证码。
     */
    @PostMapping("/email/code")
    public Result<Void> sendCurrentUserAccountEmailCode(
            @Valid @RequestBody UserAccountEmailCodeDTO emailCodeDTO,
            @AuthenticationPrincipal Jwt jwt) {
        userAccountService.sendCurrentUserAccountEmailCode(emailCodeDTO,
                Long.valueOf(jwt.getSubject()), getTokenVersion(jwt));
        return Result.success();
    }

    /**
     * 换绑前验证原邮箱，返回短期凭证，不提前解除原邮箱绑定。
     */
    @PostMapping("/email/verify-current")
    public Result<UserAccountEmailChangeVO> verifyCurrentUserAccountEmail(
            @Valid @RequestBody UserAccountVerifyEmailDTO emailDTO,
            @AuthenticationPrincipal Jwt jwt) {
        return Result.success(userAccountService.verifyCurrentUserAccountEmail(emailDTO,
                Long.valueOf(jwt.getSubject()), getTokenVersion(jwt)));
    }

    /**
     * 验证邮箱验证码后绑定或换绑自己的邮箱，并返回新登录令牌。
     */
    @PostMapping("/email")
    public Result<LoginVO> bindCurrentUserAccountEmail(
            @Valid @RequestBody UserAccountEmailDTO emailDTO,
            @AuthenticationPrincipal Jwt jwt) {
        Long id = Long.valueOf(jwt.getSubject());
        LoginVO loginVO = userAccountService.bindCurrentUserAccountEmail(emailDTO, id, getTokenVersion(jwt));
        log.info("当前账号绑定或换绑邮箱，账号 ID：{}", id);
        return Result.success(loginVO);
    }

    /**
     * 验证原邮箱验证码后解绑自己的邮箱，并返回新登录令牌。
     */
    @PostMapping("/email/unbind")
    public Result<LoginVO> unbindCurrentUserAccountEmail(
            @Valid @RequestBody UserAccountUnbindEmailDTO emailDTO,
            @AuthenticationPrincipal Jwt jwt) {
        Long id = Long.valueOf(jwt.getSubject());
        LoginVO loginVO = userAccountService.unbindCurrentUserAccountEmail(emailDTO, id, getTokenVersion(jwt));
        log.info("当前账号解绑邮箱，账号 ID：{}", id);
        return Result.success(loginVO);
    }

    /**
     * 获取已通过安全过滤器校验的令牌版本。
     */
    private Long getTokenVersion(Jwt jwt) {
        return ((Number) jwt.getClaim("token_version")).longValue();
    }
}
