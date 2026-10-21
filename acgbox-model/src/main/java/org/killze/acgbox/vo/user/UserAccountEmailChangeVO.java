package org.killze.acgbox.vo.user;

import lombok.Builder;
import lombok.Data;
import lombok.ToString;

/**
 * 原邮箱验证通过后返回的邮箱换绑凭证。
 */
@Data
@Builder
public class UserAccountEmailChangeVO {

    /**
     * 仅用于当前账号本次换绑的短期凭证，不是登录令牌。
     */
    @ToString.Exclude
    private String emailChangeToken;

    /**
     * 凭证有效时长，单位为秒。
     */
    private Long expiresIn;
}
