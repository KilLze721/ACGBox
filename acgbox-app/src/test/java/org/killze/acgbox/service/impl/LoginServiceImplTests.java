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
import org.killze.acgbox.dto.user.LoginDTO;
import org.killze.acgbox.entity.user.UserAccount;
import org.killze.acgbox.exception.BusinessException;
import org.killze.acgbox.mapper.UserAccountMapper;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 管理端密码登录服务测试。
 */
class LoginServiceImplTests {

    private LoginServiceImpl loginService;
    private UserAccountMapper userAccountMapper;
    private UserAccount userAccount;
    private LoginDTO loginDTO;

    @BeforeEach
    void setUp() {
        // 初始化实体映射，供 Lambda 查询解析字段名称。
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), UserAccount.class
        );
        userAccountMapper = mock(UserAccountMapper.class);
        BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);
        loginService = new LoginServiceImpl();
        ReflectionTestUtils.setField(loginService, "userAccountMapper", userAccountMapper);
        ReflectionTestUtils.setField(loginService, "passwordEncoder", passwordEncoder);

        userAccount = UserAccount.builder()
                .id(1L)
                .username("admin")
                .email("admin@example.com")
                .passwordHash(passwordEncoder.encode("123456"))
                .role("ADMIN")
                .enabled(true)
                .build();
        when(userAccountMapper.selectOne(any())).thenReturn(userAccount);

        loginDTO = new LoginDTO();
        loginDTO.setUsername("admin");
        loginDTO.setEmail("admin@example.com");
        loginDTO.setPassword("123456");
    }

    @Test
    void shouldQueryByUsernameWithoutSwitchingToEmail() {
        loginDTO.setUsername(" AdMiN ");
        when(userAccountMapper.selectOne(any())).thenAnswer(invocation -> {
            LambdaQueryWrapper<UserAccount> query = invocation.getArgument(0);
            assertTrue(query.getSqlSegment().contains("lower(username) = lower("));
            assertEquals("AdMiN", query.getParamNameValuePairs().get("MPGENVAL1"));
            return userAccount;
        });

        assertSame(userAccount, loginService.verifyByUsername(loginDTO));
    }

    @Test
    void shouldQueryByEmailWithoutSwitchingToUsername() {
        userAccount.setEmail("ADMIN@Example.com");
        loginDTO.setEmail(" ADMIN@Example.com ");
        when(userAccountMapper.selectOne(any())).thenAnswer(invocation -> {
            LambdaQueryWrapper<UserAccount> query = invocation.getArgument(0);
            assertTrue(query.getSqlSegment().contains("email ="));
            assertEquals("ADMIN@Example.com", query.getParamNameValuePairs().get("MPGENVAL1"));
            return userAccount;
        });

        assertSame(userAccount, loginService.verifyByEmail(loginDTO));
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN@example.com", "admin@Example.com", "ADMIN@EXAMPLE.COM"})
    void shouldRejectEmailWithDifferentCase(String email) {
        loginDTO.setEmail(email);
        when(userAccountMapper.selectOne(any())).thenAnswer(invocation -> {
            LambdaQueryWrapper<UserAccount> query = invocation.getArgument(0);
            assertTrue(query.getSqlSegment().contains("email ="));
            return userAccount.getEmail().equals(query.getParamNameValuePairs().get("MPGENVAL1"))
                    ? userAccount : null;
        });

        BusinessException exception = assertThrows(
                BusinessException.class, () -> loginService.verifyByEmail(loginDTO)
        );

        assertEquals("账号或密码错误", exception.getMessage());
    }

    @Test
    void shouldAllowUsernameLoginWithoutEmail() {
        userAccount.setEmail(null);
        loginDTO.setEmail(null);

        assertSame(userAccount, loginService.verifyByUsername(loginDTO));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = " ")
    void shouldRejectBlankUsername(String username) {
        loginDTO.setUsername(username);

        BusinessException exception = assertThrows(
                BusinessException.class, () -> loginService.verifyByUsername(loginDTO)
        );

        assertEquals("登录用户名不能为空", exception.getMessage());
        verifyNoInteractions(userAccountMapper);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = " ")
    void shouldRejectBlankEmail(String email) {
        loginDTO.setEmail(email);

        BusinessException exception = assertThrows(
                BusinessException.class, () -> loginService.verifyByEmail(loginDTO)
        );

        assertEquals("登录邮箱不能为空", exception.getMessage());
        verifyNoInteractions(userAccountMapper);
    }

    @Test
    void shouldRejectUnknownAccount() {
        when(userAccountMapper.selectOne(any())).thenReturn(null);

        BusinessException exception = assertThrows(
                BusinessException.class, () -> loginService.verifyByUsername(loginDTO)
        );

        assertEquals("账号或密码错误", exception.getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = {"wrong", "123456 ", " 123456"})
    void shouldRejectWrongPasswordWithoutTrimming(String password) {
        loginDTO.setPassword(password);

        BusinessException exception = assertThrows(
                BusinessException.class, () -> loginService.verifyByEmail(loginDTO)
        );

        assertEquals("账号或密码错误", exception.getMessage());
    }

    @Test
    void shouldRejectDisabledAccount() {
        userAccount.setEnabled(false);

        BusinessException exception = assertThrows(
                BusinessException.class, () -> loginService.verifyByUsername(loginDTO)
        );

        assertEquals("此账号已被禁用", exception.getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = {"USER", "EDITOR"})
    void shouldRejectNonAdminAccount(String role) {
        userAccount.setRole(role);

        BusinessException exception = assertThrows(
                BusinessException.class, () -> loginService.verifyByEmail(loginDTO)
        );

        assertEquals("此账号无管理端登录权限", exception.getMessage());
    }
}
