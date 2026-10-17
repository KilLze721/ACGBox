package org.killze.acgbox.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.killze.acgbox.config.JwtAccountAuthoritiesConverter;
import org.killze.acgbox.config.JwtConfig;
import org.killze.acgbox.entity.user.UserAccount;
import org.killze.acgbox.mapper.UserAccountMapper;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.SecretKey;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 使用真实编码器和解码器验证 JWT 版本号与旧令牌失效，不连接数据库。
 */
class JwtServiceImplTests {

    private JwtServiceImpl jwtService;
    private JwtDecoder jwtDecoder;
    private UserAccount userAccount;
    private JwtAccountAuthoritiesConverter authoritiesConverter;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), UserAccount.class
        );
        JwtConfig jwtConfig = new JwtConfig();
        SecretKey secretKey = jwtConfig.secretKey("MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=");
        jwtDecoder = jwtConfig.jwtDecoder(secretKey, "acgbox-admin", "acgbox-admin");
        jwtService = new JwtServiceImpl();
        ReflectionTestUtils.setField(jwtService, "jwtEncoder", jwtConfig.jwtEncoder(secretKey));
        ReflectionTestUtils.setField(jwtService, "issuer", "acgbox-admin");
        ReflectionTestUtils.setField(jwtService, "audience", "acgbox-admin");
        ReflectionTestUtils.setField(jwtService, "expiration", 7200L);
        userAccount = UserAccount.builder()
                .id(1L).username("admin").email("admin@example.com")
                .passwordHash("must-not-be-in-token").role("ADMIN").enabled(true).tokenVersion(7L)
                .build();
        UserAccountMapper userAccountMapper = mock(UserAccountMapper.class);
        when(userAccountMapper.selectOne(any())).thenReturn(userAccount);
        authoritiesConverter = new JwtAccountAuthoritiesConverter(userAccountMapper);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "EDITOR"})
    void shouldSignCurrentRoleAndVersionWithoutPasswordOrEmail(String role) {
        userAccount.setRole(role);

        Jwt jwt = jwtDecoder.decode(jwtService.createToken(userAccount));

        assertEquals("1", jwt.getSubject());
        assertEquals("acgbox-admin", jwt.getClaimAsString("iss"));
        assertTrue(jwt.getAudience().contains("acgbox-admin"));
        assertEquals(role, jwt.getClaimAsString("role"));
        assertEquals(7L, ((Number) jwt.getClaim("token_version")).longValue());
        assertEquals(7200L, jwt.getExpiresAt().getEpochSecond() - jwt.getIssuedAt().getEpochSecond());
        assertFalse(jwt.getClaims().containsKey("passwordHash"));
        assertFalse(jwt.getClaims().containsKey("password"));
        assertFalse(jwt.getClaims().containsKey("email"));
    }

    @Test
    void shouldRejectPreviouslySignedTokenAfterVersionIncrement() {
        Jwt oldJwt = jwtDecoder.decode(jwtService.createToken(userAccount));
        assertTrue(authoritiesConverter.convert(oldJwt).stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority())));

        userAccount.setTokenVersion(8L);

        assertThrows(InvalidBearerTokenException.class, () -> authoritiesConverter.convert(oldJwt));
        Jwt newJwt = jwtDecoder.decode(jwtService.createToken(userAccount));
        assertTrue(authoritiesConverter.convert(newJwt).stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority())));
    }

    @Test
    void shouldPreserveLongVersionWithoutIntegerTruncation() {
        userAccount.setTokenVersion(2147483648L);

        Jwt jwt = jwtDecoder.decode(jwtService.createToken(userAccount));

        assertEquals(2147483648L, ((Number) jwt.getClaim("token_version")).longValue());
        assertFalse(authoritiesConverter.convert(jwt).isEmpty());
    }
}
