package org.killze.acgbox.service;

import org.killze.acgbox.dto.user.LoginDTO;
import org.killze.acgbox.entity.user.UserAccount;
import org.killze.acgbox.vo.user.LoginVO;

/**
 * 登录服务接口
 */
public interface LoginService {

    /**
     * 校验用户名密码登录。
     *
     * @param loginDTO 登录信息
     * @return 校验通过的账号
     */
    UserAccount verifyByUsername(LoginDTO loginDTO);

    /**
     * 校验邮箱密码登录。
     *
     * @param loginDTO 登录信息
     * @return 校验通过的账号
     */
    UserAccount verifyByEmail(LoginDTO loginDTO);

    /**
     * 使用用户名和密码登录，返回登录信息。
     *
     * @param loginDTO 登录信息
     * @return 登录信息
     */
    LoginVO loginByUsername(LoginDTO loginDTO);

    /**
     * 使用邮箱和密码登录，返回登录信息。
     *
     * @param loginDTO 登录信息
     * @return 登录信息
     */
    LoginVO loginByEmail(LoginDTO loginDTO);

    /**
     * 退出管理端登录，使账号当前版本的全部令牌失效。
     *
     * @param id 当前登录账号 ID
     * @param tokenVersion 当前登录令牌版本号
     */
    void logout(Long id, Long tokenVersion);
}
