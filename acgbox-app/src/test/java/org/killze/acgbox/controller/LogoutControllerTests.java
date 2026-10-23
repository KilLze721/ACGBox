package org.killze.acgbox.controller;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.jayway.jsonpath.JsonPath;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.killze.acgbox.config.JwtConfig;
import org.killze.acgbox.config.SecurityConfig;
import org.killze.acgbox.entity.user.UserAccount;
import org.killze.acgbox.mapper.UserAccountMapper;
import org.killze.acgbox.service.JwtService;
import org.killze.acgbox.service.EmailVerificationService;
import org.killze.acgbox.service.impl.JwtServiceImpl;
import org.killze.acgbox.service.impl.LoginServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 管理端退出登录与真实 JWT 安全链测试，模拟数据库的版本条件更新。
 */
@SpringJUnitWebConfig(LogoutControllerTests.TestConfig.class)
@TestPropertySource(properties = {
        "jwt.secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "jwt.issuer=acgbox-admin",
        "jwt.audience=acgbox-admin",
        "jwt.expiration=7200"
})
class LogoutControllerTests {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private UserAccountMapper userAccountMapper;

    @MockitoBean
    private EmailVerificationService emailVerificationService;

    private UserAccount currentUser;
    private UserAccount otherUser;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), UserAccount.class
        );
        currentUser = UserAccount.builder()
                .id(1L)
                .username("admin")
                .passwordHash(passwordEncoder.encode("123456"))
                .role("ADMIN")
                .enabled(true)
                .tokenVersion(7L)
                .build();
        otherUser = UserAccount.builder()
                .id(2L)
                .username("editor")
                .role("EDITOR")
                .enabled(true)
                .tokenVersion(3L)
                .build();
        Map<Long, UserAccount> accounts = Map.of(1L, currentUser, 2L, otherUser);
        when(userAccountMapper.selectOne(any())).thenAnswer(invocation -> {
            LambdaQueryWrapper<UserAccount> query = invocation.getArgument(0);
            String sql = query.getSqlSegment();
            Object value = query.getParamNameValuePairs().get("MPGENVAL1");
            if (sql.contains("lower(username)")) {
                return accounts.values().stream()
                        .filter(account -> account.getUsername().equalsIgnoreCase((String) value))
                        .findFirst().orElse(null);
            }
            return accounts.get(value);
        });
        when(userAccountMapper.update(isNull(UserAccount.class), any())).thenAnswer(invocation -> {
            LambdaUpdateWrapper<UserAccount> update = invocation.getArgument(1);
            String sql = update.getSqlSegment();
            assertTrue(sql.contains("id ="));
            assertTrue(sql.contains("token_version ="));
            assertTrue(update.getSqlSet().contains("token_version = token_version + 1"));
            assertTrue(update.getSqlSet().contains("updated_at="));
            assertFalse(update.getSqlSet().contains("enabled="));
            assertFalse(update.getSqlSet().contains("role="));
            UserAccount account = accounts.get(update.getParamNameValuePairs().get("MPGENVAL2"));
            if (account == null || !account.getTokenVersion()
                    .equals(update.getParamNameValuePairs().get("MPGENVAL3"))) {
                return 0;
            }
            account.setTokenVersion(account.getTokenVersion() + 1L);
            return 1;
        });
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "EDITOR"})
    void shouldAllowManagementRolesToLogoutWithoutRequestBody(String role) throws Exception {
        currentUser.setRole(role);
        String token = jwtService.createToken(currentUser);

        MvcResult result = mockMvc.perform(post("/admin/logout")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(header().doesNotExist(HttpHeaders.LOCATION))
                .andReturn();

        assertEquals(8L, currentUser.getTokenVersion());
        assertEquals(role, currentUser.getRole());
        assertTrue(currentUser.getEnabled());
        assertNull(result.getRequest().getSession(false));
    }

    @Test
    void shouldInvalidateAllOldTokensAndAllowLoginAgain() throws Exception {
        String token = jwtService.createToken(currentUser);
        String anotherToken = createToken(Instant.now().plusSeconds(3600));

        mockMvc.perform(post("/admin/logout")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());

        // 访问同一受保护接口验证旧令牌失效，不需要新增测试专用业务接口。
        for (String oldToken : List.of(token, anotherToken)) {
            mockMvc.perform(post("/admin/logout")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + oldToken))
                    .andExpect(status().isUnauthorized());
        }
        assertEquals(8L, currentUser.getTokenVersion());
        verify(userAccountMapper, times(1)).update(isNull(UserAccount.class), any());

        MvcResult login = mockMvc.perform(post("/admin/login/username")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"123456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();
        String newToken = JsonPath.read(login.getResponse().getContentAsString(), "$.data.token");
        mockMvc.perform(post("/admin/logout")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + newToken))
                .andExpect(status().isOk());
        assertEquals(9L, currentUser.getTokenVersion());
    }

    @Test
    void shouldIgnoreSubmittedAccountIdAndPreserveOtherAccountLogin() throws Exception {
        String otherToken = jwtService.createToken(otherUser);
        mockMvc.perform(post("/admin/logout")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtService.createToken(currentUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":2,\"tokenVersion\":3}"))
                .andExpect(status().isOk());

        assertEquals(8L, currentUser.getTokenVersion());
        assertEquals(3L, otherUser.getTokenVersion());
        mockMvc.perform(post("/admin/logout")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + otherToken))
                .andExpect(status().isOk());
        assertEquals(4L, otherUser.getTokenVersion());
    }

    @Test
    void shouldNotInvalidateNewVersionWhenAccountChangesAfterAuthentication() throws Exception {
        String token = jwtService.createToken(currentUser);
        doAnswer(invocation -> {
            UserAccount authenticatedUser = UserAccount.builder()
                    .id(1L).enabled(true).tokenVersion(7L).build();
            // 模拟认证完成后，另一个请求已修改账号版本并重新签发令牌。
            currentUser.setTokenVersion(8L);
            return authenticatedUser;
        }).when(userAccountMapper).selectOne(any());

        mockMvc.perform(post("/admin/logout")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());

        assertEquals(8L, currentUser.getTokenVersion());
        verify(userAccountMapper).update(isNull(UserAccount.class), any());
    }

    @Test
    void shouldRejectAnonymousLogout() throws Exception {
        mockMvc.perform(post("/admin/logout"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(userAccountMapper);
    }

    @Test
    void shouldRejectInvalidToken() throws Exception {
        mockMvc.perform(post("/admin/logout")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(userAccountMapper);
    }

    @Test
    void shouldRejectExpiredToken() throws Exception {
        mockMvc.perform(post("/admin/logout")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer "
                                + createToken(Instant.now().minusSeconds(120))))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(userAccountMapper);
    }

    @Test
    void shouldRejectDisabledAccount() throws Exception {
        String token = jwtService.createToken(currentUser);
        currentUser.setEnabled(false);

        mockMvc.perform(post("/admin/logout")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());

        verify(userAccountMapper, never()).update(isNull(UserAccount.class), any());
    }

    @Test
    void shouldRejectUserRole() throws Exception {
        currentUser.setRole("USER");

        mockMvc.perform(post("/admin/logout")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtService.createToken(currentUser)))
                .andExpect(status().isForbidden());

        verify(userAccountMapper, never()).update(isNull(UserAccount.class), any());
    }

    @Test
    void shouldNotLogoutThroughGetRequest() throws Exception {
        mockMvc.perform(get("/admin/logout")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtService.createToken(currentUser)))
                .andExpect(status().isMethodNotAllowed());

        assertEquals(7L, currentUser.getTokenVersion());
        verify(userAccountMapper, never()).update(isNull(UserAccount.class), any());
    }

    private String createToken(Instant expiresAt) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("acgbox-admin")
                .audience(List.of("acgbox-admin"))
                .subject(currentUser.getId().toString())
                .issuedAt(expiresAt.minusSeconds(3600))
                .expiresAt(expiresAt)
                .claim("role", currentUser.getRole())
                .claim("token_version", currentUser.getTokenVersion())
                .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }

    @TestConfiguration
    @EnableWebMvc
    @EnableWebSecurity
    @Import({SecurityConfig.class, JwtConfig.class, LoginController.class, LogoutController.class,
            LoginServiceImpl.class, JwtServiceImpl.class})
    static class TestConfig {
    }
}
