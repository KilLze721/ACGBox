package org.killze.acgbox.controller;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.killze.acgbox.config.JwtConfig;
import org.killze.acgbox.config.SecurityConfig;
import org.killze.acgbox.dto.user.UserAccountPageDTO;
import org.killze.acgbox.dto.user.UserAccountDTO;
import org.killze.acgbox.dto.user.UserAccountPasswordDTO;
import org.killze.acgbox.dto.user.UserAccountCreateDTO;
import org.killze.acgbox.dto.user.UserAccountDeleteDTO;
import org.killze.acgbox.entity.user.UserAccount;
import org.killze.acgbox.exception.BusinessException;
import org.killze.acgbox.exception.GlobalExceptionHandler;
import org.killze.acgbox.mapper.UserAccountMapper;
import org.killze.acgbox.service.UserAccountService;
import org.killze.acgbox.vo.common.PageVO;
import org.killze.acgbox.vo.user.UserAccountVO;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
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
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 用户账号管理接口与真实 JWT 安全链测试，不连接真实数据库。
 */
@SpringJUnitWebConfig(UserAccountControllerTests.TestConfig.class)
@TestPropertySource(properties = {
        "jwt.secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "jwt.issuer=acgbox-admin",
        "jwt.audience=acgbox-admin"
})
class UserAccountControllerTests {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private JwtEncoder jwtEncoder;

    @MockitoBean
    private UserAccountMapper userAccountMapper;

    @MockitoBean
    private UserAccountService userAccountService;

    private UserAccount currentUser;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), UserAccount.class
        );
        currentUser = UserAccount.builder()
                .id(1L)
                .role("ADMIN")
                .enabled(true)
                .tokenVersion(0L)
                .build();
        when(userAccountMapper.selectOne(any())).thenReturn(currentUser);

        UserAccountVO userAccountVO = UserAccountVO.builder()
                .id(2L)
                .username("EditorCase")
                .email("Editor@Example.com")
                .role("EDITOR")
                .enabled(false)
                .emailVerifiedAt(LocalDateTime.of(2026, 10, 1, 12, 0))
                .createdAt(LocalDateTime.of(2026, 9, 1, 12, 0))
                .updatedAt(LocalDateTime.of(2026, 10, 2, 12, 0))
                .build();
        when(userAccountService.getUserAccountById(anyLong())).thenReturn(userAccountVO);
        when(userAccountService.updateUserAccount(any(), anyLong())).thenReturn(userAccountVO);
        when(userAccountService.pageUserAccount(any())).thenReturn(PageVO.<UserAccountVO>builder()
                .pageNum(1L)
                .pageSize(20L)
                .total(1L)
                .pages(1L)
                .rows(List.of(userAccountVO))
                .build());

        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @Test
    void shouldAllowAdminToReadPageWithDefaultParameters() throws Exception {
        mockMvc.perform(get("/admin/users/page")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + createToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.pageNum").value(1))
                .andExpect(jsonPath("$.data.pageSize").value(20))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.pages").value(1))
                .andExpect(jsonPath("$.data.rows[0].id").value(2))
                .andExpect(jsonPath("$.data.rows[0].username").value("EditorCase"))
                .andExpect(jsonPath("$.data.rows[0].email").value("Editor@Example.com"))
                .andExpect(jsonPath("$.data.rows[0].role").value("EDITOR"))
                .andExpect(jsonPath("$.data.rows[0].enabled").value(false))
                .andExpect(jsonPath("$.data.rows[0].passwordHash").doesNotExist())
                .andExpect(jsonPath("$.data.rows[0].password").doesNotExist());

        ArgumentCaptor<UserAccountPageDTO> captor = ArgumentCaptor.forClass(UserAccountPageDTO.class);
        verify(userAccountService).pageUserAccount(captor.capture());
        assertEquals(1L, captor.getValue().getPageNum());
        assertEquals(20L, captor.getValue().getPageSize());
        assertNull(captor.getValue().getUsername());
        assertNull(captor.getValue().getEmail());
        assertNull(captor.getValue().getRole());
        assertNull(captor.getValue().getEnabled());
    }

    @Test
    void shouldBindAllPageFiltersIncludingDisabledState() throws Exception {
        mockMvc.perform(get("/admin/users/page")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + createToken())
                        .param("pageNum", "2")
                        .param("pageSize", "10")
                        .param("username", "eDiToR")
                        .param("email", "EXAMPLE.COM")
                        .param("role", "EDITOR")
                        .param("enabled", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        ArgumentCaptor<UserAccountPageDTO> captor = ArgumentCaptor.forClass(UserAccountPageDTO.class);
        verify(userAccountService).pageUserAccount(captor.capture());
        UserAccountPageDTO pageDTO = captor.getValue();
        assertEquals(2L, pageDTO.getPageNum());
        assertEquals(10L, pageDTO.getPageSize());
        assertEquals("eDiToR", pageDTO.getUsername());
        assertEquals("EXAMPLE.COM", pageDTO.getEmail());
        assertEquals("EDITOR", pageDTO.getRole());
        assertFalse(pageDTO.getEnabled());
    }

    @Test
    void shouldAllowAdminToReadAccountDetailsWithoutPasswordHash() throws Exception {
        mockMvc.perform(get("/admin/users/2")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + createToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(2))
                .andExpect(jsonPath("$.data.username").value("EditorCase"))
                .andExpect(jsonPath("$.data.email").value("Editor@Example.com"))
                .andExpect(jsonPath("$.data.role").value("EDITOR"))
                .andExpect(jsonPath("$.data.enabled").value(false))
                .andExpect(jsonPath("$.data.emailVerifiedAt").exists())
                .andExpect(jsonPath("$.data.createdAt").exists())
                .andExpect(jsonPath("$.data.updatedAt").exists())
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.data.password").doesNotExist());

        verify(userAccountService).getUserAccountById(2L);
    }

    @Test
    void shouldReturnBusinessErrorWhenAccountDoesNotExist() throws Exception {
        when(userAccountService.getUserAccountById(999L)).thenThrow(new BusinessException("此用户账号不存在"));

        mockMvc.perform(get("/admin/users/999")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + createToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("此用户账号不存在"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"EDITOR", "USER"})
    void shouldRejectNonAdminOnBothAccountEndpoints(String role) throws Exception {
        currentUser.setRole(role);
        String token = createToken();

        for (String path : List.of("/admin/users/page", "/admin/users/2")) {
            mockMvc.perform(get(path)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isForbidden());
        }

        verifyNoInteractions(userAccountService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/admin/users/page", "/admin/users/2"})
    void shouldRejectAnonymousAccountQueries(String path) throws Exception {
        mockMvc.perform(get(path))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(userAccountService, userAccountMapper);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/admin/users/page", "/admin/users/2"})
    void shouldRejectDisabledAdminAccountQueries(String path) throws Exception {
        currentUser.setEnabled(false);

        mockMvc.perform(get(path)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + createToken()))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(userAccountService);
    }

    @Test
    void shouldBindUpdateAndUseOperatorIdFromJwtInsteadOfRequestBody() throws Exception {
        mockMvc.perform(post("/admin/users/update")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + createToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"id":2,"username":"EditorCase","email":"new@example.com",
                                 "role":"EDITOR","enabled":true,"currentPassword":"admin-password","operatorId":999}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.currentPassword").doesNotExist())
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.data.tokenVersion").doesNotExist());

        ArgumentCaptor<UserAccountDTO> captor = ArgumentCaptor.forClass(UserAccountDTO.class);
        verify(userAccountService).updateUserAccount(captor.capture(), eq(1L));
        assertEquals(2L, captor.getValue().getId());
        assertEquals("admin-password", captor.getValue().getCurrentPassword());
    }

    @Test
    void shouldBindPasswordResetWithoutReturningAnyPassword() throws Exception {
        mockMvc.perform(post("/admin/users/reset-password")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + createToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"id":2,"currentPassword":"admin-password","newPassword":"new-user-password"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.newPassword").doesNotExist())
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist());

        ArgumentCaptor<UserAccountPasswordDTO> captor = ArgumentCaptor.forClass(UserAccountPasswordDTO.class);
        verify(userAccountService).resetUserAccountPassword(captor.capture(), eq(1L));
        assertEquals("new-user-password", captor.getValue().getNewPassword());
    }

    @ParameterizedTest
    @ValueSource(strings = {"EDITOR", "USER"})
    void shouldRejectNonAdminOnBothMutationEndpoints(String role) throws Exception {
        currentUser.setRole(role);
        String token = createToken();
        for (String path : List.of("/admin/users/update", "/admin/users/reset-password")) {
            mockMvc.perform(post(path)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isForbidden());
        }
        verifyNoInteractions(userAccountService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/admin/users/update", "/admin/users/reset-password"})
    void shouldRejectAnonymousMutationRequests(String path) throws Exception {
        mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(userAccountService);
    }

    @Test
    void shouldRequireCurrentPasswordInResetRequest() throws Exception {
        mockMvc.perform(post("/admin/users/reset-password")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + createToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"id":2,"newPassword":"new-user-password"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("管理员密码不能为空"));
        verifyNoInteractions(userAccountService);
    }

    @Test
    void shouldRejectUnsupportedRoleBeforeCallingService() throws Exception {
        mockMvc.perform(post("/admin/users/update")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + createToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"id":2,"username":"EditorCase","role":"ROOT","enabled":true}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("账号角色仅支持 ADMIN、EDITOR、USER"));
        verifyNoInteractions(userAccountService);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void shouldCreateAccountWithoutAcceptingServerFieldsOrReturningPassword(boolean enabled) throws Exception {
        when(userAccountService.createUserAccount(any())).thenReturn(UserAccountVO.builder()
                .id(20L).username("NewEditor").role("EDITOR").enabled(enabled)
                .build());

        mockMvc.perform(post("/admin/users/create")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + createToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"NewEditor","email":null,"password":" InitialPassword! ",
                                 "role":"EDITOR","enabled":%s,"id":999,"tokenVersion":999,"passwordHash":"ignored"}
                                """.formatted(enabled)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(20))
                .andExpect(jsonPath("$.data.username").value("NewEditor"))
                .andExpect(jsonPath("$.data.enabled").value(enabled))
                .andExpect(jsonPath("$.data.password").doesNotExist())
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.data.tokenVersion").doesNotExist())
                .andExpect(jsonPath("$.data.token").doesNotExist());

        ArgumentCaptor<UserAccountCreateDTO> captor = ArgumentCaptor.forClass(UserAccountCreateDTO.class);
        verify(userAccountService).createUserAccount(captor.capture());
        assertEquals(" InitialPassword! ", captor.getValue().getPassword());
        assertEquals(enabled, captor.getValue().getEnabled());
        assertNull(captor.getValue().getEmail());
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "ROOT", "editor"})
    void shouldRejectAdminOrUnsupportedRoleInCreateRequest(String role) throws Exception {
        mockMvc.perform(post("/admin/users/create")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + createToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"NewEditor","password":"InitialPassword!","role":"%s","enabled":true}
                                """.formatted(role)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("创建账号仅支持 EDITOR、USER 角色"));

        verifyNoInteractions(userAccountService);
    }

    @Test
    void shouldRequireInitialPasswordInCreateRequest() throws Exception {
        mockMvc.perform(post("/admin/users/create")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + createToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"NewEditor","role":"EDITOR","enabled":true}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("初始密码不能为空"));

        verifyNoInteractions(userAccountService);
    }

    @Test
    void shouldDeleteSingleAccountWithOperatorIdFromJwt() throws Exception {
        mockMvc.perform(post("/admin/users/delete")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + createToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"id":2,"currentPassword":"admin-password","operatorId":999}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.currentPassword").doesNotExist())
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist());

        ArgumentCaptor<UserAccountDeleteDTO> captor = ArgumentCaptor.forClass(UserAccountDeleteDTO.class);
        verify(userAccountService).deleteUserAccount(captor.capture(), eq(1L));
        assertEquals(2L, captor.getValue().getId());
        assertEquals("admin-password", captor.getValue().getCurrentPassword());
    }

    @Test
    void shouldRequireOperatorPasswordInDeleteRequest() throws Exception {
        mockMvc.perform(post("/admin/users/delete")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + createToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"id":2}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("管理员密码不能为空"));

        verifyNoInteractions(userAccountService);
    }

    @Test
    void shouldRejectNonPositiveDeleteId() throws Exception {
        mockMvc.perform(post("/admin/users/delete")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + createToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"id":0,"currentPassword":"admin-password"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("用户账号 ID 必须大于等于 1"));

        verifyNoInteractions(userAccountService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"EDITOR", "USER"})
    void shouldRejectNonAdminOnCreateAndDelete(String role) throws Exception {
        currentUser.setRole(role);
        String token = createToken();
        for (String path : List.of("/admin/users/create", "/admin/users/delete")) {
            mockMvc.perform(post(path)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isForbidden());
        }
        verifyNoInteractions(userAccountService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/admin/users/create", "/admin/users/delete"})
    void shouldRejectAnonymousCreateAndDelete(String path) throws Exception {
        mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(userAccountService, userAccountMapper);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/admin/users/create", "/admin/users/delete"})
    void shouldRejectDisabledAdminOnCreateAndDelete(String path) throws Exception {
        currentUser.setEnabled(false);

        mockMvc.perform(post(path)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + createToken())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(userAccountService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/admin/users/create", "/admin/users/delete"})
    void shouldRejectOldVersionTokenOnCreateAndDelete(String path) throws Exception {
        String token = createToken();
        currentUser.setTokenVersion(1L);

        mockMvc.perform(post(path)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(userAccountService);
    }

    /**
     * 签发带账号当前版本和角色的令牌。
     */
    private String createToken() {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("acgbox-admin")
                .audience(List.of("acgbox-admin"))
                .subject("1")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(60))
                .claim("role", currentUser.getRole())
                .claim("token_version", currentUser.getTokenVersion())
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    @TestConfiguration
    @EnableWebMvc
    @EnableWebSecurity
    @Import({SecurityConfig.class, JwtConfig.class, UserAccountController.class, GlobalExceptionHandler.class})
    static class TestConfig {
    }
}
