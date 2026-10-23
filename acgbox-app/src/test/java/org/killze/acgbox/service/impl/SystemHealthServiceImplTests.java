package org.killze.acgbox.service.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.test.util.ReflectionTestUtils;
import org.killze.acgbox.vo.common.DependencyHealthVO;
import org.killze.acgbox.vo.common.SystemHealthVO;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.List;
import java.util.Collections;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 连接检测服务测试，不连接真实数据库、Redis 或 SMTP。
 */
class SystemHealthServiceImplTests {

    private SystemHealthServiceImpl service;
    private DataSource dataSource;
    private Connection connection;
    private StringRedisTemplate redis;
    private JavaMailSenderImpl mailSender;
    private ExecutorService executor;
    private Clock clock;
    private final Instant now = Instant.parse("2026-10-23T04:00:00Z");

    @BeforeEach
    void setUp() throws Exception {
        dataSource = mock(DataSource.class);
        connection = mock(Connection.class);
        redis = mock(StringRedisTemplate.class);
        mailSender = mock(JavaMailSenderImpl.class);
        executor = mock(ExecutorService.class);
        clock = mock(Clock.class);
        when(clock.instant()).thenReturn(now);
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.isValid(2)).thenReturn(true);
        when(redis.execute(org.mockito.ArgumentMatchers.<RedisCallback<String>>any())).thenReturn("PONG");
        when(mailSender.getHost()).thenReturn("smtp.example.com");
        when(mailSender.getUsername()).thenReturn("sender@example.com");
        when(mailSender.getPassword()).thenReturn("test-only-credential");
        when(executor.submit(org.mockito.ArgumentMatchers.<Callable<DependencyHealthVO>>any()))
                .thenAnswer(invocation -> {
                    FutureTask<DependencyHealthVO> task = new FutureTask<>(invocation.getArgument(0));
                    task.run();
                    return task;
                });
        service = new SystemHealthServiceImpl();
        ReflectionTestUtils.setField(service, "dataSource", dataSource);
        ReflectionTestUtils.setField(service, "stringRedisTemplate", redis);
        ReflectionTestUtils.setField(service, "mailSenderProvider",
                new StaticListableBeanFactory(Map.of("mailSender", mailSender)).getBeanProvider(JavaMailSender.class));
        ReflectionTestUtils.setField(service, "systemHealthExecutor", executor);
        ReflectionTestUtils.setField(service, "clock", clock);
        ReflectionTestUtils.setField(service, "enabled", true);
        ReflectionTestUtils.setField(service, "from", "sender@example.com");
    }

    @Test
    void shouldProbeEachConnectionWithoutWritingDataOrSendingMail() throws Exception {
        var result = service.getSystemHealth();
        assertEquals("UP", result.getDatabase().getStatus());
        assertEquals("UP", result.getRedis().getStatus());
        assertEquals("UP", result.getSmtp().getStatus());
        assertEquals(now, result.getCheckedAt().toInstant());
        verify(connection).isValid(2);
        verify(connection).close();
        verify(connection, never()).createStatement();
        verify(mailSender).testConnection();
        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void shouldCacheForThirtySecondsWithoutChangingActualCheckTime() throws Exception {
        var first = service.getSystemHealth();
        when(clock.instant()).thenReturn(now.plusSeconds(29));
        assertSame(first, service.getSystemHealth());
        verify(dataSource).getConnection();
        when(clock.instant()).thenReturn(now.plusSeconds(30));
        var refreshed = service.getSystemHealth();
        assertNotSame(first, refreshed);
        assertEquals(now.plusSeconds(30), refreshed.getCheckedAt().toInstant());
        verify(dataSource, times(2)).getConnection();
        verify(mailSender, times(2)).testConnection();
    }

    @Test
    void shouldShareOneProbeAcrossConcurrentRequests() throws Exception {
        try (var requests = Executors.newFixedThreadPool(4)) {
            List<Callable<SystemHealthVO>> calls = Collections.nCopies(8, service::getSystemHealth);
            var results = requests.invokeAll(calls);
            var first = results.getFirst().get();
            for (var result : results) {
                assertSame(first, result.get());
            }
        }
        verify(dataSource).getConnection();
        verify(mailSender).testConnection();
    }

    @Test
    void shouldKeepOtherResultsWhenDatabaseFailsAndHideExceptionDetails() throws Exception {
        when(dataSource.getConnection()).thenThrow(new SQLException("jdbc:private-host password=private"));
        var result = service.getSystemHealth();
        assertEquals("DOWN", result.getDatabase().getStatus());
        assertEquals("UP", result.getRedis().getStatus());
        assertEquals("UP", result.getSmtp().getStatus());
        assertFalse(result.toString().contains("private"));
        assertFalse(result.toString().contains("smtp.example.com"));
    }

    @Test
    void shouldCloseDatabaseConnectionWhenValidationFails() throws Exception {
        when(connection.isValid(2)).thenThrow(new SQLException("failed"));
        assertEquals("DOWN", service.getSystemHealth().getDatabase().getStatus());
        verify(connection).close();
    }

    @Test
    void shouldReportInvalidDatabaseConnection() throws Exception {
        when(connection.isValid(2)).thenReturn(false);
        assertEquals("DOWN", service.getSystemHealth().getDatabase().getStatus());
    }

    @Test
    void shouldReportUnexpectedRedisResponse() {
        when(redis.execute(org.mockito.ArgumentMatchers.<RedisCallback<String>>any())).thenReturn(null);
        var result = service.getSystemHealth();
        assertEquals("DOWN", result.getRedis().getStatus());
        assertEquals("UP", result.getDatabase().getStatus());
    }

    @Test
    void shouldNotContactSmtpWhenFeatureDisabled() {
        ReflectionTestUtils.setField(service, "enabled", false);
        assertEquals("DISABLED", service.getSystemHealth().getSmtp().getStatus());
        verifyNoInteractions(mailSender);
    }

    @Test
    void shouldReportMissingMailSender() {
        ReflectionTestUtils.setField(service, "mailSenderProvider",
                new StaticListableBeanFactory().getBeanProvider(JavaMailSender.class));
        assertEquals("UNCONFIGURED", service.getSystemHealth().getSmtp().getStatus());
    }

    @Test
    void shouldNotConnectWithMissingSenderAddress() throws Exception {
        ReflectionTestUtils.setField(service, "from", "");
        assertEquals("UNCONFIGURED", service.getSystemHealth().getSmtp().getStatus());
        verify(mailSender, never()).testConnection();
    }

    @Test
    void shouldNotConnectWithIncompleteSmtpCredentials() throws Exception {
        when(mailSender.getPassword()).thenReturn("");
        assertEquals("UNCONFIGURED", service.getSystemHealth().getSmtp().getStatus());
        verify(mailSender, never()).testConnection();
    }

    @Test
    void shouldReportUnsupportedCustomMailSenderWithoutCastingOrSending() {
        JavaMailSender customSender = mock(JavaMailSender.class);
        ReflectionTestUtils.setField(service, "mailSenderProvider",
                new StaticListableBeanFactory(Map.of("mailSender", customSender)).getBeanProvider(JavaMailSender.class));
        assertEquals("NOT_CHECKED", service.getSystemHealth().getSmtp().getStatus());
        verifyNoInteractions(customSender);
    }

    @Test
    void shouldKeepDatabaseAndRedisResultsWhenSmtpFails() throws Exception {
        doThrow(new jakarta.mail.MessagingException("private credentials")).when(mailSender).testConnection();
        var result = service.getSystemHealth();
        assertEquals("DOWN", result.getSmtp().getStatus());
        assertEquals("UP", result.getDatabase().getStatus());
        assertEquals("UP", result.getRedis().getStatus());
        assertFalse(result.toString().contains("private"));
    }

    @Test
    void shouldReturnNotCheckedWhenWorkersAreStillBusy() {
        doThrow(new RejectedExecutionException()).when(executor)
                .submit(org.mockito.ArgumentMatchers.<Callable<DependencyHealthVO>>any());
        var result = service.getSystemHealth();
        assertEquals("NOT_CHECKED", result.getDatabase().getStatus());
        assertEquals("NOT_CHECKED", result.getRedis().getStatus());
        assertEquals("NOT_CHECKED", result.getSmtp().getStatus());
        verifyNoInteractions(dataSource, redis, mailSender);
    }

    @Test
    void shouldKeepDisabledSmtpStatusEvenWhenOtherChecksCannotStart() {
        ReflectionTestUtils.setField(service, "enabled", false);
        doThrow(new RejectedExecutionException()).when(executor)
                .submit(org.mockito.ArgumentMatchers.<Callable<DependencyHealthVO>>any());
        var result = service.getSystemHealth();
        assertEquals("NOT_CHECKED", result.getDatabase().getStatus());
        assertEquals("DISABLED", result.getSmtp().getStatus());
        verifyNoInteractions(mailSender);
    }

    @Test
    void shouldCancelTimedOutChecksWithoutWaitingForUnderlyingNetwork() {
        FutureTask<DependencyHealthVO> blocked = new FutureTask<>(() -> null) {
            @Override
            public DependencyHealthVO get(long timeout, TimeUnit unit) throws TimeoutException {
                throw new TimeoutException();
            }
        };
        doReturn(blocked).when(executor).submit(org.mockito.ArgumentMatchers.<Callable<DependencyHealthVO>>any());
        var result = service.getSystemHealth();
        assertEquals("DOWN", result.getDatabase().getStatus());
        assertEquals("连接检测超时", result.getDatabase().getMessage());
        assertEquals("DOWN", result.getRedis().getStatus());
        assertEquals("DOWN", result.getSmtp().getStatus());
        assertTrue(blocked.isCancelled());
        verifyNoInteractions(dataSource, redis, mailSender);
    }
}
