package org.killze.acgbox.service;

import org.killze.acgbox.dto.user.UserAccountPageDTO;
import org.killze.acgbox.dto.user.UserAccountDTO;
import org.killze.acgbox.dto.user.UserAccountPasswordDTO;
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

    /**
     * 修改用户账号，邮箱变化时验证执行操作的管理员密码。
     *
     * @param userAccountDTO 用户账号信息
     * @param operatorId 当前登录的管理员 ID
     * @return 修改后的用户账号信息
     */
    UserAccountVO updateUserAccount(UserAccountDTO userAccountDTO, Long operatorId);

    /**
     * 重置用户账号密码，并使该账号的旧令牌失效。
     *
     * @param passwordDTO 密码重置信息
     * @param operatorId 当前登录的管理员 ID
     */
    void resetUserAccountPassword(UserAccountPasswordDTO passwordDTO, Long operatorId);
}
