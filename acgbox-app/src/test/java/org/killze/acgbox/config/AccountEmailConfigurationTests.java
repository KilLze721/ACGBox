package org.killze.acgbox.config;

import org.junit.jupiter.api.Test;
import org.killze.acgbox.exception.BusinessException;
import org.killze.acgbox.service.EmailVerificationService;
import org.killze.acgbox.service.impl.EmailVerificationServiceImpl;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.boot.mail.autoconfigure.MailSenderAutoConfiguration;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 邮箱验证码关闭时的自动配置测试，不连接 SMTP、Redis 或数据库。
 */
class AccountEmailConfigurationTests {

    @Test
    void shouldStartWithEmailDisabledAndNoSmtpSettings() {
        new ApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withConfiguration(AutoConfigurations.of(MailSenderAutoConfiguration.class, DataRedisAutoConfiguration.class))
                .withUserConfiguration(EmailVerificationServiceImpl.class)
                .withBean(SecretKey.class, () -> new SecretKeySpec(
                        "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8), "HmacSHA256"))
                .withPropertyValues("account-email.enabled=false", "spring.mail.host=", "account-email.from=")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(StringRedisTemplate.class);
                    EmailVerificationService service = context.getBean(EmailVerificationService.class);
                    BusinessException exception = assertThrows(BusinessException.class,
                            () -> service.sendCode(2L, 7L, "BIND", "a@example.com"));
                    assertEquals("邮箱验证码功能尚未启用", exception.getMessage());
                });
    }
}
