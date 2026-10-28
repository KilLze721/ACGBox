package org.killze.acgbox.config;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.killze.acgbox.entity.user.UserAccount;
import org.killze.acgbox.mapper.UserAccountMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 管理端 JWT 认证和角色权限测试，不连接数据库。
 */
@SpringJUnitWebConfig(SecurityConfigTests.TestConfig.class)
@TestPropertySource(properties = {
        "jwt.secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "jwt.issuer=acgbox-admin",
        "jwt.audience=acgbox-admin"
})
class SecurityConfigTests {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private JwtEncoder jwtEncoder;

    @MockitoBean
    private UserAccountMapper userAccountMapper;

    @MockitoBean
    private org.killze.acgbox.service.ImageUploadService imageUploadService;

    private UserAccount userAccount;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        // 初始化实体映射，并模拟每次请求查询到的当前账号，不连接真实数据库。
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), UserAccount.class
        );
        userAccount = UserAccount.builder()
                .id(1L)
                .role("ADMIN")
                .enabled(true)
                .tokenVersion(0L)
                .build();
        when(userAccountMapper.selectOne(any())).thenReturn(userAccount);

        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "EDITOR"})
    void shouldAllowManagementRolesToReadContent(String role) throws Exception {
        userAccount.setRole(role);

        mockMvc.perform(get("/security-test/content")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + createToken(role)))
                .andExpect(status().isOk())
                .andExpect(content().string("ok"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "EDITOR"})
    void shouldAllowManagementRolesToModifyContent(String role) throws Exception {
        userAccount.setRole(role);

        mockMvc.perform(post("/security-test/content")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + createToken(role)))
                .andExpect(status().isOk())
                .andExpect(content().string("ok"));
    }

    @Test
    void shouldRejectUserRoleToken() throws Exception {
        userAccount.setRole("USER");

        mockMvc.perform(get("/security-test/content")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + createToken("USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldRejectAnonymousBusinessRequest() throws Exception {
        mockMvc.perform(get("/security-test/content"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(userAccountMapper);
    }

    @Test
    void shouldRejectInvalidToken() throws Exception {
        mockMvc.perform(get("/security-test/content")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(userAccountMapper);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/admin/login/username", "/admin/login/email-password",
            "/admin/login/email-code/send", "/admin/login/email-code"})
    void shouldAllowAnonymousLoginRequests(String path) throws Exception {
        mockMvc.perform(post(path))
                .andExpect(status().isOk())
                .andExpect(content().string("ok"));

        verifyNoInteractions(userAccountMapper);
    }

    @Test
    void shouldAllowAdminToManageUsers() throws Exception {
        String token = createToken("ADMIN");

        mockMvc.perform(get("/admin/users/security-test")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
        mockMvc.perform(post("/admin/users/security-test")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {"EDITOR", "USER"})
    void shouldRejectNonAdminAccessToUserManagement(String role) throws Exception {
        userAccount.setRole(role);
        String token = createToken(role);

        mockMvc.perform(get("/admin/users/security-test")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/admin/users/security-test")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldApplyRoleDowngradeOnNextRequestWithSameToken() throws Exception {
        String token = createToken("ADMIN");
        mockMvc.perform(get("/admin/users/security-test")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());

        userAccount.setRole("EDITOR");
        userAccount.setTokenVersion(1L);

        mockMvc.perform(get("/admin/users/security-test")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/security-test/content")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/security-test/content")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + createToken("EDITOR")))
                .andExpect(status().isOk());

        verify(userAccountMapper, times(4)).selectOne(any());
    }

    @Test
    void shouldRejectContentAfterRoleChangesToUser() throws Exception {
        String token = createToken("ADMIN");
        mockMvc.perform(get("/security-test/content")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());

        userAccount.setRole("USER");
        userAccount.setTokenVersion(1L);

        mockMvc.perform(get("/security-test/content")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/security-test/content")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + createToken("USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldApplyRoleUpgradeOnNextRequestWithSameToken() throws Exception {
        userAccount.setRole("EDITOR");
        String token = createToken("EDITOR");
        mockMvc.perform(get("/admin/users/security-test")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden());

        userAccount.setRole("ADMIN");
        userAccount.setTokenVersion(1L);

        mockMvc.perform(get("/admin/users/security-test")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/admin/users/security-test")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + createToken("ADMIN")))
                .andExpect(status().isOk());

        verify(userAccountMapper, times(3)).selectOne(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "EDITOR"})
    void shouldRejectAccountAfterItIsDisabled(String role) throws Exception {
        userAccount.setRole(role);
        String token = createToken(role);
        mockMvc.perform(get("/security-test/content")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());

        userAccount.setEnabled(false);
        userAccount.setTokenVersion(1L);

        mockMvc.perform(get("/security-test/content")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());

        verify(userAccountMapper, times(2)).selectOne(any());
    }

    @Test
    void shouldRejectAccountAfterItIsDeleted() throws Exception {
        String token = createToken("ADMIN");
        mockMvc.perform(get("/security-test/content")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());

        when(userAccountMapper.selectOne(any())).thenReturn(null);

        mockMvc.perform(get("/security-test/content")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldRestoreAccessAfterAccountIsEnabled() throws Exception {
        userAccount.setEnabled(false);
        String token = createToken("ADMIN");
        mockMvc.perform(get("/security-test/content")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());

        userAccount.setEnabled(true);
        userAccount.setTokenVersion(1L);

        mockMvc.perform(get("/security-test/content")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/security-test/content")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + createToken("ADMIN")))
                .andExpect(status().isOk());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"abc", "9223372036854775808"})
    void shouldRejectMissingOrInvalidAccountId(String subject) throws Exception {
        mockMvc.perform(get("/security-test/content")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + createToken("ADMIN", subject)))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(userAccountMapper);
    }

    @Test
    void shouldQueryOnlyCurrentAccountAuthenticationFields() throws Exception {
        when(userAccountMapper.selectOne(any())).thenAnswer(invocation -> {
            LambdaQueryWrapper<UserAccount> query = invocation.getArgument(0);
            assertEquals("id,enabled,token_version", query.getSqlSelect());
            assertTrue(query.getSqlSegment().contains("id ="));
            assertEquals(1L, query.getParamNameValuePairs().get("MPGENVAL1"));
            return userAccount;
        });

        mockMvc.perform(get("/security-test/content")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + createToken("ADMIN")))
                .andExpect(status().isOk());

        verify(userAccountMapper).selectOne(any());
    }

    /**
     * 使用独立的测试密钥签发令牌，验证实际的解码和角色转换流程。
     */
    private String createToken(String role) {
        return createToken(role, "1");
    }

    /**
     * 支持省略或替换账号 ID，验证非法令牌不会进入账号查询。
     */
    private String createToken(String role, String subject) {
        return createToken(role, subject, userAccount.getTokenVersion());
    }

    /**
     * 支持构造缺失或非法的版本号，确保不能兼容放行旧令牌。
     */
    private String createToken(String role, String subject, Object tokenVersion) {
        Instant now = Instant.now();
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer("acgbox-admin")
                .audience(List.of("acgbox-admin"))
                .issuedAt(now)
                .expiresAt(now.plusSeconds(60))
                .claim("role", role);
        if (subject != null) {
            claims.subject(subject);
        }
        if (tokenVersion != null) {
            claims.claim("token_version", tokenVersion);
        }
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims.build())).getTokenValue();
    }

    @Test
    void shouldRejectTokenWithoutVersion() throws Exception {
        mockMvc.perform(get("/security-test/content")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + createToken("ADMIN", "1", null)))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(userAccountMapper);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "abc"})
    void shouldRejectNonNumericVersion(String tokenVersion) throws Exception {
        mockMvc.perform(get("/security-test/content")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + createToken("ADMIN", "1", tokenVersion)))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(userAccountMapper);
    }

    @Test
    void shouldRejectFractionalVersionWithoutTruncatingIt() throws Exception {
        mockMvc.perform(get("/security-test/content")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + createToken("ADMIN", "1", 0.5)))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(userAccountMapper);
    }

    @Test
    void shouldRejectTokenAfterPasswordVersionChanges() throws Exception {
        String token = createToken("ADMIN");
        userAccount.setTokenVersion(1L);

        mockMvc.perform(get("/security-test/content")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldUseSignedRoleWhenVersionMatchesWithoutReadingDatabaseRole() throws Exception {
        userAccount.setRole(null);

        mockMvc.perform(get("/admin/users/security-test")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + createToken("ADMIN")))
                .andExpect(status().isOk());
    }

    @TestConfiguration
    @EnableWebMvc
    @EnableWebSecurity
    @Import({SecurityConfig.class, JwtConfig.class, TestController.class,
            org.killze.acgbox.controller.ImageUploadController.class})
    static class TestConfig {
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "EDITOR", "USER"})
    void imageUploadUsesActualControllerAndExistingContentPermissions(String role) throws Exception {
        var file = new org.springframework.mock.web.MockMultipartFile("file", "cover.png", "image/png", new byte[]{1});
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/uploads/images")
                        .file(file).param("targetType", "ANIME")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + createToken(role)))
                .andExpect(role.equals("USER") ? status().isForbidden() : status().isOk());
        if (role.equals("USER")) {
            verifyNoInteractions(imageUploadService);
        } else {
            verify(imageUploadService).uploadImage(file, "ANIME");
        }
    }

    @Test
    void imageUploadRejectsAnonymousInvalidAndRevokedTokensBeforeBusiness() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/uploads/images"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/uploads/images")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token"))
                .andExpect(status().isUnauthorized());
        String token = createToken("ADMIN");
        userAccount.setTokenVersion(1L);
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/uploads/images")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(imageUploadService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "EDITOR", "USER"})
    void bookRoutesKeepContentManagementRoleRules(String role) throws Exception {
        String token = createToken(role);
        for (String path : java.util.List.of("/book/page", "/book-type/list")) {
            mockMvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(role.equals("USER") ? status().isForbidden() : status().isOk());
        }
        for (String path : java.util.List.of("/book/create", "/book-type/create")) {
            mockMvc.perform(post(path).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(role.equals("USER") ? status().isForbidden() : status().isOk());
        }
    }

    /**
     * 仅在本测试上下文注册的接口，不参与正式应用的组件扫描。
     */
    @TestComponent
    @RestController
    static class TestController {

        @GetMapping({"/security-test/content", "/admin/users/security-test", "/book/page", "/book-type/list"})
        public String getContent() {
            return "ok";
        }

        @PostMapping({"/security-test/content", "/admin/users/security-test", "/admin/login/username",
                "/admin/login/email-password", "/admin/login/email-code/send", "/admin/login/email-code",
                "/book/create", "/book-type/create"})
        public String postContent() {
            return "ok";
        }
    }
}
