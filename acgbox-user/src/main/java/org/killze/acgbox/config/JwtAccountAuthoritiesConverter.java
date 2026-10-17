package org.killze.acgbox.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.killze.acgbox.entity.user.UserAccount;
import org.killze.acgbox.mapper.UserAccountMapper;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;

import java.util.Collection;
import java.util.List;

/**
 * 校验账号状态和令牌版本，使用版本校验通过的 JWT 角色授权。
 */
public class JwtAccountAuthoritiesConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    private final UserAccountMapper userAccountMapper;

    public JwtAccountAuthoritiesConverter(UserAccountMapper userAccountMapper) {
        this.userAccountMapper = userAccountMapper;
    }

    /**
     * JWT 校验通过后查询账号，拒绝版本已失效的令牌。
     *
     * @param jwt 已通过签名和声明校验的令牌
     * @return 令牌角色对应的权限
     */
    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        Long id;
        try {
            id = Long.valueOf(jwt.getSubject());
        } catch (NumberFormatException exception) {
            throw new InvalidBearerTokenException("令牌中的账号 ID 无效", exception);
        }
        Object tokenVersion = jwt.getClaim("token_version");
        if (!(tokenVersion instanceof Long || tokenVersion instanceof Integer)
                || ((Number) tokenVersion).longValue() < 0L) {
            throw new InvalidBearerTokenException("令牌版本号无效，请重新登录");
        }

        // 每次请求只查询认证需要的字段，不读取密码哈希等账号信息。
        UserAccount userAccount = userAccountMapper.selectOne(
                new LambdaQueryWrapper<UserAccount>()
                        .select(UserAccount::getId, UserAccount::getEnabled, UserAccount::getTokenVersion)
                        .eq(UserAccount::getId, id)
        );
        if (userAccount == null || !Boolean.TRUE.equals(userAccount.getEnabled())) {
            throw new InvalidBearerTokenException("账号不存在或已被禁用");
        }
        if (userAccount.getTokenVersion() == null
                || ((Number) tokenVersion).longValue() != userAccount.getTokenVersion()) {
            throw new InvalidBearerTokenException("登录状态已失效，请重新登录");
        }

        String role = jwt.getClaimAsString("role");
        if (!"ADMIN".equals(role) && !"EDITOR".equals(role) && !"USER".equals(role)) {
            throw new InvalidBearerTokenException("令牌中的账号角色无效");
        }
        return List.of(new SimpleGrantedAuthority("ROLE_" + role));
    }
}
