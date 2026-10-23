package org.killze.acgbox.service.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.killze.acgbox.exception.BusinessException;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 邮箱验证码服务测试，验证邮件、摘要、限流及故障处理，不实际发送邮件。
 */
class EmailVerificationServiceImplTests {

    private EmailVerificationServiceImpl service;
    private StringRedisTemplate redis;
    private JavaMailSender mailSender;

    @BeforeEach
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        mailSender = mock(JavaMailSender.class);
        service = new EmailVerificationServiceImpl();
        ReflectionTestUtils.setField(service, "stringRedisTemplate", redis);
        ReflectionTestUtils.setField(service, "mailSenderProvider",
                new StaticListableBeanFactory(Map.of("mailSender", mailSender)).getBeanProvider(JavaMailSender.class));
        ReflectionTestUtils.setField(service, "secretKey", new SecretKeySpec(
                "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        ReflectionTestUtils.setField(service, "enabled", true);
        ReflectionTestUtils.setField(service, "from", "noreply@example.com");
        when(redis.execute(org.mockito.ArgumentMatchers.<RedisScript<Long>>any(), anyList(), anyString()))
                .thenReturn(1L);
        when(redis.execute(org.mockito.ArgumentMatchers.<RedisScript<Long>>any(), anyList(), anyString(), anyString()))
                .thenReturn(1L);
    }

    @Test
    void shouldMailSixDigitCodeAndStoreOnlyDigest() {
        service.sendCode(2L, 7L, "BIND", "New@Example.com");

        ArgumentCaptor<SimpleMailMessage> messageCaptor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(messageCaptor.capture());
        SimpleMailMessage message = messageCaptor.getValue();
        assertEquals("New@Example.com", message.getTo()[0]);
        assertEquals("noreply@example.com", message.getFrom());
        Matcher matcher = Pattern.compile("验证码为：([0-9]{6})").matcher(message.getText());
        assertTrue(matcher.find());
        String code = matcher.group(1);

        ArgumentCaptor<RedisScript<Long>> scriptCaptor = ArgumentCaptor.captor();
        ArgumentCaptor<List<String>> keysCaptor = ArgumentCaptor.captor();
        ArgumentCaptor<String> digestCaptor = ArgumentCaptor.forClass(String.class);
        verify(redis).execute(scriptCaptor.capture(), keysCaptor.capture(), digestCaptor.capture());
        assertEquals(3, keysCaptor.getValue().size());
        assertTrue(keysCaptor.getValue().stream().allMatch(key -> key.contains("{2}")));
        assertTrue(keysCaptor.getValue().get(2).contains(":code:7:BIND:"));
        assertFalse(keysCaptor.getValue().get(2).contains("New@Example.com"));
        assertTrue(digestCaptor.getValue().matches("[0-9a-f]{64}"));
        assertFalse(code.equals(digestCaptor.getValue()));
        assertTrue(scriptCaptor.getValue().getScriptAsString().contains("300"));
        assertTrue(scriptCaptor.getValue().getScriptAsString().contains("count >= 10"));

        service.verifyAndConsume(2L, 7L, "BIND", "New@Example.com", code);
        verify(redis, times(2)).execute(scriptCaptor.capture(), keysCaptor.capture(), digestCaptor.capture());
        assertEquals(digestCaptor.getAllValues().get(0), digestCaptor.getValue());
        assertTrue(scriptCaptor.getValue().getScriptAsString().contains("attempts >= 5"));
        assertTrue(scriptCaptor.getValue().getScriptAsString().contains("redis.call('DEL', KEYS[1])"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"CHANGE", "UNBIND", "LOGIN"})
    void shouldSendCorrectOperationText(String purpose) {
        service.sendCode(2L, 7L, purpose, "Old@Example.com");
        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        String operation = switch (purpose) {
            case "CHANGE" -> "更换账号邮箱";
            case "UNBIND" -> "解除此邮箱";
            default -> "登录管理端";
        };
        assertTrue(captor.getValue().getText().contains(operation));
    }

    @Test
    void shouldRejectDisabledFeatureWithoutTouchingDependencies() {
        ReflectionTestUtils.setField(service, "enabled", false);
        assertError("邮箱验证码功能尚未启用", () -> service.sendCode(2L, 7L, "BIND", "a@example.com"));
        assertError("邮箱验证码功能尚未启用",
                () -> service.verifyAndConsume(2L, 7L, "BIND", "a@example.com", "123456"));
        verifyNoInteractions(redis, mailSender);
    }

    @Test
    void shouldRejectMissingSmtpConfiguration() {
        ReflectionTestUtils.setField(service, "mailSenderProvider",
                new StaticListableBeanFactory().getBeanProvider(JavaMailSender.class));
        assertError("邮件服务尚未配置", () -> service.sendCode(2L, 7L, "BIND", "a@example.com"));
        verifyNoInteractions(redis, mailSender);
    }

    @Test
    void shouldRejectMissingSenderAddress() {
        ReflectionTestUtils.setField(service, "from", "");
        assertError("邮件服务尚未配置", () -> service.sendCode(2L, 7L, "BIND", "a@example.com"));
        verifyNoInteractions(redis, mailSender);
    }

    @Test
    void shouldEnforceResendCooldownBeforeMailing() {
        when(redis.execute(org.mockito.ArgumentMatchers.<RedisScript<Long>>any(), anyList(), anyString())).thenReturn(0L);
        assertError("验证码发送过于频繁，请 60 秒后重试",
                () -> service.sendCode(2L, 7L, "BIND", "a@example.com"));
        verifyNoInteractions(mailSender);
    }

    @Test
    void shouldEnforceHourlyQuotaBeforeMailing() {
        when(redis.execute(org.mockito.ArgumentMatchers.<RedisScript<Long>>any(), anyList(), anyString())).thenReturn(-1L);
        assertError("验证码发送次数已达上限，请稍后重试",
                () -> service.sendCode(2L, 7L, "BIND", "a@example.com"));
        verifyNoInteractions(mailSender);
    }

    @Test
    void shouldRejectMissingRedisResultWithoutMailing() {
        when(redis.execute(org.mockito.ArgumentMatchers.<RedisScript<Long>>any(), anyList(), anyString())).thenReturn(null);
        assertError("验证码服务暂不可用，请稍后重试", () -> service.sendCode(2L, 7L, "BIND", "a@example.com"));
        verifyNoInteractions(mailSender);
    }

    @Test
    void shouldFailClosedWhenRedisUnavailable() {
        when(redis.execute(org.mockito.ArgumentMatchers.<RedisScript<Long>>any(), anyList(), anyString()))
                .thenThrow(new DataAccessResourceFailureException("offline"));
        assertError("验证码服务暂不可用，请稍后重试", () -> service.sendCode(2L, 7L, "BIND", "a@example.com"));
        assertError("验证码服务暂不可用，请稍后重试",
                () -> service.verifyAndConsume(2L, 7L, "BIND", "a@example.com", "123456"));
        verifyNoInteractions(mailSender);
    }

    @Test
    void shouldRemoveOnlyCurrentCodeWhenMailingFails() {
        doThrow(new MailSendException("offline")).when(mailSender).send(any(SimpleMailMessage.class));
        assertError("验证码邮件发送失败，请稍后重试", () -> service.sendCode(2L, 7L, "BIND", "a@example.com"));
        ArgumentCaptor<RedisScript<Long>> scriptCaptor = ArgumentCaptor.captor();
        ArgumentCaptor<List<String>> keysCaptor = ArgumentCaptor.captor();
        ArgumentCaptor<String> digestCaptor = ArgumentCaptor.forClass(String.class);
        verify(redis, times(2)).execute(scriptCaptor.capture(), keysCaptor.capture(), digestCaptor.capture());
        assertEquals(1, keysCaptor.getValue().size());
        assertEquals(digestCaptor.getAllValues().get(0), digestCaptor.getValue());
        assertTrue(scriptCaptor.getValue().getScriptAsString().contains("== ARGV[1]"));
        assertFalse(scriptCaptor.getValue().getScriptAsString().contains("cooldown"));
    }

    @Test
    void shouldStillReturnMailFailureWhenRedisCleanupFails() {
        doThrow(new MailSendException("offline")).when(mailSender).send(any(SimpleMailMessage.class));
        when(redis.execute(org.mockito.ArgumentMatchers.<RedisScript<Long>>any(), anyList(), anyString()))
                .thenReturn(1L).thenThrow(new DataAccessResourceFailureException("offline"));
        assertError("验证码邮件发送失败，请稍后重试", () -> service.sendCode(2L, 7L, "BIND", "a@example.com"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "12345", "abcdef", "１２３４５６", "1234567"})
    void shouldRejectMalformedCodeWithoutRedisAccess(String code) {
        assertError("验证码必须为 6 位数字", () -> service.verifyAndConsume(2L, 7L, "BIND", "a@example.com", code));
        verifyNoInteractions(redis, mailSender);
    }

    @Test
    void shouldRejectWrongExpiredOrConsumedCode() {
        when(redis.execute(org.mockito.ArgumentMatchers.<RedisScript<Long>>any(), anyList(), anyString())).thenReturn(0L);
        assertError("验证码错误或已过期，请重新获取",
                () -> service.verifyAndConsume(2L, 7L, "BIND", "a@example.com", "123456"));
    }

    @Test
    void shouldBindDigestToAccountVersionPurposeAndCaseSensitiveEmail() {
        service.verifyAndConsume(2L, 7L, "BIND", "a@example.com", "123456");
        service.verifyAndConsume(3L, 7L, "BIND", "a@example.com", "123456");
        service.verifyAndConsume(2L, 8L, "BIND", "a@example.com", "123456");
        service.verifyAndConsume(2L, 7L, "UNBIND", "a@example.com", "123456");
        service.verifyAndConsume(2L, 7L, "BIND", "A@example.com", "123456");
        service.verifyAndConsume(2L, 7L, "LOGIN", "a@example.com", "123456");
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(redis, times(6)).execute(org.mockito.ArgumentMatchers.<RedisScript<Long>>any(), anyList(), captor.capture());
        assertEquals(6, new HashSet<>(captor.getAllValues()).size());
        verifyNoInteractions(mailSender);
    }

    private void assertError(String message, Runnable action) {
        assertEquals(message, assertThrows(BusinessException.class, action::run).getMessage());
    }

    @Test
    void shouldCreateRandomProofAndStoreOnlyScopedDigestWithExpiry() {
        String first = service.createEmailChangeToken(2L, 7L, "Old@Example.com");
        String second = service.createEmailChangeToken(2L, 7L, "Old@Example.com");
        assertTrue(first.matches("[A-Za-z0-9_-]{43}"));
        assertTrue(second.matches("[A-Za-z0-9_-]{43}"));
        assertFalse(first.equals(second));

        ArgumentCaptor<RedisScript<Long>> script = ArgumentCaptor.captor();
        ArgumentCaptor<List<String>> keys = ArgumentCaptor.captor();
        ArgumentCaptor<String> digest = ArgumentCaptor.forClass(String.class);
        verify(redis, times(2)).execute(script.capture(), keys.capture(), digest.capture());
        assertEquals(keys.getAllValues().get(0), keys.getValue());
        assertEquals(1, keys.getValue().size());
        assertTrue(keys.getValue().get(0).contains("{2}:code:7:CHANGE_TOKEN:"));
        assertFalse(keys.getValue().get(0).contains("Old@Example.com"));
        assertTrue(digest.getValue().matches("[0-9a-f]{64}"));
        assertFalse(digest.getValue().equals(second));
        assertFalse(digest.getAllValues().get(0).equals(digest.getValue()));
        assertTrue(script.getValue().getScriptAsString().contains("'HSET'"));
        assertTrue(script.getValue().getScriptAsString().contains("'EXPIRE', KEYS[1], 300"));
        verifyNoInteractions(mailSender);
    }

    @Test
    void shouldCheckProofWithoutConsumingUntilFinalChange() {
        String proof = service.createEmailChangeToken(2L, 7L, "Old@Example.com");
        service.verifyEmailChangeToken(2L, 7L, "Old@Example.com", proof, false);
        service.verifyEmailChangeToken(2L, 7L, "Old@Example.com", proof, true);
        ArgumentCaptor<String> storedDigest = ArgumentCaptor.forClass(String.class);
        verify(redis).execute(org.mockito.ArgumentMatchers.<RedisScript<Long>>any(), anyList(), storedDigest.capture());
        ArgumentCaptor<RedisScript<Long>> script = ArgumentCaptor.captor();
        ArgumentCaptor<String> digest = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> consume = ArgumentCaptor.forClass(String.class);
        verify(redis, times(2)).execute(script.capture(), anyList(), digest.capture(), consume.capture());
        assertEquals(List.of("0", "1"), consume.getAllValues());
        assertTrue(digest.getAllValues().stream().allMatch(storedDigest.getValue()::equals));
        assertTrue(script.getValue().getScriptAsString().contains("digest ~= ARGV[1]"));
        assertTrue(script.getValue().getScriptAsString().contains("if ARGV[2] == '1' then"));
        assertTrue(script.getValue().getScriptAsString().contains("redis.call('DEL', KEYS[1])"));
    }

    @Test
    void shouldScopeProofToAccountVersionAndCaseSensitiveOldEmail() {
        String proof = "a".repeat(43);
        service.verifyEmailChangeToken(2L, 7L, "Old@Example.com", proof, false);
        service.verifyEmailChangeToken(3L, 7L, "Old@Example.com", proof, false);
        service.verifyEmailChangeToken(2L, 8L, "Old@Example.com", proof, false);
        service.verifyEmailChangeToken(2L, 7L, "old@example.com", proof, false);
        ArgumentCaptor<List<String>> keys = ArgumentCaptor.captor();
        ArgumentCaptor<String> digests = ArgumentCaptor.forClass(String.class);
        verify(redis, times(4)).execute(org.mockito.ArgumentMatchers.<RedisScript<Long>>any(),
                keys.capture(), digests.capture(), anyString());
        assertEquals(4, new HashSet<>(keys.getAllValues()).size());
        assertEquals(4, new HashSet<>(digests.getAllValues()).size());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "123456", "a b", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"})
    void shouldRejectMalformedProofBeforeRedis(String proof) {
        assertError("请先验证原邮箱后再换绑",
                () -> service.verifyEmailChangeToken(2L, 7L, "Old@Example.com", proof, false));
        verifyNoInteractions(redis, mailSender);
    }

    @Test
    void shouldRejectMissingProofBeforeRedis() {
        assertError("请先验证原邮箱后再换绑",
                () -> service.verifyEmailChangeToken(2L, 7L, "Old@Example.com", null, false));
        verifyNoInteractions(redis, mailSender);
    }

    @Test
    void shouldRejectExpiredOrAlreadyConsumedProof() {
        when(redis.execute(org.mockito.ArgumentMatchers.<RedisScript<Long>>any(), anyList(), anyString(), anyString()))
                .thenReturn(1L, 0L, 0L);
        String proof = "a".repeat(43);
        service.verifyEmailChangeToken(2L, 7L, "Old@Example.com", proof, true);
        assertError("邮箱换绑凭证无效或已过期，请重新验证原邮箱",
                () -> service.verifyEmailChangeToken(2L, 7L, "Old@Example.com", proof, true));
        assertError("邮箱换绑凭证无效或已过期，请重新验证原邮箱",
                () -> service.verifyEmailChangeToken(2L, 7L, "Old@Example.com", proof, false));
    }

    @Test
    void shouldFailClosedWhenProofRedisIsUnavailable() {
        when(redis.execute(org.mockito.ArgumentMatchers.<RedisScript<Long>>any(), anyList(), anyString()))
                .thenThrow(new DataAccessResourceFailureException("offline"));
        when(redis.execute(org.mockito.ArgumentMatchers.<RedisScript<Long>>any(), anyList(), anyString(), anyString()))
                .thenThrow(new DataAccessResourceFailureException("offline"));
        assertError("验证码服务暂不可用，请稍后重试", () -> service.createEmailChangeToken(2L, 7L, "Old@Example.com"));
        assertError("验证码服务暂不可用，请稍后重试",
                () -> service.verifyEmailChangeToken(2L, 7L, "Old@Example.com", "a".repeat(43), true));
    }

    @Test
    void shouldRejectMissingRedisResultForProof() {
        when(redis.execute(org.mockito.ArgumentMatchers.<RedisScript<Long>>any(), anyList(), anyString())).thenReturn(null);
        when(redis.execute(org.mockito.ArgumentMatchers.<RedisScript<Long>>any(), anyList(), anyString(), anyString())).thenReturn(null);
        assertError("验证码服务暂不可用，请稍后重试", () -> service.createEmailChangeToken(2L, 7L, "Old@Example.com"));
        assertError("邮箱换绑凭证无效或已过期，请重新验证原邮箱",
                () -> service.verifyEmailChangeToken(2L, 7L, "Old@Example.com", "a".repeat(43), false));
    }

    @Test
    void shouldRejectProofOperationsWhenEmailFeatureIsDisabled() {
        ReflectionTestUtils.setField(service, "enabled", false);
        assertError("邮箱验证码功能尚未启用", () -> service.createEmailChangeToken(2L, 7L, "Old@Example.com"));
        assertError("邮箱验证码功能尚未启用",
                () -> service.verifyEmailChangeToken(2L, 7L, "Old@Example.com", "a".repeat(43), false));
        verifyNoInteractions(redis, mailSender);
    }
}
