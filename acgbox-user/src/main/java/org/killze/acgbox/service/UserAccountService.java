package org.killze.acgbox.service;

import org.killze.acgbox.dto.user.UserAccountPageDTO;
import org.killze.acgbox.dto.user.UserAccountDTO;
import org.killze.acgbox.dto.user.UserAccountPasswordDTO;
import org.killze.acgbox.dto.user.UserAccountCreateDTO;
import org.killze.acgbox.dto.user.UserAccountDeleteDTO;
import org.killze.acgbox.dto.user.UserAccountChangePasswordDTO;
import org.killze.acgbox.dto.user.UserAccountEmailCodeDTO;
import org.killze.acgbox.dto.user.UserAccountEmailDTO;
import org.killze.acgbox.dto.user.UserAccountUnbindEmailDTO;
import org.killze.acgbox.dto.user.UserAccountVerifyEmailDTO;
import org.killze.acgbox.vo.common.PageVO;
import org.killze.acgbox.vo.user.UserAccountVO;
import org.killze.acgbox.vo.user.UserAccountEmailChangeVO;
import org.killze.acgbox.vo.user.LoginVO;

/**
 * 用户账号服务接口。
 */
public interface UserAccountService {

    /**
     * 创建编辑或普通用户账号，不允许创建管理员。
     *
     * @param userAccountCreateDTO 用户账号创建信息
     * @return 用户账号信息
     */
    UserAccountVO createUserAccount(UserAccountCreateDTO userAccountCreateDTO);

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

    /**
     * 验证管理员密码后物理删除单个账号，不允许删除管理员。
     *
     * @param userAccountDeleteDTO 用户账号删除信息
     * @param operatorId 当前登录的管理员 ID
     */
    void deleteUserAccount(UserAccountDeleteDTO userAccountDeleteDTO, Long operatorId);

    /**
     * 校验当前密码后修改自己的密码，返回当前账号的新登录令牌。
     *
     * @param passwordDTO 密码修改信息
     * @param id 当前登录账号 ID
     * @param tokenVersion 当前登录令牌版本号
     * @return 当前账号的新登录信息
     */
    LoginVO changeCurrentUserAccountPassword(UserAccountChangePasswordDTO passwordDTO, Long id, Long tokenVersion);

    /**
     * 发送绑定、换绑或解绑邮箱验证码，换绑的新邮箱发信须先验证原邮箱。
     *
     * @param emailCodeDTO 验证码发送信息
     * @param id 当前登录账号 ID
     * @param tokenVersion 当前登录令牌版本号
     */
    void sendCurrentUserAccountEmailCode(UserAccountEmailCodeDTO emailCodeDTO, Long id, Long tokenVersion);

    /**
     * 验证原邮箱并返回短期换绑凭证，暂不修改邮箱或令牌版本。
     *
     * @param emailDTO 原邮箱验证信息
     * @param id 当前登录账号 ID
     * @param tokenVersion 当前登录令牌版本号
     * @return 邮箱换绑凭证
     */
    UserAccountEmailChangeVO verifyCurrentUserAccountEmail(UserAccountVerifyEmailDTO emailDTO, Long id, Long tokenVersion);

    /**
     * 验证新邮箱后绑定邮箱并返回新登录令牌，换绑须同时提交已验证原邮箱的凭证。
     *
     * @param emailDTO 邮箱绑定信息
     * @param id 当前登录账号 ID
     * @param tokenVersion 当前登录令牌版本号
     * @return 当前账号的新登录信息
     */
    LoginVO bindCurrentUserAccountEmail(UserAccountEmailDTO emailDTO, Long id, Long tokenVersion);

    /**
     * 校验原邮箱验证码后解绑自己的邮箱并返回新登录令牌，不要求输入密码。
     *
     * @param emailDTO 邮箱解绑信息
     * @param id 当前登录账号 ID
     * @param tokenVersion 当前登录令牌版本号
     * @return 当前账号的新登录信息
     */
    LoginVO unbindCurrentUserAccountEmail(UserAccountUnbindEmailDTO emailDTO, Long id, Long tokenVersion);
}
