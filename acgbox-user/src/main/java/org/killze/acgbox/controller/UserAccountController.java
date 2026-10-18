package org.killze.acgbox.controller;

import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.killze.acgbox.dto.user.UserAccountDTO;
import org.killze.acgbox.dto.user.UserAccountPageDTO;
import org.killze.acgbox.dto.user.UserAccountPasswordDTO;
import org.killze.acgbox.dto.user.UserAccountCreateDTO;
import org.killze.acgbox.dto.user.UserAccountDeleteDTO;
import org.killze.acgbox.result.Result;
import org.killze.acgbox.service.UserAccountService;
import org.killze.acgbox.vo.common.PageVO;
import org.killze.acgbox.vo.user.UserAccountVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端用户账号控制器。
 */
@RestController
@RequestMapping("/admin/users")
@Slf4j
public class UserAccountController {

    @Autowired
    private UserAccountService userAccountService;

    /**
     * 创建编辑或普通用户账号，不记录初始密码。
     */
    @PostMapping("/create")
    public Result<UserAccountVO> createUserAccount(@Valid @RequestBody UserAccountCreateDTO userAccountCreateDTO) {
        log.info("创建用户账号");
        return Result.success(userAccountService.createUserAccount(userAccountCreateDTO));
    }

    /**
     * 分页查询用户账号。
     */
    @GetMapping("/page")
    public Result<PageVO<UserAccountVO>> pageUserAccount(UserAccountPageDTO pageDTO) {
        log.info("分页查询用户账号");
        return Result.success(userAccountService.pageUserAccount(pageDTO));
    }

    /**
     * 根据 ID 获取用户账号。
     */
    @GetMapping("/{id}")
    public Result<UserAccountVO> getUserAccountById(@PathVariable Long id) {
        log.info("根据 ID 获取用户账号：{}", id);
        return Result.success(userAccountService.getUserAccountById(id));
    }

    /**
     * 修改用户账号，操作者 ID 从已认证的 JWT 获取，不接受前端指定。
     */
    @PostMapping("/update")
    public Result<UserAccountVO> updateUserAccount(
            @Valid @RequestBody UserAccountDTO userAccountDTO,
            @AuthenticationPrincipal Jwt jwt) {
        log.info("修改用户账号：{}", userAccountDTO.getId());
        return Result.success(userAccountService.updateUserAccount(userAccountDTO, Long.valueOf(jwt.getSubject())));
    }

    /**
     * 重置用户账号密码，不记录或返回密码内容。
     */
    @PostMapping("/reset-password")
    public Result<Void> resetUserAccountPassword(
            @Valid @RequestBody UserAccountPasswordDTO passwordDTO,
            @AuthenticationPrincipal Jwt jwt) {
        log.info("重置用户账号密码：{}", passwordDTO.getId());
        userAccountService.resetUserAccountPassword(passwordDTO, Long.valueOf(jwt.getSubject()));
        return Result.success();
    }

    /**
     * 物理删除单个用户账号，操作者 ID 从已认证的 JWT 获取。
     */
    @PostMapping("/delete")
    public Result<Void> deleteUserAccount(
            @Valid @RequestBody UserAccountDeleteDTO userAccountDeleteDTO,
            @AuthenticationPrincipal Jwt jwt) {
        log.info("删除用户账号：{}", userAccountDeleteDTO.getId());
        userAccountService.deleteUserAccount(userAccountDeleteDTO, Long.valueOf(jwt.getSubject()));
        return Result.success();
    }
}
