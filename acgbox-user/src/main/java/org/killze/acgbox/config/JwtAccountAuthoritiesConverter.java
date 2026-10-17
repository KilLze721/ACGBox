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
 * 根据账号当前状态和角色转换 JWT 权限，不使用令牌中的旧角色授权。
 */
public class JwtAccountAuthoritiesConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    private final UserAccountMapper userAccountMapper;

    public JwtAccountAuthoritiesConverter(UserAccountMapper userAccountMapper) {
        this.userAccountMapper = userAccountMapper;
    }

    /**
     * JWT 校验通过后查询账号，确保禁用和角色变更在下一次请求生效。
     *
     * @param jwt 已通过签名和声明校验的令牌
     * @return 账号当前角色对应的权限
     */
    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        Long id;
        try {
            id = Long.valueOf(jwt.getSubject());
        } catch (NumberFormatException exception) {
            throw new InvalidBearerTokenException("令牌中的账号 ID 无效", exception);
        }

        // 每次请求只查询认证需要的字段，不读取密码哈希等账号信息。
        UserAccount userAccount = userAccountMapper.selectOne(
                new LambdaQueryWrapper<UserAccount>()
                        .select(UserAccount::getId, UserAccount::getRole, UserAccount::getEnabled)
                        .eq(UserAccount::getId, id)
        );
        if (userAccount == null || !Boolean.TRUE.equals(userAccount.getEnabled())) {
            throw new InvalidBearerTokenException("账号不存在或已被禁用");
        }

        return List.of(new SimpleGrantedAuthority("ROLE_" + userAccount.getRole()));
    }
}
