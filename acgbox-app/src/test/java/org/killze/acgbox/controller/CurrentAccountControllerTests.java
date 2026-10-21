package org.killze.acgbox.controller;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.killze.acgbox.config.JwtConfig;
import org.killze.acgbox.config.SecurityConfig;
import org.killze.acgbox.entity.user.UserAccount;
import org.killze.acgbox.exception.BusinessException;
import org.killze.acgbox.exception.GlobalExceptionHandler;
import org.killze.acgbox.mapper.UserAccountMapper;
import org.killze.acgbox.service.EmailVerificationService;
import org.killze.acgbox.service.JwtService;
import org.killze.acgbox.service.impl.JwtServiceImpl;
import org.killze.acgbox.service.impl.UserAccountServiceImpl;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 当前账号编辑与真实 JWT 安全链测试，数据库和验证码服务使用模拟对象。
 */
@SpringJUnitWebConfig(CurrentAccountControllerTests.TestConfig.class)
@TestPropertySource(properties = {
        "jwt.secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "jwt.issuer=acgbox-admin",
        "jwt.audience=acgbox-admin",
        "jwt.expiration=7200"
})
class CurrentAccountControllerTests {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private UserAccountMapper mapper;

    @MockitoBean
    private EmailVerificationService emailVerificationService;

    private UserAccount account;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), UserAccount.class);
        account = UserAccount.builder().id(2L).username("Editor").role("EDITOR").enabled(true)
                .email(null).tokenVersion(7L)
                .passwordHash(new BCryptPasswordEncoder(4).encode("old-password"))
                .build();
        when(mapper.selectOne(any())).thenReturn(account);
        when(mapper.selectById(2L)).thenReturn(account);
        when(mapper.update(isNull(UserAccount.class), any())).thenReturn(1);
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "EDITOR"})
    void shouldReadOwnAccountWithoutSensitiveFields(String role) throws Exception {
        account.setRole(role);
        mockMvc.perform(get("/admin/account")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtService.createToken(account))
                        .param("id", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(2))
                .andExpect(jsonPath("$.data.username").value("Editor"))
                .andExpect(jsonPath("$.data.role").value(role))
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.data.tokenVersion").doesNotExist());
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "EDITOR"})
    void shouldChangeOwnPasswordAndRejectOldToken(String role) throws Exception {
        account.setRole(role);
        String token = jwtService.createToken(account);
        mockMvc.perform(post("/admin/account/change-password")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"old-password\",\"newPassword\":\"new-password\","
                                + "\"id\":1,\"role\":\"ADMIN\",\"enabled\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        assertEquals(8L, account.getTokenVersion());
        assertEquals(role, account.getRole());
        assertTrue(account.getEnabled());
        ArgumentCaptor<LambdaUpdateWrapper<UserAccount>> captor = ArgumentCaptor.captor();
        verify(mapper).update(isNull(UserAccount.class), captor.capture());
        assertTrue(captor.getValue().getSqlSegment().contains("id ="));
        assertTrue(captor.getValue().getParamNameValuePairs().values().containsAll(List.of(2L, 7L)));
        assertFalse(captor.getValue().getSqlSet().contains("role="));
        verify(mapper, never()).selectById(1L);
        mockMvc.perform(get("/admin/account").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/admin/account")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtService.createToken(account)))
                .andExpect(status().isOk());
    }

    @Test
    void shouldSendCodeWithoutInvalidatingLogin() throws Exception {
        String token = jwtService.createToken(account);
        mockMvc.perform(post("/admin/account/email/code")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"purpose\":\"BIND\",\"email\":\"New@Example.com\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        verify(emailVerificationService).sendCode(2L, 7L, "BIND", "New@Example.com");
        assertEquals(7L, account.getTokenVersion());
        mockMvc.perform(get("/admin/account").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void shouldBindEmailAndInvalidateOldToken() throws Exception {
        String token = jwtService.createToken(account);
        mockMvc.perform(post("/admin/account/email")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"New@Example.com\",\"newEmailCode\":\"123456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        verify(emailVerificationService).verifyAndConsume(2L, 7L, "BIND", "New@Example.com", "123456");
        assertEquals(8L, account.getTokenVersion());
        mockMvc.perform(get("/admin/account").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldUnbindEmailAndInvalidateOldToken() throws Exception {
        account.setEmail("Old@Example.com");
        String token = jwtService.createToken(account);
        mockMvc.perform(post("/admin/account/email/unbind")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"verificationCode\":\"654321\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        verify(emailVerificationService).verifyAndConsume(2L, 7L, "UNBIND", "Old@Example.com", "654321");
        assertEquals(8L, account.getTokenVersion());
        mockMvc.perform(get("/admin/account").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldReportWrongCurrentPasswordWithoutUpdating() throws Exception {
        mockMvc.perform(post("/admin/account/change-password")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtService.createToken(account))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"wrong\",\"newPassword\":\"new-password\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("当前密码错误"));
        verify(mapper, never()).update(isNull(UserAccount.class), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/admin/account/change-password", "/admin/account/email/code",
            "/admin/account/email", "/admin/account/email/unbind", "/admin/account/email/verify-current"})
    void shouldRejectAnonymousMutation(String path) throws Exception {
        mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(mapper, emailVerificationService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/admin/account/change-password", "/admin/account/email/code",
            "/admin/account/email", "/admin/account/email/unbind", "/admin/account/email/verify-current"})
    void shouldRejectUserRoleMutation(String path) throws Exception {
        account.setRole("USER");
        mockMvc.perform(post(path)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtService.createToken(account))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        verify(mapper, never()).selectById(any());
        verifyNoInteractions(emailVerificationService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/admin/account/change-password", "/admin/account/email/code",
            "/admin/account/email", "/admin/account/email/unbind", "/admin/account/email/verify-current"})
    void shouldValidateMissingRequiredFields(String path) throws Exception {
        mockMvc.perform(post(path)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtService.createToken(account))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500));
        verify(mapper, never()).selectById(any());
        verifyNoInteractions(emailVerificationService);
    }

    @Test
    void shouldRejectInvalidEmailAndCodeFormat() throws Exception {
        mockMvc.perform(post("/admin/account/email")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtService.createToken(account))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"invalid\",\"newEmailCode\":\"abc\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500));
        verify(mapper, never()).selectById(any());
        verifyNoInteractions(emailVerificationService);
    }

    @Test
    void shouldRejectAnonymousAccountDetails() throws Exception {
        mockMvc.perform(get("/admin/account")).andExpect(status().isUnauthorized());
        verifyNoInteractions(mapper);
    }

    @Test
    void shouldRejectDisabledAccount() throws Exception {
        String token = jwtService.createToken(account);
        account.setEnabled(false);
        mockMvc.perform(get("/admin/account").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "EDITOR"})
    void shouldChangeMailboxInTwoStagesWithoutPassword(String role) throws Exception {
        account.setRole(role);
        account.setEmail("Old@Example.com");
        String token = jwtService.createToken(account);
        String proof = "a".repeat(43);
        when(emailVerificationService.createEmailChangeToken(2L, 7L, "Old@Example.com")).thenReturn(proof);

        mockMvc.perform(post("/admin/account/email/code")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"purpose\":\"CHANGE\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
        mockMvc.perform(post("/admin/account/email/verify-current")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"currentEmailCode\":\"654321\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.emailChangeToken").value(proof))
                .andExpect(jsonPath("$.data.expiresIn").value(300));
        assertEquals("Old@Example.com", account.getEmail());
        assertEquals(7L, account.getTokenVersion());
        verify(mapper, never()).update(isNull(UserAccount.class), any());

        mockMvc.perform(post("/admin/account/email/code")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"purpose\":\"BIND\",\"email\":\"New@Example.com\",\"emailChangeToken\":\"" + proof + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
        mockMvc.perform(post("/admin/account/email")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"New@Example.com\",\"newEmailCode\":\"123456\",\"emailChangeToken\":\"" + proof + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));

        var order = inOrder(emailVerificationService);
        order.verify(emailVerificationService).sendCode(2L, 7L, "CHANGE", "Old@Example.com");
        order.verify(emailVerificationService).verifyAndConsume(2L, 7L, "CHANGE", "Old@Example.com", "654321");
        order.verify(emailVerificationService).createEmailChangeToken(2L, 7L, "Old@Example.com");
        order.verify(emailVerificationService).verifyEmailChangeToken(2L, 7L, "Old@Example.com", proof, false);
        order.verify(emailVerificationService).sendCode(2L, 7L, "BIND", "New@Example.com");
        order.verify(emailVerificationService).verifyEmailChangeToken(2L, 7L, "Old@Example.com", proof, false);
        order.verify(emailVerificationService).verifyAndConsume(2L, 7L, "BIND", "New@Example.com", "123456");
        order.verify(emailVerificationService).verifyEmailChangeToken(2L, 7L, "Old@Example.com", proof, true);
        assertEquals(8L, account.getTokenVersion());
        mockMvc.perform(get("/admin/account").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/admin/account/email/code", "/admin/account/email"})
    void shouldRejectChangingEmailBeforeOldEmailVerification(String path) throws Exception {
        account.setEmail("Old@Example.com");
        doThrow(new BusinessException("请先验证原邮箱后再换绑")).when(emailVerificationService)
                .verifyEmailChangeToken(2L, 7L, "Old@Example.com", null, false);
        mockMvc.perform(post(path)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtService.createToken(account))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"purpose\":\"BIND\",\"email\":\"New@Example.com\",\"newEmailCode\":\"123456\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("请先验证原邮箱后再换绑"));
        verify(mapper, never()).update(isNull(UserAccount.class), any());
        verify(emailVerificationService, never()).sendCode(2L, 7L, "BIND", "New@Example.com");
        verify(emailVerificationService, never()).verifyAndConsume(2L, 7L, "BIND", "New@Example.com", "123456");
    }

    @Test
    void shouldStillRequireOldPasswordWhenChangingPassword() throws Exception {
        mockMvc.perform(post("/admin/account/change-password")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtService.createToken(account))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"newPassword\":\"new-password\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("当前密码不能为空"));
        verify(mapper, never()).update(isNull(UserAccount.class), any());
    }

    @TestConfiguration
    @EnableWebMvc
    @EnableWebSecurity
    @Import({SecurityConfig.class, JwtConfig.class, CurrentAccountController.class,
            UserAccountServiceImpl.class, JwtServiceImpl.class, GlobalExceptionHandler.class})
    static class TestConfig {
    }
}
