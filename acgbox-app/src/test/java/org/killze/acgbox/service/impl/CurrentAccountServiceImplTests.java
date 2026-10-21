package org.killze.acgbox.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.killze.acgbox.dto.user.UserAccountChangePasswordDTO;
import org.killze.acgbox.dto.user.UserAccountEmailCodeDTO;
import org.killze.acgbox.dto.user.UserAccountEmailDTO;
import org.killze.acgbox.dto.user.UserAccountUnbindEmailDTO;
import org.killze.acgbox.dto.user.UserAccountVerifyEmailDTO;
import org.killze.acgbox.entity.user.UserAccount;
import org.killze.acgbox.exception.BusinessException;
import org.killze.acgbox.mapper.UserAccountMapper;
import org.killze.acgbox.service.EmailVerificationService;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * 当前账号编辑服务测试，不连接真实数据库、Redis 或邮件服务器。
 */
class CurrentAccountServiceImplTests {

    private UserAccountServiceImpl service;
    private UserAccountMapper mapper;
    private EmailVerificationService emailVerificationService;
    private BCryptPasswordEncoder passwordEncoder;
    private UserAccount account;
    private UserAccountChangePasswordDTO passwordDTO;
    private UserAccountEmailCodeDTO codeDTO;
    private UserAccountEmailDTO emailDTO;
    private UserAccountUnbindEmailDTO unbindDTO;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), UserAccount.class);
        mapper = mock(UserAccountMapper.class);
        emailVerificationService = mock(EmailVerificationService.class);
        passwordEncoder = new BCryptPasswordEncoder(4);
        service = new UserAccountServiceImpl();
        ReflectionTestUtils.setField(service, "userAccountMapper", mapper);
        ReflectionTestUtils.setField(service, "passwordEncoder", passwordEncoder);
        ReflectionTestUtils.setField(service, "emailVerificationService", emailVerificationService);
        account = UserAccount.builder().id(2L).username("Editor").role("EDITOR").enabled(true)
                .email("Old@Example.com").emailVerifiedAt(LocalDateTime.now())
                .passwordHash(passwordEncoder.encode("old-password")).tokenVersion(7L).build();
        when(mapper.selectById(2L)).thenReturn(account);
        when(mapper.update(isNull(UserAccount.class), any())).thenReturn(1);
        passwordDTO = new UserAccountChangePasswordDTO();
        passwordDTO.setCurrentPassword("old-password");
        passwordDTO.setNewPassword("new-password");
        codeDTO = new UserAccountEmailCodeDTO();
        codeDTO.setPurpose("BIND");
        codeDTO.setEmail("New@Example.com");
        codeDTO.setEmailChangeToken("a".repeat(43));
        emailDTO = new UserAccountEmailDTO();
        emailDTO.setEmail("New@Example.com");
        emailDTO.setNewEmailCode("123456");
        emailDTO.setEmailChangeToken("a".repeat(43));
        unbindDTO = new UserAccountUnbindEmailDTO();
        unbindDTO.setVerificationCode("654321");
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "EDITOR"})
    void shouldChangeOwnPasswordAndIncrementVersion(String role) {
        account.setRole(role);
        passwordDTO.setNewPassword(" New-Password ");

        service.changeCurrentUserAccountPassword(passwordDTO, 2L, 7L);

        LambdaUpdateWrapper<UserAccount> update = captureUpdate();
        String hash = update.getParamNameValuePairs().values().stream()
                .filter(value -> value instanceof String && ((String) value).startsWith("$2"))
                .map(String.class::cast).findFirst().orElseThrow();
        assertTrue(passwordEncoder.matches(" New-Password ", hash));
        assertFalse(passwordEncoder.matches("New-Password", hash));
        assertEquals(8L, account.getTokenVersion());
        assertEquals(role, account.getRole());
        assertFalse(update.getSqlSet().contains("email="));
        verifyNoInteractions(emailVerificationService);
    }

    @Test
    void shouldKeepVersionWhenPasswordDoesNotChange() {
        passwordDTO.setNewPassword("old-password");
        service.changeCurrentUserAccountPassword(passwordDTO, 2L, 7L);
        assertEquals(7L, account.getTokenVersion());
        assertNoUpdate();
    }

    @ParameterizedTest
    @ValueSource(strings = {"wrong", "old-password ", " old-password"})
    void shouldRejectWrongCurrentPassword(String password) {
        passwordDTO.setCurrentPassword(password);
        assertError("当前密码错误", () -> service.changeCurrentUserAccountPassword(passwordDTO, 2L, 7L));
        assertNoUpdate();
    }

    @Test
    void shouldRejectPasswordOverBcryptByteLimit() {
        passwordDTO.setNewPassword("密".repeat(25));
        assertError("新密码不能超过 72 个 UTF-8 字节",
                () -> service.changeCurrentUserAccountPassword(passwordDTO, 2L, 7L));
        assertNoUpdate();
    }

    @Test
    void shouldAcceptPasswordAtBcryptByteLimit() {
        passwordDTO.setNewPassword("密".repeat(24));
        service.changeCurrentUserAccountPassword(passwordDTO, 2L, 7L);
        captureUpdate();
    }

    @Test
    void shouldRejectVersionChangedAfterAuthentication() {
        account.setTokenVersion(8L);
        assertError("登录状态已失效，请重新登录",
                () -> service.changeCurrentUserAccountPassword(passwordDTO, 2L, 7L));
        assertNoUpdate();
    }

    @Test
    void shouldRejectDisabledAccount() {
        account.setEnabled(false);
        assertError("登录状态已失效，请重新登录",
                () -> service.sendCurrentUserAccountEmailCode(codeDTO, 2L, 7L));
        verifyNoInteractions(emailVerificationService);
    }

    @Test
    void shouldRejectUserRole() {
        account.setRole("USER");
        assertError("登录状态已失效，请重新登录",
                () -> service.unbindCurrentUserAccountEmail(unbindDTO, 2L, 7L));
        assertNoUpdate();
    }

    @Test
    void shouldRejectMissingAccount() {
        when(mapper.selectById(2L)).thenReturn(null);
        assertError("此用户账号不存在", () -> service.bindCurrentUserAccountEmail(emailDTO, 2L, 7L));
        verifyNoInteractions(emailVerificationService);
    }

    @Test
    void shouldSendNewMailboxCodeWithoutChangingItsCase() {
        codeDTO.setEmail(" New@Example.com ");
        service.sendCurrentUserAccountEmailCode(codeDTO, 2L, 7L);
        var order = inOrder(emailVerificationService);
        order.verify(emailVerificationService).verifyEmailChangeToken(2L, 7L, "Old@Example.com", "a".repeat(43), false);
        order.verify(emailVerificationService).sendCode(2L, 7L, "BIND", "New@Example.com");
        assertNoUpdate();
    }

    @ParameterizedTest
    @ValueSource(strings = {"CHANGE", "UNBIND"})
    void shouldSendOldMailboxCodeToDatabaseEmailOnly(String purpose) {
        codeDTO.setPurpose(purpose);
        codeDTO.setEmail("attacker@example.com");
        service.sendCurrentUserAccountEmailCode(codeDTO, 2L, 7L);
        verify(emailVerificationService).sendCode(2L, 7L, purpose, "Old@Example.com");
    }

    @Test
    void shouldRejectInvalidPurpose() {
        codeDTO.setPurpose("LOGIN");
        assertError("验证码用途仅支持 BIND、CHANGE、UNBIND",
                () -> service.sendCurrentUserAccountEmailCode(codeDTO, 2L, 7L));
        verifyNoInteractions(emailVerificationService);
    }

    @Test
    void shouldRejectBlankBindingEmail() {
        codeDTO.setEmail(" ");
        assertError("邮箱不能为空", () -> service.sendCurrentUserAccountEmailCode(codeDTO, 2L, 7L));
        verifyNoInteractions(emailVerificationService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"CHANGE", "UNBIND"})
    void shouldRejectOldMailboxCodeWhenNoEmailBound(String purpose) {
        account.setEmail(null);
        codeDTO.setPurpose(purpose);
        assertError("当前账号尚未绑定邮箱", () -> service.sendCurrentUserAccountEmailCode(codeDTO, 2L, 7L));
        verifyNoInteractions(emailVerificationService);
    }

    @Test
    void shouldRejectDuplicateEmailBeforeSendingCode() {
        when(mapper.exists(any())).thenReturn(true);
        assertError("此邮箱已被使用", () -> service.sendCurrentUserAccountEmailCode(codeDTO, 2L, 7L));
        verifyNoInteractions(emailVerificationService);
    }

    @Test
    void shouldBindFirstMailboxWithNewMailboxCodeOnly() {
        account.setEmail(null);
        account.setEmailVerifiedAt(null);
        emailDTO.setEmailChangeToken(null);

        service.bindCurrentUserAccountEmail(emailDTO, 2L, 7L);

        verify(emailVerificationService).verifyAndConsume(2L, 7L, "BIND", "New@Example.com", "123456");
        verifyNoMoreInteractions(emailVerificationService);
        LambdaUpdateWrapper<UserAccount> update = captureUpdate();
        assertTrue(update.getSqlSet().contains("email_verified_at="));
        assertTrue(update.getParamNameValuePairs().values().contains("New@Example.com"));
        assertEquals(8L, account.getTokenVersion());
    }

    @Test
    void shouldRequireBothMailboxCodesWhenChangingEmail() {
        service.bindCurrentUserAccountEmail(emailDTO, 2L, 7L);
        var order = inOrder(emailVerificationService);
        order.verify(emailVerificationService).verifyEmailChangeToken(2L, 7L, "Old@Example.com", "a".repeat(43), false);
        order.verify(emailVerificationService).verifyAndConsume(2L, 7L, "BIND", "New@Example.com", "123456");
        order.verify(emailVerificationService).verifyEmailChangeToken(2L, 7L, "Old@Example.com", "a".repeat(43), true);
        captureUpdate();
    }

    @Test
    void shouldRequireOldMailboxCodeEvenWhenOnlyEmailCaseChanges() {
        emailDTO.setEmail("old@example.com");
        emailDTO.setEmailChangeToken(null);
        doThrow(new BusinessException("请先验证原邮箱后再换绑")).when(emailVerificationService)
                .verifyEmailChangeToken(2L, 7L, "Old@Example.com", null, false);
        assertError("请先验证原邮箱后再换绑", () -> service.bindCurrentUserAccountEmail(emailDTO, 2L, 7L));
        assertNoUpdate();
        verify(emailVerificationService, never()).verifyAndConsume(anyLong(), anyLong(), anyString(), anyString(), anyString());
    }

    @Test
    void shouldVerifyExistingUnverifiedMailboxWithoutOldMailboxCode() {
        account.setEmailVerifiedAt(null);
        emailDTO.setEmail(account.getEmail());
        emailDTO.setEmailChangeToken(null);
        service.bindCurrentUserAccountEmail(emailDTO, 2L, 7L);
        verify(emailVerificationService).verifyAndConsume(2L, 7L, "BIND", "Old@Example.com", "123456");
        verifyNoMoreInteractions(emailVerificationService);
        assertEquals(8L, account.getTokenVersion());
    }

    @Test
    void shouldRejectAlreadyVerifiedSameMailbox() {
        emailDTO.setEmail(account.getEmail());
        assertError("此邮箱已绑定并验证", () -> service.bindCurrentUserAccountEmail(emailDTO, 2L, 7L));
        assertNoUpdate();
        verifyNoInteractions(emailVerificationService);
    }

    @Test
    void shouldKeepEmailUniquenessCaseSensitive() {
        when(mapper.exists(any())).thenAnswer(invocation -> {
            LambdaQueryWrapper<UserAccount> query = invocation.getArgument(0);
            assertTrue(query.getSqlSegment().contains("email ="));
            assertFalse(query.getSqlSegment().contains("lower("));
            assertTrue(query.getParamNameValuePairs().values().containsAll(List.of(2L, "New@Example.com")));
            return false;
        });
        service.bindCurrentUserAccountEmail(emailDTO, 2L, 7L);
    }

    @Test
    void shouldNotUpdateWhenVerificationFails() {
        doThrow(new BusinessException("验证码错误或已过期，请重新获取"))
                .when(emailVerificationService).verifyAndConsume(anyLong(), anyLong(), anyString(), anyString(), anyString());
        assertError("验证码错误或已过期，请重新获取", () -> service.bindCurrentUserAccountEmail(emailDTO, 2L, 7L));
        assertNoUpdate();
    }

    @Test
    void shouldNotSendNewMailboxCodeWithoutOldMailboxProof() {
        codeDTO.setEmailChangeToken(null);
        doThrow(new BusinessException("请先验证原邮箱后再换绑")).when(emailVerificationService)
                .verifyEmailChangeToken(2L, 7L, "Old@Example.com", null, false);
        assertError("请先验证原邮箱后再换绑", () -> service.sendCurrentUserAccountEmailCode(codeDTO, 2L, 7L));
        verify(emailVerificationService, never()).sendCode(anyLong(), anyLong(), anyString(), anyString());
    }

    @Test
    void shouldVerifyOldMailboxWithoutChangingAccountOrPassword() {
        UserAccountVerifyEmailDTO dto = new UserAccountVerifyEmailDTO();
        dto.setCurrentEmailCode("654321");
        when(emailVerificationService.createEmailChangeToken(2L, 7L, "Old@Example.com")).thenReturn("a".repeat(43));

        var result = service.verifyCurrentUserAccountEmail(dto, 2L, 7L);

        var order = inOrder(emailVerificationService);
        order.verify(emailVerificationService).verifyAndConsume(2L, 7L, "CHANGE", "Old@Example.com", "654321");
        order.verify(emailVerificationService).createEmailChangeToken(2L, 7L, "Old@Example.com");
        assertEquals("a".repeat(43), result.getEmailChangeToken());
        assertEquals(300L, result.getExpiresIn());
        assertEquals("Old@Example.com", account.getEmail());
        assertEquals(7L, account.getTokenVersion());
        assertNoUpdate();
    }

    @Test
    void shouldNotIssueProofForWrongOldMailboxCode() {
        UserAccountVerifyEmailDTO dto = new UserAccountVerifyEmailDTO();
        dto.setCurrentEmailCode("654321");
        doThrow(new BusinessException("验证码错误或已过期，请重新获取")).when(emailVerificationService)
                .verifyAndConsume(2L, 7L, "CHANGE", "Old@Example.com", "654321");
        assertError("验证码错误或已过期，请重新获取", () -> service.verifyCurrentUserAccountEmail(dto, 2L, 7L));
        verify(emailVerificationService, never()).createEmailChangeToken(anyLong(), anyLong(), anyString());
        assertNoUpdate();
    }

    @Test
    void shouldRejectOldMailboxVerificationWithoutBoundEmail() {
        account.setEmail(null);
        assertError("当前账号尚未绑定邮箱",
                () -> service.verifyCurrentUserAccountEmail(new UserAccountVerifyEmailDTO(), 2L, 7L));
        verifyNoInteractions(emailVerificationService);
    }

    @Test
    void shouldKeepProofWhenNewMailboxCodeIsWrong() {
        doThrow(new BusinessException("验证码错误或已过期，请重新获取")).when(emailVerificationService)
                .verifyAndConsume(2L, 7L, "BIND", "New@Example.com", "123456");
        assertError("验证码错误或已过期，请重新获取", () -> service.bindCurrentUserAccountEmail(emailDTO, 2L, 7L));
        verify(emailVerificationService, never()).verifyEmailChangeToken(2L, 7L, "Old@Example.com", "a".repeat(43), true);
        assertNoUpdate();
    }

    @Test
    void shouldNotUpdateWhenProofExpiresBeforeConsumption() {
        doThrow(new BusinessException("邮箱换绑凭证无效或已过期，请重新验证原邮箱")).when(emailVerificationService)
                .verifyEmailChangeToken(2L, 7L, "Old@Example.com", "a".repeat(43), true);
        assertError("邮箱换绑凭证无效或已过期，请重新验证原邮箱",
                () -> service.bindCurrentUserAccountEmail(emailDTO, 2L, 7L));
        assertNoUpdate();
    }

    @Test
    void shouldUnbindAndExplicitlyWriteNulls() {
        service.unbindCurrentUserAccountEmail(unbindDTO, 2L, 7L);
        verify(emailVerificationService).verifyAndConsume(2L, 7L, "UNBIND", "Old@Example.com", "654321");
        LambdaUpdateWrapper<UserAccount> update = captureUpdate();
        assertTrue(update.getSqlSet().contains("email="));
        assertTrue(update.getSqlSet().contains("email_verified_at="));
        assertEquals(2L, update.getParamNameValuePairs().values().stream().filter(value -> value == null).count());
        assertEquals(8L, account.getTokenVersion());
    }

    @Test
    void shouldRejectUnbindWhenNoEmailBound() {
        account.setEmail(null);
        assertError("当前账号尚未绑定邮箱", () -> service.unbindCurrentUserAccountEmail(unbindDTO, 2L, 7L));
        assertNoUpdate();
        verifyNoInteractions(emailVerificationService);
    }

    @Test
    void shouldRejectConcurrentAccountUpdate() {
        when(mapper.update(isNull(UserAccount.class), any())).thenReturn(0);
        assertError("账号信息已发生变化，请刷新后重试",
                () -> service.changeCurrentUserAccountPassword(passwordDTO, 2L, 7L));
        assertEquals(7L, account.getTokenVersion());
    }

    @Test
    void shouldConvertEmailUniqueIndexConflictToBusinessError() {
        when(mapper.update(isNull(UserAccount.class), any())).thenThrow(new DuplicateKeyException("duplicate"));
        assertError("登录用户名或邮箱已被使用", () -> service.bindCurrentUserAccountEmail(emailDTO, 2L, 7L));
        assertEquals(7L, account.getTokenVersion());
    }

    @Test
    void shouldExcludePasswordsAndCodesFromDtoText() {
        assertFalse(passwordDTO.toString().contains("old-password"));
        assertFalse(passwordDTO.toString().contains("new-password"));
        assertFalse(codeDTO.toString().contains("old-password"));
        assertFalse(emailDTO.toString().contains("123456"));
        assertFalse(emailDTO.toString().contains("654321"));
        assertFalse(emailDTO.toString().contains("old-password"));
        assertFalse(unbindDTO.toString().contains("old-password"));
        assertFalse(unbindDTO.toString().contains("654321"));
    }

    private LambdaUpdateWrapper<UserAccount> captureUpdate() {
        ArgumentCaptor<LambdaUpdateWrapper<UserAccount>> captor = ArgumentCaptor.captor();
        verify(mapper).update(isNull(UserAccount.class), captor.capture());
        LambdaUpdateWrapper<UserAccount> update = captor.getValue();
        assertTrue(update.getSqlSet().contains("token_version = token_version + 1"));
        assertFalse(update.getSqlSet().contains("role="));
        assertFalse(update.getSqlSet().contains("enabled="));
        assertFalse(update.getSqlSet().contains("username="));
        assertTrue(update.getSqlSegment().contains("token_version ="));
        assertTrue(update.getParamNameValuePairs().values().containsAll(List.of(2L, 7L)));
        return update;
    }

    private void assertNoUpdate() {
        verify(mapper, never()).update(isNull(UserAccount.class), any());
    }

    private void assertError(String message, Runnable action) {
        assertEquals(message, assertThrows(BusinessException.class, action::run).getMessage());
    }
}
