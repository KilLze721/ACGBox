package org.killze.acgbox.service.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.killze.acgbox.service.SystemHealthService;
import org.killze.acgbox.vo.common.DependencyHealthVO;
import org.killze.acgbox.vo.common.SystemHealthVO;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 复用现有连接组件执行只读探测，独立返回各依赖结果，不回传敏感连接信息。
 */
@Service
public class SystemHealthServiceImpl implements SystemHealthService {

    private static final Logger log = LoggerFactory.getLogger(SystemHealthServiceImpl.class);

    @Autowired
    private DataSource dataSource;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private ObjectProvider<JavaMailSender> mailSenderProvider;

    @Autowired
    @Qualifier("systemHealthExecutor")
    private ExecutorService systemHealthExecutor;

    @Value("${account-email.enabled:false}")
    private boolean enabled;

    @Value("${account-email.from:}")
    private String from;

    private Clock clock = Clock.systemUTC();
    private SystemHealthVO cachedResult;
    private Instant expiresAt;

    /**
     * 同步刷新避免并发重复探测，三项检查共享五秒等待期限，完成后缓存三十秒。
     */
    @Override
    public synchronized SystemHealthVO getSystemHealth() {
        if (cachedResult != null && clock.instant().isBefore(expiresAt)) {
            return cachedResult;
        }
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5L);
        Future<DependencyHealthVO> database = submit(this::checkDatabase);
        Future<DependencyHealthVO> redis = submit(this::checkRedis);
        Future<DependencyHealthVO> smtp = enabled ? submit(this::checkSmtp)
                : CompletableFuture.completedFuture(result("DISABLED", "邮箱验证码功能未启用"));
        DependencyHealthVO databaseResult = await(database, deadline, "数据库");
        DependencyHealthVO redisResult = await(redis, deadline, "Redis");
        DependencyHealthVO smtpResult = await(smtp, deadline, "SMTP");
        Instant completedAt = clock.instant();
        cachedResult = SystemHealthVO.builder()
                .checkedAt(OffsetDateTime.ofInstant(completedAt, clock.getZone()))
                .database(databaseResult).redis(redisResult).smtp(smtpResult).build();
        expiresAt = completedAt.plusSeconds(30L);
        return cachedResult;
    }

    private DependencyHealthVO checkDatabase() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            return connection.isValid(2) ? result("UP", "连接正常") : result("DOWN", "连接验证失败");
        }
    }

    private DependencyHealthVO checkRedis() {
        String response = stringRedisTemplate.execute((RedisCallback<String>) connection -> connection.ping());
        return "PONG".equals(response) ? result("UP", "连接正常") : result("DOWN", "连接验证失败");
    }

    private DependencyHealthVO checkSmtp() throws Exception {
        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        if (mailSender == null || !StringUtils.hasText(from)) {
            return result("UNCONFIGURED", "邮件服务配置不完整");
        }
        if (!(mailSender instanceof JavaMailSenderImpl sender)) {
            return result("NOT_CHECKED", "当前邮件组件不支持连接检测");
        }
        if (!StringUtils.hasText(sender.getHost()) || !StringUtils.hasText(sender.getUsername())
                || !StringUtils.hasText(sender.getPassword())) {
            return result("UNCONFIGURED", "邮件服务配置不完整");
        }
        sender.testConnection();
        return result("UP", "连接正常，未执行邮件投递");
    }

    private Future<DependencyHealthVO> submit(Callable<DependencyHealthVO> check) {
        try {
            return systemHealthExecutor.submit(check);
        } catch (RejectedExecutionException exception) {
            return CompletableFuture.completedFuture(result("NOT_CHECKED", "检测繁忙，请稍后重试"));
        }
    }

    private DependencyHealthVO await(Future<DependencyHealthVO> check, long deadline, String dependency) {
        try {
            return check.get(Math.max(0L, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
        } catch (TimeoutException exception) {
            check.cancel(true);
            return result("DOWN", "连接检测超时");
        } catch (InterruptedException exception) {
            check.cancel(true);
            Thread.currentThread().interrupt();
            return result("NOT_CHECKED", "检测已中断");
        } catch (ExecutionException exception) {
            log.warn("依赖连接检测失败，类型：{}，异常类型：{}", dependency,
                    exception.getCause().getClass().getSimpleName());
            return result("DOWN", "连接检测失败");
        }
    }

    private DependencyHealthVO result(String status, String message) {
        return DependencyHealthVO.builder().status(status).message(message).build();
    }
}
