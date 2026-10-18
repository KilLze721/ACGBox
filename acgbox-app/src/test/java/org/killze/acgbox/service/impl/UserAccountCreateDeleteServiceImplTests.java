package org.killze.acgbox.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.killze.acgbox.dto.user.UserAccountCreateDTO;
import org.killze.acgbox.dto.user.UserAccountDeleteDTO;
import org.killze.acgbox.entity.user.UserAccount;
import org.killze.acgbox.exception.BusinessException;
import org.killze.acgbox.mapper.UserAccountMapper;
import org.killze.acgbox.vo.user.UserAccountVO;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 用户账号创建与单条删除测试，不连接真实数据库。
 */
class UserAccountCreateDeleteServiceImplTests {

    private UserAccountServiceImpl userAccountService;
    private UserAccountMapper userAccountMapper;
    private BCryptPasswordEncoder passwordEncoder;
    private UserAccount admin;
    private UserAccount userAccount;
    private UserAccountCreateDTO userAccountCreateDTO;
    private UserAccountDeleteDTO userAccountDeleteDTO;

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
                .id(1L).role("ADMIN").enabled(true).tokenVersion(3L)
                .passwordHash(passwordEncoder.encode("admin-password"))
                .build();
        userAccount = UserAccount.builder()
                .id(2L).role("EDITOR").enabled(true).tokenVersion(7L)
                .passwordHash(passwordEncoder.encode("user-password"))
                .build();
        when(userAccountMapper.selectById(1L)).thenReturn(admin);
        when(userAccountMapper.selectById(2L)).thenReturn(userAccount);
        when(userAccountMapper.delete(any())).thenReturn(1);
        when(userAccountMapper.insert(any(UserAccount.class))).thenAnswer(invocation -> {
            UserAccount created = invocation.getArgument(0);
            assertNull(created.getId());
            created.setId(20L);
            return 1;
        });

        userAccountCreateDTO = new UserAccountCreateDTO();
        userAccountCreateDTO.setUsername(" NewEditor ");
        userAccountCreateDTO.setPassword(" InitialPassword! ");
        userAccountCreateDTO.setRole("EDITOR");
        userAccountCreateDTO.setEnabled(true);
        userAccountDeleteDTO = new UserAccountDeleteDTO();
        userAccountDeleteDTO.setId(2L);
        userAccountDeleteDTO.setCurrentPassword("admin-password");
    }

    @ParameterizedTest
    @CsvSource({"EDITOR,true", "EDITOR,false", "USER,true", "USER,false"})
    void shouldCreateOnlySupportedRolesAndInitializeServerFields(String role, boolean enabled) {
        userAccountCreateDTO.setRole(role);
        userAccountCreateDTO.setEnabled(enabled);

        UserAccountVO result = userAccountService.createUserAccount(userAccountCreateDTO);

        ArgumentCaptor<UserAccount> captor = ArgumentCaptor.forClass(UserAccount.class);
        verify(userAccountMapper).insert(captor.capture());
        UserAccount created = captor.getValue();
        assertEquals(20L, result.getId());
        assertEquals("NewEditor", result.getUsername());
        assertEquals(role, result.getRole());
        assertEquals(enabled, result.getEnabled());
        assertEquals(0L, created.getTokenVersion());
        assertNull(created.getEmailVerifiedAt());
        assertNotNull(created.getCreatedAt());
        assertEquals(created.getCreatedAt(), created.getUpdatedAt());
        assertEquals(created.getCreatedAt(), result.getCreatedAt());
        assertNotEquals(userAccountCreateDTO.getPassword(), created.getPasswordHash());
        assertTrue(passwordEncoder.matches(" InitialPassword! ", created.getPasswordHash()));
        assertFalse(passwordEncoder.matches("InitialPassword!", created.getPasswordHash()));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"ADMIN", "ROOT", "editor"})
    void shouldRejectCreatingAdminOrUnsupportedRole(String role) {
        userAccountCreateDTO.setRole(role);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> userAccountService.createUserAccount(userAccountCreateDTO));

        assertEquals("创建账号仅支持 EDITOR、USER 角色", exception.getMessage());
        verifyNoInteractions(userAccountMapper);
    }

    @ParameterizedTest
    @NullAndEmptySource
    void shouldAllowCreatingAccountWithoutEmail(String email) {
        userAccountCreateDTO.setEmail(email);

        UserAccountVO result = userAccountService.createUserAccount(userAccountCreateDTO);

        assertNull(result.getEmail());
        assertNull(result.getEmailVerifiedAt());
    }

    @Test
    void shouldCheckUsernameIgnoringCaseAndEmailUsingOriginalCase() {
        userAccountCreateDTO.setEmail("NewEditor@Example.com");
        when(userAccountMapper.exists(any())).thenAnswer(invocation -> {
            LambdaQueryWrapper<UserAccount> query = invocation.getArgument(0);
            String sql = query.getSqlSegment();
            if (sql.contains("username")) {
                assertTrue(sql.contains("lower(username) = lower("));
                assertTrue(query.getParamNameValuePairs().containsValue("NewEditor"));
            } else {
                assertTrue(sql.contains("email ="));
                assertFalse(sql.contains("lower(email)"));
                assertTrue(query.getParamNameValuePairs().containsValue("NewEditor@Example.com"));
            }
            return false;
        });

        assertEquals("NewEditor@Example.com", userAccountService.createUserAccount(userAccountCreateDTO).getEmail());
    }

    @Test
    void shouldRejectDuplicateUsername() {
        when(userAccountMapper.exists(any())).thenReturn(true);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> userAccountService.createUserAccount(userAccountCreateDTO));

        assertEquals("此登录用户名已存在", exception.getMessage());
        verify(userAccountMapper, never()).insert(any(UserAccount.class));
    }

    @Test
    void shouldRejectDuplicateEmail() {
        userAccountCreateDTO.setEmail("Used@Example.com");
        when(userAccountMapper.exists(any())).thenReturn(false, true);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> userAccountService.createUserAccount(userAccountCreateDTO));

        assertEquals("此邮箱已被使用", exception.getMessage());
        verify(userAccountMapper, never()).insert(any(UserAccount.class));
    }

    @Test
    void shouldConvertInsertUniquenessRaceToBusinessError() {
        when(userAccountMapper.insert(any(UserAccount.class))).thenThrow(new DuplicateKeyException("duplicate"));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> userAccountService.createUserAccount(userAccountCreateDTO));

        assertEquals("登录用户名或邮箱已被使用", exception.getMessage());
    }

    @Test
    void shouldRejectInitialPasswordOverBcryptByteLimit() {
        userAccountCreateDTO.setPassword("密".repeat(25));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> userAccountService.createUserAccount(userAccountCreateDTO));

        assertEquals("密码不能超过 72 个 UTF-8 字节", exception.getMessage());
        verifyNoInteractions(userAccountMapper);
    }

    @Test
    void shouldAllowInitialPasswordAtBcryptByteLimit() {
        userAccountCreateDTO.setPassword("密".repeat(24));

        assertEquals(20L, userAccountService.createUserAccount(userAccountCreateDTO).getId());
    }

    @ParameterizedTest
    @CsvSource({"EDITOR,true", "EDITOR,false", "USER,true", "USER,false"})
    void shouldDeleteOneNonAdminAccountAfterVerifyingOperatorPassword(String role, boolean enabled) {
        userAccount.setRole(role);
        userAccount.setEnabled(enabled);

        userAccountService.deleteUserAccount(userAccountDeleteDTO, 1L);

        ArgumentCaptor<LambdaQueryWrapper<UserAccount>> captor = ArgumentCaptor.captor();
        verify(userAccountMapper).delete(captor.capture());
        LambdaQueryWrapper<UserAccount> query = captor.getValue();
        String sql = query.getSqlSegment();
        assertTrue(sql.contains("id ="));
        assertTrue(sql.contains("role <>"));
        assertTrue(sql.contains("token_version ="));
        assertTrue(query.getParamNameValuePairs().values().containsAll(List.of(2L, "ADMIN", 7L)));
        verify(userAccountMapper).selectById(1L);
        verify(userAccountMapper, never()).update(isNull(UserAccount.class), any());
    }

    @Test
    void shouldRejectDeletingAdminIncludingOperatorAccount() {
        userAccountDeleteDTO.setId(1L);

        assertDeleteError("不允许删除管理员账号");
        assertNoDelete();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = " ")
    void shouldRequireAdminPasswordForDeletion(String password) {
        userAccountDeleteDTO.setCurrentPassword(password);

        assertDeleteError("管理员密码不能为空");
        assertNoDelete();
    }

    @ParameterizedTest
    @ValueSource(strings = {"wrong", "user-password", " admin-password", "admin-password "})
    void shouldNotAcceptTargetPasswordOrTrimOperatorPassword(String password) {
        userAccountDeleteDTO.setCurrentPassword(password);

        assertDeleteError("管理员密码错误");
        assertNoDelete();
    }

    @ParameterizedTest
    @ValueSource(strings = {"EDITOR", "USER"})
    void shouldRejectNonAdminOperatorDuringDeletion(String role) {
        admin.setRole(role);

        assertDeleteError("管理员身份已失效，请重新登录");
        assertNoDelete();
    }

    @Test
    void shouldRejectDisabledAdminDuringDeletion() {
        admin.setEnabled(false);

        assertDeleteError("管理员身份已失效，请重新登录");
        assertNoDelete();
    }

    @Test
    void shouldRejectDeletingUnknownAccount() {
        userAccountDeleteDTO.setId(999L);

        assertDeleteError("此用户账号不存在");
        verify(userAccountMapper, never()).selectById(1L);
        assertNoDelete();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(longs = {0L, -1L})
    void shouldRejectInvalidDeleteId(Long id) {
        userAccountDeleteDTO.setId(id);

        assertDeleteError("用户账号 ID 必须大于等于 1");
        verifyNoInteractions(userAccountMapper);
    }

    @Test
    void shouldRejectDeletionWhenAccountChangedConcurrently() {
        when(userAccountMapper.delete(any())).thenReturn(0);

        assertDeleteError("账号信息已发生变化，请刷新后重试");
    }

    @Test
    void shouldExcludePasswordsFromDtoText() {
        assertFalse(userAccountCreateDTO.toString().contains("InitialPassword!"));
        assertFalse(userAccountDeleteDTO.toString().contains("admin-password"));
    }

    @Test
    void shouldWrapCreationAndDeletionInTransactions() throws Exception {
        assertNotNull(UserAccountServiceImpl.class.getMethod("createUserAccount", UserAccountCreateDTO.class)
                .getAnnotation(Transactional.class));
        assertNotNull(UserAccountServiceImpl.class.getMethod("deleteUserAccount", UserAccountDeleteDTO.class, Long.class)
                .getAnnotation(Transactional.class));
    }

    private void assertDeleteError(String message) {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> userAccountService.deleteUserAccount(userAccountDeleteDTO, 1L));
        assertEquals(message, exception.getMessage());
    }

    private void assertNoDelete() {
        verify(userAccountMapper, never()).delete(any());
    }
}
