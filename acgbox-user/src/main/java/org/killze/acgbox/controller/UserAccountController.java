package org.killze.acgbox.controller;

import lombok.extern.slf4j.Slf4j;
import org.killze.acgbox.dto.user.UserAccountPageDTO;
import org.killze.acgbox.result.Result;
import org.killze.acgbox.service.UserAccountService;
import org.killze.acgbox.vo.common.PageVO;
import org.killze.acgbox.vo.user.UserAccountVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
}
