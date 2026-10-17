package org.killze.acgbox.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.killze.acgbox.dto.user.UserAccountPageDTO;
import org.killze.acgbox.entity.user.UserAccount;
import org.killze.acgbox.exception.BusinessException;
import org.killze.acgbox.mapper.UserAccountMapper;
import org.killze.acgbox.vo.common.PageVO;
import org.killze.acgbox.vo.user.UserAccountVO;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.notNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 用户账号查询服务测试，不连接真实数据库。
 */
class UserAccountServiceImplTests {

    private UserAccountServiceImpl userAccountService;
    private UserAccountMapper userAccountMapper;
    private UserAccount userAccount;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), UserAccount.class
        );
        userAccountMapper = mock(UserAccountMapper.class);
        userAccountService = new UserAccountServiceImpl();
        ReflectionTestUtils.setField(userAccountService, "userAccountMapper", userAccountMapper);

        userAccount = UserAccount.builder()
                .id(2L)
                .username("EditorCase")
                .email("Editor@Example.com")
                .passwordHash("must-not-be-returned")
                .role("EDITOR")
                .enabled(false)
                .tokenVersion(0L)
                .emailVerifiedAt(LocalDateTime.of(2026, 10, 1, 12, 0))
                .createdAt(LocalDateTime.of(2026, 9, 1, 12, 0))
                .updatedAt(LocalDateTime.of(2026, 10, 2, 12, 0))
                .build();
        when(userAccountMapper.selectPage(notNull(), any())).thenAnswer(invocation -> {
            Page<UserAccount> page = invocation.getArgument(0);
            page.setTotal(1L);
            page.setRecords(List.of(userAccount));
            return page;
        });
        when(userAccountMapper.selectOne(any())).thenReturn(userAccount);
    }

    @Test
    void shouldUseDefaultPaginationAndPreserveAccountFields() {
        PageVO<UserAccountVO> result = userAccountService.pageUserAccount(new UserAccountPageDTO());

        assertEquals(1L, result.getPageNum());
        assertEquals(20L, result.getPageSize());
        assertEquals(1L, result.getTotal());
        assertEquals(1L, result.getPages());
        assertEquals(1, result.getRows().size());
        assertAccountFields(result.getRows().getFirst());
    }

    @Test
    void shouldDefaultNullPaginationParameters() {
        UserAccountPageDTO pageDTO = new UserAccountPageDTO();
        pageDTO.setPageNum(null);
        pageDTO.setPageSize(null);

        PageVO<UserAccountVO> result = userAccountService.pageUserAccount(pageDTO);

        assertEquals(1L, result.getPageNum());
        assertEquals(20L, result.getPageSize());
    }

    @Test
    void shouldReturnPaginationMetadataFromMapper() {
        UserAccountPageDTO pageDTO = new UserAccountPageDTO();
        pageDTO.setPageNum(2L);
        pageDTO.setPageSize(2L);
        when(userAccountMapper.selectPage(notNull(), any())).thenAnswer(invocation -> {
            Page<UserAccount> page = invocation.getArgument(0);
            assertEquals(2L, page.getCurrent());
            assertEquals(2L, page.getSize());
            page.setTotal(5L);
            page.setRecords(List.of(userAccount));
            return page;
        });

        PageVO<UserAccountVO> result = userAccountService.pageUserAccount(pageDTO);

        assertEquals(2L, result.getPageNum());
        assertEquals(2L, result.getPageSize());
        assertEquals(5L, result.getTotal());
        assertEquals(3L, result.getPages());
    }

    @Test
    void shouldCombineFiltersAndSelectOnlyPublicAccountFields() {
        UserAccountPageDTO pageDTO = new UserAccountPageDTO();
        pageDTO.setUsername("eDiToR");
        pageDTO.setEmail("EXAMPLE.COM");
        pageDTO.setRole("EDITOR");
        pageDTO.setEnabled(false);
        when(userAccountMapper.selectPage(notNull(), any())).thenAnswer(invocation -> {
            LambdaQueryWrapper<UserAccount> query = invocation.getArgument(1);
            assertPublicAccountColumns(query);
            String sql = query.getSqlSegment();
            assertTrue(sql.contains("username ILIKE '%' ||"));
            assertTrue(sql.contains("email ILIKE '%' ||"));
            assertTrue(sql.contains("role ="));
            assertTrue(sql.contains("enabled ="));
            assertTrue(sql.endsWith("ORDER BY updated_at DESC,id DESC"));
            assertTrue(query.getParamNameValuePairs().values()
                    .containsAll(List.of("eDiToR", "EXAMPLE.COM", "EDITOR", false)));
            Page<UserAccount> page = invocation.getArgument(0);
            page.setRecords(List.of(userAccount));
            return page;
        });

        userAccountService.pageUserAccount(pageDTO);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void shouldFilterBothEnabledStates(boolean enabled) {
        UserAccountPageDTO pageDTO = new UserAccountPageDTO();
        pageDTO.setEnabled(enabled);
        when(userAccountMapper.selectPage(notNull(), any())).thenAnswer(invocation -> {
            LambdaQueryWrapper<UserAccount> query = invocation.getArgument(1);
            assertTrue(query.getSqlSegment().contains("enabled ="));
            assertEquals(enabled, query.getParamNameValuePairs().get("MPGENVAL1"));
            return invocation.getArgument(0);
        });

        userAccountService.pageUserAccount(pageDTO);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = " ")
    void shouldIgnoreBlankFiltersAndOmittedEnabledState(String keyword) {
        UserAccountPageDTO pageDTO = new UserAccountPageDTO();
        pageDTO.setUsername(keyword);
        pageDTO.setEmail(keyword);
        pageDTO.setRole(keyword);
        when(userAccountMapper.selectPage(notNull(), any())).thenAnswer(invocation -> {
            LambdaQueryWrapper<UserAccount> query = invocation.getArgument(1);
            assertPublicAccountColumns(query);
            assertEquals("ORDER BY updated_at DESC,id DESC", query.getSqlSegment().trim());
            assertTrue(query.getParamNameValuePairs().isEmpty());
            return invocation.getArgument(0);
        });

        userAccountService.pageUserAccount(pageDTO);
    }

    @Test
    void shouldReturnEmptyPageWhenNoAccountsMatch() {
        when(userAccountMapper.selectPage(notNull(), any())).thenAnswer(invocation -> invocation.getArgument(0));

        PageVO<UserAccountVO> result = userAccountService.pageUserAccount(new UserAccountPageDTO());

        assertEquals(0L, result.getTotal());
        assertEquals(0L, result.getPages());
        assertTrue(result.getRows().isEmpty());
    }

    @Test
    void shouldGetDisabledAccountByIdWithoutReadingPasswordHash() {
        when(userAccountMapper.selectOne(any())).thenAnswer(invocation -> {
            LambdaQueryWrapper<UserAccount> query = invocation.getArgument(0);
            assertPublicAccountColumns(query);
            assertTrue(query.getSqlSegment().contains("id ="));
            assertEquals(2L, query.getParamNameValuePairs().get("MPGENVAL1"));
            return userAccount;
        });

        assertAccountFields(userAccountService.getUserAccountById(2L));
    }

    @Test
    void shouldAllowAccountWithoutEmail() {
        userAccount.setEmail(null);
        userAccount.setEmailVerifiedAt(null);

        UserAccountVO result = userAccountService.getUserAccountById(2L);

        assertNull(result.getEmail());
        assertNull(result.getEmailVerifiedAt());
    }

    @Test
    void shouldRejectUnknownAccount() {
        when(userAccountMapper.selectOne(any())).thenReturn(null);

        BusinessException exception = assertThrows(
                BusinessException.class, () -> userAccountService.getUserAccountById(999L)
        );

        assertEquals("此用户账号不存在", exception.getMessage());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(longs = {0L, -1L})
    void shouldRejectInvalidAccountId(Long id) {
        BusinessException exception = assertThrows(
                BusinessException.class, () -> userAccountService.getUserAccountById(id)
        );

        assertEquals("用户账号 ID 必须大于等于 1", exception.getMessage());
        verifyNoInteractions(userAccountMapper);
    }

    @ParameterizedTest
    @ValueSource(longs = {0L, -1L})
    void shouldRejectInvalidPageNum(Long pageNum) {
        UserAccountPageDTO pageDTO = new UserAccountPageDTO();
        pageDTO.setPageNum(pageNum);

        BusinessException exception = assertThrows(
                BusinessException.class, () -> userAccountService.pageUserAccount(pageDTO)
        );

        assertEquals("pageNum 必须大于等于 1", exception.getMessage());
        verifyNoInteractions(userAccountMapper);
    }

    @ParameterizedTest
    @ValueSource(longs = {0L, -1L, 101L})
    void shouldRejectInvalidPageSize(Long pageSize) {
        UserAccountPageDTO pageDTO = new UserAccountPageDTO();
        pageDTO.setPageSize(pageSize);

        BusinessException exception = assertThrows(
                BusinessException.class, () -> userAccountService.pageUserAccount(pageDTO)
        );

        assertEquals("pageSize 必须处于 1 到 100 之间", exception.getMessage());
        verifyNoInteractions(userAccountMapper);
    }

    private void assertPublicAccountColumns(LambdaQueryWrapper<UserAccount> query) {
        assertEquals("id,username,email,role,enabled,email_verified_at,created_at,updated_at", query.getSqlSelect());
        assertFalse(query.getSqlSelect().contains("password_hash"));
    }

    private void assertAccountFields(UserAccountVO result) {
        assertEquals(userAccount.getId(), result.getId());
        assertEquals("EditorCase", result.getUsername());
        assertEquals("Editor@Example.com", result.getEmail());
        assertEquals("EDITOR", result.getRole());
        assertEquals(false, result.getEnabled());
        assertEquals(userAccount.getEmailVerifiedAt(), result.getEmailVerifiedAt());
        assertEquals(userAccount.getCreatedAt(), result.getCreatedAt());
        assertEquals(userAccount.getUpdatedAt(), result.getUpdatedAt());
    }
}
