package org.killze.acgbox.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.killze.acgbox.dto.user.EmailCodeLoginDTO;
import org.killze.acgbox.entity.user.UserAccount;
import org.killze.acgbox.exception.BusinessException;
import org.killze.acgbox.mapper.UserAccountMapper;
import org.killze.acgbox.service.EmailVerificationService;
import org.killze.acgbox.service.JwtService;
import org.killze.acgbox.vo.user.LoginVO;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 邮箱验证码登录服务测试，复用验证码服务与登录响应，不连接真实基础设施。
 */
class EmailCodeLoginServiceImplTests {

    private LoginServiceImpl service;
    private UserAccountMapper mapper;
    private EmailVerificationService emailVerificationService;
    private JwtService jwtService;
    private PasswordEncoder passwordEncoder;
    private UserAccount account;
    private EmailCodeLoginDTO loginDTO;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), UserAccount.class);
        mapper = mock(UserAccountMapper.class);
        emailVerificationService = mock(EmailVerificationService.class);
        jwtService = mock(JwtService.class);
        passwordEncoder = mock(PasswordEncoder.class);
        service = new LoginServiceImpl();
        ReflectionTestUtils.setField(service, "userAccountMapper", mapper);
        ReflectionTestUtils.setField(service, "emailVerificationService", emailVerificationService);
        ReflectionTestUtils.setField(service, "jwtService", jwtService);
        ReflectionTestUtils.setField(service, "passwordEncoder", passwordEncoder);
        ReflectionTestUtils.setField(service, "expiration", 7200L);
        account = UserAccount.builder().id(2L).username("Editor").email("Editor@Example.com")
                .role("EDITOR").enabled(true).emailVerifiedAt(LocalDateTime.now()).tokenVersion(7L).build();
        when(mapper.selectOne(any())).thenReturn(account);
        when(jwtService.createToken(account)).thenReturn("test-login-token");
        loginDTO = new EmailCodeLoginDTO();
        loginDTO.setEmail("Editor@Example.com");
        loginDTO.setVerificationCode("003821");
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "EDITOR"})
    void shouldSendLoginCodeUsingDatabaseAccountAndVersion(String role) {
        account.setRole(role);
        service.sendLoginEmailCode(loginDTO);
        verify(emailVerificationService).sendCode(2L, 7L, "LOGIN", "Editor@Example.com");
        verifyNoInteractions(jwtService, passwordEncoder);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "EDITOR"})
    void shouldConsumeCodeBeforeIssuingExistingLoginResponse(String role) {
        account.setRole(role);
        LoginVO result = service.loginByEmailCode(loginDTO);
        var order = inOrder(emailVerificationService, jwtService);
        order.verify(emailVerificationService).verifyAndConsume(2L, 7L, "LOGIN", "Editor@Example.com", "003821");
        order.verify(jwtService).createToken(account);
        assertEquals("test-login-token", result.getToken());
        assertEquals(7200L, result.getExpiresIn());
        assertEquals(2L, result.getId());
        assertEquals("Editor", result.getUsername());
        assertEquals(role, result.getRole());
        assertEquals(7L, account.getTokenVersion());
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void shouldReadOnlyNeededFieldsAndKeepEmailCaseSensitive() {
        loginDTO.setEmail(" Editor@Example.com ");
        when(mapper.selectOne(any())).thenAnswer(invocation -> {
            LambdaQueryWrapper<UserAccount> query = invocation.getArgument(0);
            assertEquals("id,username,email,role,enabled,email_verified_at,token_version", query.getSqlSelect());
            assertTrue(query.getSqlSegment().contains("email ="));
            assertFalse(query.getSqlSegment().contains("lower("));
            assertEquals("Editor@Example.com", query.getParamNameValuePairs().get("MPGENVAL1"));
            return account;
        });
        service.sendLoginEmailCode(loginDTO);
    }

    @ParameterizedTest
    @ValueSource(strings = {"unknown", "disabled", "unverified", "USER", "missing-role"})
    void shouldNotSendCodeOrRevealIneligibleAccount(String state) {
        makeIneligible(state);
        service.sendLoginEmailCode(loginDTO);
        verifyNoInteractions(emailVerificationService, jwtService, passwordEncoder);
    }

    @ParameterizedTest
    @ValueSource(strings = {"unknown", "disabled", "unverified", "USER", "missing-role"})
    void shouldReturnSameVerificationErrorForIneligibleAccount(String state) {
        makeIneligible(state);
        assertError("验证码错误或已过期，请重新获取", () -> service.loginByEmailCode(loginDTO));
        verifyNoInteractions(emailVerificationService, jwtService, passwordEncoder);
    }

    @ParameterizedTest
    @ValueSource(strings = {"editor@example.com", "EDITOR@Example.com"})
    void shouldNotLoginWithDifferentEmailCase(String email) {
        loginDTO.setEmail(email);
        when(mapper.selectOne(any())).thenAnswer(invocation -> {
            LambdaQueryWrapper<UserAccount> query = invocation.getArgument(0);
            query.getSqlSegment();
            return account.getEmail().equals(query.getParamNameValuePairs().get("MPGENVAL1")) ? account : null;
        });
        service.sendLoginEmailCode(loginDTO);
        assertError("验证码错误或已过期，请重新获取", () -> service.loginByEmailCode(loginDTO));
        verifyNoInteractions(emailVerificationService, jwtService);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = " ")
    void shouldRequireEmail(String email) {
        loginDTO.setEmail(email);
        assertError("登录邮箱不能为空", () -> service.sendLoginEmailCode(loginDTO));
        assertError("登录邮箱不能为空", () -> service.loginByEmailCode(loginDTO));
        verifyNoInteractions(mapper, emailVerificationService, jwtService);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = " ")
    void shouldRequireCodeOnlyWhenLoggingIn(String code) {
        loginDTO.setVerificationCode(code);
        assertError("登录验证码不能为空", () -> service.loginByEmailCode(loginDTO));
        verifyNoInteractions(mapper, emailVerificationService, jwtService);
        service.sendLoginEmailCode(loginDTO);
        verify(emailVerificationService).sendCode(2L, 7L, "LOGIN", "Editor@Example.com");
    }

    @Test
    void shouldRejectWrongExpiredOrConsumedCodeBeforeSigning() {
        doThrow(new BusinessException("验证码错误或已过期，请重新获取")).when(emailVerificationService)
                .verifyAndConsume(2L, 7L, "LOGIN", "Editor@Example.com", "003821");
        assertError("验证码错误或已过期，请重新获取", () -> service.loginByEmailCode(loginDTO));
        verifyNoInteractions(jwtService, passwordEncoder);
    }

    @Test
    void shouldUseCurrentDatabaseVersionInsteadOfSentVersion() {
        service.sendLoginEmailCode(loginDTO);
        account.setTokenVersion(8L);
        doThrow(new BusinessException("验证码错误或已过期，请重新获取")).when(emailVerificationService)
                .verifyAndConsume(2L, 8L, "LOGIN", "Editor@Example.com", "003821");
        assertError("验证码错误或已过期，请重新获取", () -> service.loginByEmailCode(loginDTO));
        verifyNoInteractions(jwtService);
    }

    @Test
    void shouldNotSignWhenVerificationServiceUnavailable() {
        doThrow(new BusinessException("验证码服务暂不可用，请稍后重试")).when(emailVerificationService)
                .verifyAndConsume(2L, 7L, "LOGIN", "Editor@Example.com", "003821");
        assertError("验证码服务暂不可用，请稍后重试", () -> service.loginByEmailCode(loginDTO));
        verifyNoInteractions(jwtService);
    }

    @Test
    void shouldExcludeVerificationCodeFromDtoText() {
        assertFalse(loginDTO.toString().contains("003821"));
    }

    private void makeIneligible(String state) {
        switch (state) {
            case "unknown" -> when(mapper.selectOne(any())).thenReturn(null);
            case "disabled" -> account.setEnabled(false);
            case "unverified" -> account.setEmailVerifiedAt(null);
            case "missing-role" -> account.setRole(null);
            default -> account.setRole("USER");
        }
    }

    private void assertError(String message, Runnable action) {
        assertEquals(message, assertThrows(BusinessException.class, action::run).getMessage());
    }
}
