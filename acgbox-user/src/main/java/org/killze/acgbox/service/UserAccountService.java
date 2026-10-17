package org.killze.acgbox.service;

import org.killze.acgbox.dto.user.UserAccountPageDTO;
import org.killze.acgbox.vo.common.PageVO;
import org.killze.acgbox.vo.user.UserAccountVO;

/**
 * 用户账号服务接口。
 */
public interface UserAccountService {

    /**
     * 分页查询用户账号。
     *
     * @param pageDTO 分页查询参数
     * @return 用户账号分页结果
     */
    PageVO<UserAccountVO> pageUserAccount(UserAccountPageDTO pageDTO);

    /**
     * 根据 ID 查询用户账号。
     *
     * @param id 用户账号 ID
     * @return 用户账号信息
     */
    UserAccountVO getUserAccountById(Long id);
}
