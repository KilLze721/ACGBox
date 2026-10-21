package org.killze.acgbox.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.context.config.ConfigDataResourceNotFoundException;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 验证 JAR 外私有配置可以解析原文件占位符，不读取本机真实私有配置。
 */
class ExternalSecretsConfigurationTests {

    @TempDir
    private Path directory;

    @Test
    void shouldResolveUppercaseKeysAndPreserveApplicationDefaults() throws IOException {
        Path file = directory.resolve("application-secrets.yml");
        String secret = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";
        Files.writeString(file, """
                ACGBOX_JWT_SECRET: "%s"
                ACGBOX_ACCOUNT_EMAIL_ENABLED: true
                ACGBOX_MAIL_HOST: "smtp.example.test"
                ACGBOX_REDIS_PORT: 6380
                spring:
                  datasource:
                    password: "test-only-password"
                """.formatted(secret), StandardCharsets.UTF_8);

        try (GenericApplicationContext context = createContext(file)) {
            new ConfigDataApplicationContextInitializer().initialize(context);
            assertEquals(secret, context.getEnvironment().getProperty("jwt.secret"));
            assertEquals("smtp.example.test", context.getEnvironment().getProperty("spring.mail.host"));
            assertEquals("true", context.getEnvironment().getProperty("account-email.enabled"));
            assertEquals("6380", context.getEnvironment().getProperty("spring.data.redis.port"));
            assertEquals("test-only-password", context.getEnvironment().getProperty("spring.datasource.password"));
            assertEquals("8080", context.getEnvironment().getProperty("server.port"));
            assertEquals("acgbox-admin", context.getEnvironment().getProperty("jwt.issuer"));
        }
    }

    @Test
    void shouldFailWhenRequiredPrivateFileDoesNotExist() {
        try (GenericApplicationContext context = createContext(directory.resolve("missing.yml"))) {
            assertThrows(ConfigDataResourceNotFoundException.class,
                    () -> new ConfigDataApplicationContextInitializer().initialize(context));
        }
    }

    private GenericApplicationContext createContext(Path file) {
        GenericApplicationContext context = new GenericApplicationContext();
        // 隔离开发机器的环境变量和 JVM 参数，防止真实私有配置影响测试。
        context.getEnvironment().getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        context.getEnvironment().getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("external-config-test", Map.of(
                "spring.config.additional-location", file.toUri().toString()
        )));
        return context;
    }
}
