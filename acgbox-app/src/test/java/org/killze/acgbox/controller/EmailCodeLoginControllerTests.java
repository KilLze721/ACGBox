package org.killze.acgbox.controller;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.jayway.jsonpath.JsonPath;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.killze.acgbox.config.JwtConfig;
import org.killze.acgbox.config.SecurityConfig;
import org.killze.acgbox.entity.user.UserAccount;
import org.killze.acgbox.exception.GlobalExceptionHandler;
import org.killze.acgbox.mapper.UserAccountMapper;
import org.killze.acgbox.service.impl.EmailVerificationServiceImpl;
import org.killze.acgbox.service.impl.JwtServiceImpl;
import org.killze.acgbox.service.impl.LoginServiceImpl;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 邮箱验证码登录与真实 JWT 安全链测试，Redis、邮件和数据库使用模拟对象。
 */
@SpringJUnitWebConfig(EmailCodeLoginControllerTests.TestConfig.class)
@TestPropertySource(properties = {
        "jwt.secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "jwt.issuer=acgbox-admin",
        "jwt.audience=acgbox-admin",
        "jwt.expiration=7200",
        "account-email.enabled=true",
        "account-email.from=noreply@example.com"
})
class EmailCodeLoginControllerTests {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private JwtDecoder jwtDecoder;

    @Autowired
    private EmailVerificationServiceImpl emailVerificationService;

    @MockitoBean
    private UserAccountMapper mapper;

    @MockitoBean
    private StringRedisTemplate redis;

    @MockitoBean
    private JavaMailSender mailSender;

    private MockMvc mockMvc;
    private UserAccount account;
    private Map<String, String> digests;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), UserAccount.class);
        account = UserAccount.builder().id(2L).username("Editor").email("Editor@Example.com")
                .role("EDITOR").enabled(true).emailVerifiedAt(LocalDateTime.now()).tokenVersion(7L).build();
        when(mapper.selectOne(any())).thenAnswer(invocation -> {
            LambdaQueryWrapper<UserAccount> query = invocation.getArgument(0);
            query.getSqlSegment();
            if (query.getSqlSegment().contains("email =")) {
                return query.getParamNameValuePairs().containsValue(account.getEmail()) ? account : null;
            }
            return account;
        });
        digests = new HashMap<>();
        // 模拟摘要的存取及单次消费，不实际运行 Lua 或访问 Redis。
        when(redis.execute(org.mockito.ArgumentMatchers.<RedisScript<Long>>any(), anyList(), anyString()))
                .thenAnswer(invocation -> {
                    List<String> keys = invocation.getArgument(1);
                    String digest = invocation.getArgument(2);
                    if (keys.size() == 3) {
                        digests.put(keys.get(2), digest);
                        return 1L;
                    }
                    if (!Objects.equals(digests.get(keys.getFirst()), digest)) {
                        return 0L;
                    }
                    digests.remove(keys.getFirst());
                    return 1L;
                });
        ReflectionTestUtils.setField(emailVerificationService, "enabled", true);
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "EDITOR"})
    void shouldSendAndLoginAnonymouslyWithoutPasswordAndConsumeCodeOnce(String role) throws Exception {
        account.setRole(role);
        String code = sendCodeAndReadMail();
        MvcResult result = mockMvc.perform(post("/admin/login/email-code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"Editor@Example.com\",\"verificationCode\":\"" + code
                                + "\",\"id\":1,\"role\":\"USER\",\"tokenVersion\":99}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(2))
                .andExpect(jsonPath("$.data.username").value("Editor"))
                .andExpect(jsonPath("$.data.role").value(role))
                .andExpect(jsonPath("$.data.expiresIn").value(7200))
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.data.verificationCode").doesNotExist()).andReturn();
        String token = JsonPath.read(result.getResponse().getContentAsString(), "$.data.token");
        Jwt jwt = jwtDecoder.decode(token);
        assertEquals("2", jwt.getSubject());
        assertEquals(7L, ((Number) jwt.getClaim("token_version")).longValue());
        assertEquals(role, jwt.getClaimAsString("role"));
        assertEquals(7L, account.getTokenVersion());
        mockMvc.perform(get("/email-login-test").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
        assertLoginRejected(code);
        verify(mapper, never()).insert(any(UserAccount.class));
        verify(mapper, never()).update(isNull(UserAccount.class), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"unknown", "unverified", "disabled", "USER"})
    void shouldNotSendToIneligibleAccountOrExposeItsState(String state) throws Exception {
        switch (state) {
            case "unknown" -> account.setEmail("Other@Example.com");
            case "unverified" -> account.setEmailVerifiedAt(null);
            case "disabled" -> account.setEnabled(false);
            default -> account.setRole("USER");
        }
        mockMvc.perform(post("/admin/login/email-code/send")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"Editor@Example.com\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.token").doesNotExist());
        assertLoginRejected("123456");
        verifyNoInteractions(redis, mailSender);
    }

    @Test
    void shouldNotSendOrLoginUsingDifferentEmailCase() throws Exception {
        mockMvc.perform(post("/admin/login/email-code/send")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"editor@example.com\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
        verifyNoInteractions(redis, mailSender);
    }

    @ParameterizedTest
    @ValueSource(strings = {"version", "email", "disabled", "role", "unverified", "expired"})
    void shouldRejectCodeAfterAccountChangesOrCodeExpires(String state) throws Exception {
        String code = sendCodeAndReadMail();
        switch (state) {
            case "version" -> account.setTokenVersion(8L);
            case "email" -> account.setEmail("Other@Example.com");
            case "disabled" -> account.setEnabled(false);
            case "role" -> account.setRole("USER");
            case "unverified" -> account.setEmailVerifiedAt(null);
            default -> digests.clear();
        }
        assertLoginRejected(code);
    }

    @Test
    void shouldNotUseBindingCodeAsLoginCode() throws Exception {
        emailVerificationService.sendCode(2L, 7L, "BIND", account.getEmail());
        assertLoginRejected(readMailCode());
    }

    @Test
    void shouldKeepValidCodeAfterWrongAttempt() throws Exception {
        String code = sendCodeAndReadMail();
        assertLoginRejected("000000".equals(code) ? "000001" : "000000");
        mockMvc.perform(post("/admin/login/email-code").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"Editor@Example.com\",\"verificationCode\":\"" + code + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void shouldRequireCodeForLoginButNotForSending() throws Exception {
        mockMvc.perform(post("/admin/login/email-code").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"Editor@Example.com\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("登录验证码不能为空"));
        verifyNoInteractions(mapper, redis, mailSender);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"email\":\"invalid\"}",
            "{\"email\":\"Editor@Example.com\",\"verificationCode\":\"12345\"}",
            "{\"email\":\"Editor@Example.com\",\"verificationCode\":\"１２３４５６\"}"})
    void shouldValidateRequestBeforeReadingAccount(String body) throws Exception {
        mockMvc.perform(post("/admin/login/email-code").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(500));
        verifyNoInteractions(mapper, redis, mailSender);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/admin/login/username", "/admin/login/email-password"})
    void shouldKeepPasswordRequiredForExistingLoginMethods(String path) throws Exception {
        mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"Editor@Example.com\",\"username\":\"Editor\",\"verificationCode\":\"123456\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("登录密码不能为空"));
        verifyNoInteractions(mapper, redis, mailSender);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/admin/login/email-code", "/admin/login/email-code/send"})
    void shouldNotPermitAnonymousGetRequests(String path) throws Exception {
        mockMvc.perform(get(path)).andExpect(status().isUnauthorized());
    }

    @Test
    void shouldNotPermitUnlistedLoginPaths() throws Exception {
        mockMvc.perform(post("/admin/login/other")).andExpect(status().isUnauthorized());
    }

    @Test
    void shouldPropagateCooldownWithoutSendingMail() throws Exception {
        doReturn(0L).when(redis).execute(org.mockito.ArgumentMatchers.<RedisScript<Long>>any(), anyList(), anyString());
        mockMvc.perform(post("/admin/login/email-code/send").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"Editor@Example.com\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("验证码发送过于频繁，请 60 秒后重试"));
        verifyNoInteractions(mailSender);
    }

    @Test
    void shouldFailClosedWhenRedisUnavailable() throws Exception {
        doThrow(new DataAccessResourceFailureException("offline")).when(redis)
                .execute(org.mockito.ArgumentMatchers.<RedisScript<Long>>any(), anyList(), anyString());
        mockMvc.perform(post("/admin/login/email-code").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"Editor@Example.com\",\"verificationCode\":\"123456\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("验证码服务暂不可用，请稍后重试"))
                .andExpect(jsonPath("$.data.token").doesNotExist());
    }

    @Test
    void shouldReportMailFailureWithoutReturningLoginToken() throws Exception {
        doThrow(new MailSendException("offline")).when(mailSender).send(any(SimpleMailMessage.class));
        mockMvc.perform(post("/admin/login/email-code/send").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"Editor@Example.com\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("验证码邮件发送失败，请稍后重试"));
        assertTrue(digests.isEmpty());
    }

    @Test
    void shouldRejectWhenEmailFeatureDisabled() throws Exception {
        ReflectionTestUtils.setField(emailVerificationService, "enabled", false);
        mockMvc.perform(post("/admin/login/email-code/send").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"Editor@Example.com\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("邮箱验证码功能尚未启用"));
        verifyNoInteractions(redis, mailSender);
    }

    private String sendCodeAndReadMail() throws Exception {
        mockMvc.perform(post("/admin/login/email-code/send").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"Editor@Example.com\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.token").doesNotExist());
        assertEquals(1, digests.size());
        assertTrue(digests.keySet().iterator().next().contains("{2}:code:7:LOGIN:"));
        return readMailCode();
    }

    private String readMailCode() {
        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        SimpleMailMessage message = captor.getValue();
        assertEquals("Editor@Example.com", message.getTo()[0]);
        assertTrue(message.getText().contains("验证码为："));
        Matcher matcher = Pattern.compile("验证码为：([0-9]{6})").matcher(message.getText());
        assertTrue(matcher.find());
        assertFalse(digests.containsValue(matcher.group(1)));
        return matcher.group(1);
    }

    private void assertLoginRejected(String code) throws Exception {
        mockMvc.perform(post("/admin/login/email-code").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"Editor@Example.com\",\"verificationCode\":\"" + code + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("验证码错误或已过期，请重新获取"))
                .andExpect(jsonPath("$.data.token").doesNotExist());
    }

    @TestConfiguration
    @EnableWebMvc
    @EnableWebSecurity
    @Import({SecurityConfig.class, JwtConfig.class, LoginController.class, LoginServiceImpl.class,
            JwtServiceImpl.class, EmailVerificationServiceImpl.class, GlobalExceptionHandler.class, TestController.class})
    static class TestConfig {
    }

    @TestComponent
    @RestController
    static class TestController {

        @GetMapping("/email-login-test")
        public String getContent() {
            return "ok";
        }
    }
}
