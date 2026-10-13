package org.killze.acgbox.service;

import org.killze.acgbox.entity.user.UserAccount;

/** JWT 签发服务接口。 */
public interface JwtService {

    /** 为已通过登录校验的账号生成登录令牌。 */
    String createToken(UserAccount userAccount);
}
