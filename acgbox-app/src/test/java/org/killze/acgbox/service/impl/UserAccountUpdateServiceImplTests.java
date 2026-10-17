package org.killze.acgbox.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.killze.acgbox.dto.user.UserAccountDTO;
import org.killze.acgbox.dto.user.UserAccountPasswordDTO;
import org.killze.acgbox.entity.user.UserAccount;
import org.killze.acgbox.exception.BusinessException;
import org.killze.acgbox.mapper.UserAccountMapper;
import org.killze.acgbox.vo.user.UserAccountVO;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 用户账号编辑、管理员保护与密码重置测试，不连接真实数据库。
 */
class UserAccountUpdateServiceImplTests {

    private UserAccountServiceImpl userAccountService;
    private UserAccountMapper userAccountMapper;
    private BCryptPasswordEncoder passwordEncoder;
    private UserAccount userAccount;
    private UserAccount admin;
    private UserAccountDTO userAccountDTO;
    private UserAccountPasswordDTO passwordDTO;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), UserAccount.class
        );
        userAccountMapper = mock(UserAccountMapper.class);
        passwordEncoder = new BCryptPasswordEncoder(4);
        userAccountService = new UserAccountServiceImpl();
        ReflectionTestUtils.setField(userAccountService, "userAccountMapper", userAccountMapper);
        ReflectionTestUtils.setField(userAccountService, "passwordEncoder", passwordEncoder);

        admin = UserAccount.builder()
                .id(1L).username("admin").email("Admin@Example.com")
                .passwordHash(passwordEncoder.encode("admin-password"))
                .role("ADMIN").enabled(true).tokenVersion(3L)
                .build();
        userAccount = UserAccount.builder()
                .id(2L).username("EditorCase").email("Editor@Example.com")
                .passwordHash(passwordEncoder.encode("user-password"))
                .role("EDITOR").enabled(true).tokenVersion(7L)
                .emailVerifiedAt(LocalDateTime.of(2026, 10, 1, 12, 0))
                .createdAt(LocalDateTime.of(2026, 9, 1, 12, 0))
                .updatedAt(LocalDateTime.of(2026, 10, 2, 12, 0))
                .build();
        when(userAccountMapper.selectById(1L)).thenReturn(admin);
        when(userAccountMapper.selectById(2L)).thenReturn(userAccount);
        when(userAccountMapper.update(isNull(UserAccount.class), any())).thenReturn(1);

        userAccountDTO = new UserAccountDTO();
        userAccountDTO.setId(2L);
        userAccountDTO.setUsername(userAccount.getUsername());
        userAccountDTO.setEmail(userAccount.getEmail());
        userAccountDTO.setRole("EDITOR");
        userAccountDTO.setEnabled(true);
        passwordDTO = new UserAccountPasswordDTO();
        passwordDTO.setId(2L);
        passwordDTO.setCurrentPassword("admin-password");
        passwordDTO.setNewPassword("new-user-password");
    }

    @Test
    void shouldUpdateUsernameAndIncrementVersionAtomically() {
        userAccountDTO.setUsername(" NewEditor ");

        UserAccountVO result = userAccountService.updateUserAccount(userAccountDTO, 1L);

        assertEquals("NewEditor", result.getUsername());
        assertEquals(8L, userAccount.getTokenVersion());
        assertEquals(userAccount.getCreatedAt(), result.getCreatedAt());
        LambdaUpdateWrapper<UserAccount> wrapper = captureUpdate();
        assertTrue(wrapper.getSqlSet().contains("username="));
        assertTrue(wrapper.getSqlSet().contains("token_version = token_version + 1"));
        assertTrue(wrapper.getSqlSegment().contains("id ="));
        assertTrue(wrapper.getSqlSegment().contains("token_version ="));
        assertTrue(wrapper.getParamNameValuePairs().values().containsAll(List.of("NewEditor", 2L, 7L)));
        assertFalse(wrapper.getSqlSet().contains("created_at="));
        verify(userAccountMapper, never()).selectById(1L);
    }

    @Test
    void shouldCheckUsernameUniquenessWithoutChangingItsCase() {
        userAccountDTO.setUsername("EDITORCASE");
        when(userAccountMapper.exists(any())).thenAnswer(invocation -> {
            LambdaQueryWrapper<UserAccount> query = invocation.getArgument(0);
            assertTrue(query.getSqlSegment().contains("lower(username) = lower("));
            assertTrue(query.getSqlSegment().contains("id <>"));
            assertTrue(query.getParamNameValuePairs().values().containsAll(List.of("EDITORCASE", 2L)));
            return false;
        });

        assertEquals("EDITORCASE", userAccountService.updateUserAccount(userAccountDTO, 1L).getUsername());
    }

    @Test
    void shouldRejectDuplicateUsername() {
        userAccountDTO.setUsername("OtherUser");
        when(userAccountMapper.exists(any())).thenReturn(true);

        assertUpdateError("此登录用户名已存在");
        assertNoUpdate();
    }

    @Test
    void shouldChangeEmailAfterVerifyingAdminPasswordAndClearVerification() {
        userAccountDTO.setEmail("NewEmail@Example.com");
        userAccountDTO.setCurrentPassword("admin-password");
        when(userAccountMapper.exists(any())).thenAnswer(invocation -> {
            LambdaQueryWrapper<UserAccount> query = invocation.getArgument(0);
            assertTrue(query.getSqlSegment().contains("email ="));
            assertFalse(query.getSqlSegment().contains("lower(email)"));
            assertTrue(query.getParamNameValuePairs().containsValue("NewEmail@Example.com"));
            return false;
        });

        UserAccountVO result = userAccountService.updateUserAccount(userAccountDTO, 1L);

        assertEquals("NewEmail@Example.com", result.getEmail());
        assertNull(result.getEmailVerifiedAt());
        assertEquals(8L, userAccount.getTokenVersion());
        assertTrue(captureUpdate().getSqlSet().contains("email_verified_at="));
        verify(userAccountMapper).selectById(1L);
    }

    @ParameterizedTest
    @NullAndEmptySource
    void shouldAllowClearingEmailAfterReauthentication(String email) {
        userAccountDTO.setEmail(email);
        userAccountDTO.setCurrentPassword("admin-password");

        UserAccountVO result = userAccountService.updateUserAccount(userAccountDTO, 1L);

        assertNull(result.getEmail());
        assertNull(result.getEmailVerifiedAt());
        assertEquals(8L, userAccount.getTokenVersion());
        assertTrue(captureUpdate().getSqlSet().contains("email="));
    }

    @Test
    void shouldRejectDuplicateEmail() {
        userAccountDTO.setEmail("Other@Example.com");
        userAccountDTO.setCurrentPassword("admin-password");
        when(userAccountMapper.exists(any())).thenReturn(true);

        assertUpdateError("此邮箱已被使用");
        assertNoUpdate();
    }

    @Test
    void shouldNotIncrementVersionWhenAccountIsUnchanged() {
        LocalDateTime updatedAt = userAccount.getUpdatedAt();

        userAccountService.updateUserAccount(userAccountDTO, 1L);

        assertEquals(7L, userAccount.getTokenVersion());
        assertEquals(updatedAt, userAccount.getUpdatedAt());
        verify(userAccountMapper, never()).selectById(1L);
        assertNoUpdate();
    }

    @ParameterizedTest
    @CsvSource({"EDITOR,true", "USER,true", "ADMIN,false"})
    void shouldRejectAdminDemotionOrDisabling(String role, boolean enabled) {
        userAccountDTO.setId(1L);
        userAccountDTO.setUsername(admin.getUsername());
        userAccountDTO.setEmail(admin.getEmail());
        userAccountDTO.setRole(role);
        userAccountDTO.setEnabled(enabled);

        assertUpdateError("管理员不能降级或禁用");
        assertEquals(3L, admin.getTokenVersion());
        assertNoUpdate();
    }

    @ParameterizedTest
    @ValueSource(strings = {"EDITOR", "USER"})
    void shouldRejectPromotionToAdmin(String role) {
        userAccount.setRole(role);
        userAccountDTO.setRole("ADMIN");

        assertUpdateError("不允许将其他账号修改为管理员");
        assertNoUpdate();
    }

    @ParameterizedTest
    @CsvSource({"USER,true", "EDITOR,false"})
    void shouldIncrementVersionForRoleOrEnabledChanges(String role, boolean enabled) {
        userAccountDTO.setRole(role);
        userAccountDTO.setEnabled(enabled);

        UserAccountVO result = userAccountService.updateUserAccount(userAccountDTO, 1L);

        assertEquals(role, result.getRole());
        assertEquals(enabled, result.getEnabled());
        assertEquals(8L, userAccount.getTokenVersion());
        captureUpdate();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = " ")
    void shouldRequireAdminPasswordWhenEmailChanges(String currentPassword) {
        userAccountDTO.setEmail("new@example.com");
        userAccountDTO.setCurrentPassword(currentPassword);

        assertUpdateError("管理员密码不能为空");
        assertNoUpdate();
    }

    @ParameterizedTest
    @ValueSource(strings = {"wrong", "user-password", " admin-password", "admin-password "})
    void shouldVerifyOperatorPasswordWithoutTrimmingOrUsingTargetPassword(String currentPassword) {
        userAccountDTO.setEmail("new@example.com");
        userAccountDTO.setCurrentPassword(currentPassword);

        assertUpdateError("管理员密码错误");
        assertNoUpdate();
    }

    @ParameterizedTest
    @ValueSource(strings = {"EDITOR", "USER"})
    void shouldRejectNonAdminOperatorDuringReauthentication(String role) {
        admin.setRole(role);
        userAccountDTO.setEmail("new@example.com");
        userAccountDTO.setCurrentPassword("admin-password");

        assertUpdateError("管理员身份已失效，请重新登录");
        assertNoUpdate();
    }

    @Test
    void shouldRejectDisabledOperatorDuringReauthentication() {
        admin.setEnabled(false);
        userAccountDTO.setEmail("new@example.com");
        userAccountDTO.setCurrentPassword("admin-password");

        assertUpdateError("管理员身份已失效，请重新登录");
        assertNoUpdate();
    }

    @Test
    void shouldResetPasswordUsingBcryptAndInvalidateOldTokens() {
        userAccountService.resetUserAccountPassword(passwordDTO, 1L);

        LambdaUpdateWrapper<UserAccount> wrapper = captureUpdate();
        String passwordHash = wrapper.getParamNameValuePairs().values().stream()
                .filter(String.class::isInstance).map(String.class::cast)
                .filter(value -> value.startsWith("$2")).findFirst().orElseThrow();
        assertNotEquals(passwordDTO.getNewPassword(), passwordHash);
        assertTrue(passwordEncoder.matches(passwordDTO.getNewPassword(), passwordHash));
        assertTrue(wrapper.getSqlSet().contains("password_hash="));
        assertTrue(wrapper.getSqlSet().contains("token_version = token_version + 1"));
        assertEquals(8L, userAccount.getTokenVersion());
    }

    @Test
    void shouldAllowAdminToResetOwnPassword() {
        passwordDTO.setId(1L);

        userAccountService.resetUserAccountPassword(passwordDTO, 1L);

        assertEquals(4L, admin.getTokenVersion());
        assertEquals("ADMIN", admin.getRole());
        assertTrue(admin.getEnabled());
        captureUpdate();
    }

    @Test
    void shouldNotIncrementVersionWhenNewPasswordMatchesExistingPassword() {
        passwordDTO.setNewPassword("user-password");

        userAccountService.resetUserAccountPassword(passwordDTO, 1L);

        assertEquals(7L, userAccount.getTokenVersion());
        assertNoUpdate();
    }

    @Test
    void shouldRejectWrongAdminPasswordForReset() {
        passwordDTO.setCurrentPassword("user-password");

        BusinessException exception = assertThrows(BusinessException.class,
                () -> userAccountService.resetUserAccountPassword(passwordDTO, 1L));

        assertEquals("管理员密码错误", exception.getMessage());
        assertNoUpdate();
    }

    @Test
    void shouldRejectPasswordExceedingBcryptByteLimit() {
        passwordDTO.setNewPassword("密".repeat(25));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> userAccountService.resetUserAccountPassword(passwordDTO, 1L));

        assertEquals("新密码不能超过 72 个 UTF-8 字节", exception.getMessage());
        assertNoUpdate();
    }

    @Test
    void shouldAcceptPasswordAtBcryptByteLimit() {
        passwordDTO.setNewPassword("密".repeat(24));

        userAccountService.resetUserAccountPassword(passwordDTO, 1L);

        assertEquals(8L, userAccount.getTokenVersion());
        captureUpdate();
    }

    @Test
    void shouldRejectConcurrentVersionConflictWithoutChangingResult() {
        userAccountDTO.setUsername("new-editor");
        when(userAccountMapper.update(isNull(UserAccount.class), any())).thenReturn(0);

        assertUpdateError("账号信息已发生变化，请刷新后重试");
        assertEquals(7L, userAccount.getTokenVersion());
        assertEquals("EditorCase", userAccount.getUsername());
    }

    @Test
    void shouldConvertDatabaseUniquenessConflictToBusinessError() {
        userAccountDTO.setUsername("new-editor");
        when(userAccountMapper.update(isNull(UserAccount.class), any()))
                .thenThrow(new DuplicateKeyException("duplicate"));

        assertUpdateError("登录用户名或邮箱已被使用");
        assertEquals(7L, userAccount.getTokenVersion());
    }

    @Test
    void shouldRejectUnknownTargetAccount() {
        userAccountDTO.setId(999L);

        assertUpdateError("此用户账号不存在");
        assertNoUpdate();
    }

    @Test
    void shouldExcludeBothPasswordsFromDtoText() {
        userAccountDTO.setCurrentPassword("admin-password");

        assertFalse(userAccountDTO.toString().contains("admin-password"));
        assertFalse(passwordDTO.toString().contains("admin-password"));
        assertFalse(passwordDTO.toString().contains("new-user-password"));
    }

    @Test
    void shouldWrapBothMutationMethodsInTransactions() throws Exception {
        assertNotNull(UserAccountServiceImpl.class.getMethod("updateUserAccount", UserAccountDTO.class, Long.class)
                .getAnnotation(Transactional.class));
        assertNotNull(UserAccountServiceImpl.class.getMethod("resetUserAccountPassword", UserAccountPasswordDTO.class, Long.class)
                .getAnnotation(Transactional.class));
    }

    private LambdaUpdateWrapper<UserAccount> captureUpdate() {
        ArgumentCaptor<LambdaUpdateWrapper<UserAccount>> captor = ArgumentCaptor.captor();
        verify(userAccountMapper).update(isNull(UserAccount.class), captor.capture());
        return captor.getValue();
    }

    private void assertNoUpdate() {
        verify(userAccountMapper, never()).update(isNull(UserAccount.class), any());
    }

    private void assertUpdateError(String message) {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> userAccountService.updateUserAccount(userAccountDTO, 1L));
        assertEquals(message, exception.getMessage());
    }
}
