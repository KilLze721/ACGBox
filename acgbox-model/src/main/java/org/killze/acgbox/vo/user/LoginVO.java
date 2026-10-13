package org.killze.acgbox.vo.user;

import lombok.Builder;
import lombok.Data;

/**
 * 管理端登录视图对象。
 */
@Data
@Builder
public class LoginVO {

    /**
     * 登录令牌。
     */
    private String token;

    /**
     * 令牌有效时长，单位为秒。
     */
    private Long expiresIn;

    /**
     * 账号 ID。
     */
    private Long id;

    /**
     * 登录用户名。
     */
    private String username;

    /**
     * 账号角色。
     */
    private String role;
}
