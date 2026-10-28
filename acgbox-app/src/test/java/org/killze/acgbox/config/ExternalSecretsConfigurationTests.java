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
                ACGBOX_COS_ENABLED: true
                ACGBOX_COS_REGION: "ap-guangzhou"
                ACGBOX_COS_BUCKET: "covers-1250000000"
                ACGBOX_COS_SECRET_ID: "test-only-id"
                ACGBOX_COS_SECRET_KEY: "test-only-key"
                ACGBOX_COS_PUBLIC_BASE_URL: "https://img.example.test"
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
            assertEquals("true", context.getEnvironment().getProperty("storage.cos.enabled"));
            assertEquals("ap-guangzhou", context.getEnvironment().getProperty("storage.cos.region"));
            assertEquals("covers-1250000000", context.getEnvironment().getProperty("storage.cos.bucket"));
            assertEquals("test-only-id", context.getEnvironment().getProperty("storage.cos.secret-id"));
            assertEquals("test-only-key", context.getEnvironment().getProperty("storage.cos.secret-key"));
            assertEquals("https://img.example.test", context.getEnvironment().getProperty("storage.cos.public-base-url"));
            assertEquals("5242880B", context.getEnvironment().getProperty("spring.servlet.multipart.max-file-size"));
            assertEquals("6291456B", context.getEnvironment().getProperty("spring.servlet.multipart.max-request-size"));
            assertEquals("true", context.getEnvironment().getProperty("spring.servlet.multipart.resolve-lazily"));
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

    @Test
    void disabledCosDoesNotRequireCredentialsOrCreateClient() {
        try (var context = new org.springframework.context.annotation.AnnotationConfigApplicationContext()) {
            context.register(CosStorageConfig.class);
            context.refresh();
            assertEquals(0, context.getBeansOfType(com.qcloud.cos.COSClient.class).size());
            context.getBean(CosStorageProperties.class).validate();
        }
    }

    @Test
    void enabledCosRequiresAllSettingsBeforeClientCreation() {
        var properties = new CosStorageProperties();
        properties.setEnabled(true);
        var config = new CosStorageConfig();
        assertThrows(IllegalStateException.class, () -> config.cosClient(properties));
        properties.setRegion("ap-guangzhou");
        assertThrows(IllegalStateException.class, () -> config.cosClient(properties));
        properties.setBucket("covers-1250000000");
        assertThrows(IllegalStateException.class, () -> config.cosClient(properties));
        properties.setSecretId("test-only-id");
        assertThrows(IllegalStateException.class, () -> config.cosClient(properties));
        properties.setSecretKey("test-only-key");
        assertThrows(IllegalStateException.class, () -> config.cosClient(properties));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"http://img.example.test", "https://img.example.test/path",
            "https://img.example.test?key=value", "https://img.example.test#fragment", "https://user@img.example.test",
            "https://localhost", "https://127.0.0.1", "https://img.example.test:443", "invalid", ""})
    void cosRejectsInvalidPublicBasesWithoutEchoingThem(String url) {
        var properties = cosProperties();
        properties.setPublicBaseUrl(url);
        var error = assertThrows(IllegalStateException.class, properties::validate);
        assertEquals("storage.cos.public-base-url 必须为不带路径、端口或参数的 HTTPS 域名基址", error.getMessage());
    }

    @Test
    void cosClientUsesHttpsTimeoutsAndNormalizesPublicBaseWithoutCloudProbe() {
        var properties = cosProperties();
        properties.setPublicBaseUrl("https://img.example.test///");
        var client = new CosStorageConfig().cosClient(properties);
        try {
            assertEquals("https://img.example.test", properties.getPublicBaseUrl());
            assertEquals(com.qcloud.cos.http.HttpProtocol.https, client.getClientConfig().getHttpProtocol());
            assertEquals(5000, client.getClientConfig().getConnectionTimeout());
            assertEquals(5000, client.getClientConfig().getConnectionRequestTimeout());
            assertEquals(30000, client.getClientConfig().getSocketTimeout());
            assertEquals(0, client.getClientConfig().getMaxErrorRetry());
        } finally {
            client.shutdown();
        }
    }

    private CosStorageProperties cosProperties() {
        var properties = new CosStorageProperties();
        properties.setEnabled(true);
        properties.setRegion("ap-guangzhou");
        properties.setBucket("covers-1250000000");
        properties.setSecretId("test-only-id");
        properties.setSecretKey("test-only-key");
        properties.setPublicBaseUrl("https://img.example.test");
        return properties;
    }
}
