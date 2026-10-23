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
import org.killze.acgbox.entity.user.UserAccount;
import org.killze.acgbox.mapper.UserAccountMapper;
import org.killze.acgbox.service.JwtService;
import org.killze.acgbox.service.SystemHealthService;
import org.killze.acgbox.service.impl.JwtServiceImpl;
import org.killze.acgbox.vo.common.DependencyHealthVO;
import org.killze.acgbox.vo.common.SystemHealthVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 连接检测接口的 JWT 和管理员权限测试，认证数据库与检测服务均使用模拟对象。
 */
@SpringJUnitWebConfig(SystemHealthControllerTests.TestConfig.class)
@TestPropertySource(properties = {
        "jwt.secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "jwt.issuer=acgbox-admin", "jwt.audience=acgbox-admin", "jwt.expiration=7200"
})
class SystemHealthControllerTests {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private UserAccountMapper mapper;

    @MockitoBean
    private SystemHealthService systemHealthService;

    private UserAccount account;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), UserAccount.class);
        account = UserAccount.builder().id(1L).role("ADMIN").enabled(true).tokenVersion(7L).build();
        when(mapper.selectOne(any())).thenReturn(account);
        when(systemHealthService.getSystemHealth()).thenReturn(SystemHealthVO.builder()
                .checkedAt(OffsetDateTime.parse("2026-10-23T04:00:00Z"))
                .database(DependencyHealthVO.builder().status("UP").message("连接正常").build())
                .redis(DependencyHealthVO.builder().status("DOWN").message("连接检测失败").build())
                .smtp(DependencyHealthVO.builder().status("DISABLED").message("邮箱验证码功能未启用").build()).build());
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void shouldReturnSeparateResultsEvenWhenDependencyIsDown() throws Exception {
        mockMvc.perform(get("/admin/system/health")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtService.createToken(account)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.database.status").value("UP"))
                .andExpect(jsonPath("$.data.redis.status").value("DOWN"))
                .andExpect(jsonPath("$.data.smtp.status").value("DISABLED"))
                .andExpect(jsonPath("$.data.checkedAt").isNotEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"EDITOR", "USER"})
    void shouldRejectNonAdminRoles(String role) throws Exception {
        account.setRole(role);
        mockMvc.perform(get("/admin/system/health")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtService.createToken(account)))
                .andExpect(status().isForbidden());
        verifyNoInteractions(systemHealthService);
    }

    @Test
    void shouldRejectAnonymousRequestsBeforeCheckingConnections() throws Exception {
        mockMvc.perform(get("/admin/system/health")).andExpect(status().isUnauthorized());
        verifyNoInteractions(mapper, systemHealthService);
    }

    @Test
    void shouldNotBypassVersionValidationForMonitoring() throws Exception {
        String token = jwtService.createToken(account);
        account.setTokenVersion(8L);
        mockMvc.perform(get("/admin/system/health").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(systemHealthService);
    }

    @Test
    void shouldNotBypassAuthenticationWhenItsDatabaseIsUnavailable() {
        String token = jwtService.createToken(account);
        when(mapper.selectOne(any())).thenThrow(new DataAccessResourceFailureException("offline"));
        assertThrows(Exception.class, () -> mockMvc.perform(get("/admin/system/health")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)));
        verifyNoInteractions(systemHealthService);
    }

    @TestConfiguration
    @EnableWebMvc
    @EnableWebSecurity
    @Import({SecurityConfig.class, JwtConfig.class, SystemHealthController.class, JwtServiceImpl.class})
    static class TestConfig {
    }
}
