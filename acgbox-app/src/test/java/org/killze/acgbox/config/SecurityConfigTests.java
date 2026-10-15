package org.killze.acgbox.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.time.Instant;
import java.util.List;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 管理端 JWT 认证和角色权限测试，不连接数据库。
 */
@SpringJUnitWebConfig(SecurityConfigTests.TestConfig.class)
@TestPropertySource(properties = {
        "jwt.secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "jwt.issuer=acgbox-admin",
        "jwt.audience=acgbox-admin"
})
class SecurityConfigTests {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private JwtEncoder jwtEncoder;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "EDITOR"})
    void shouldAllowManagementRolesToReadContent(String role) throws Exception {
        mockMvc.perform(get("/security-test/content")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + createToken(role)))
                .andExpect(status().isOk())
                .andExpect(content().string("ok"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "EDITOR"})
    void shouldAllowManagementRolesToModifyContent(String role) throws Exception {
        mockMvc.perform(post("/security-test/content")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + createToken(role)))
                .andExpect(status().isOk())
                .andExpect(content().string("ok"));
    }

    @Test
    void shouldRejectUserRoleToken() throws Exception {
        mockMvc.perform(get("/security-test/content")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + createToken("USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldRejectAnonymousBusinessRequest() throws Exception {
        mockMvc.perform(get("/security-test/content"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldRejectInvalidToken() throws Exception {
        mockMvc.perform(get("/security-test/content")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token"))
                .andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/admin/login/username", "/admin/login/email-password"})
    void shouldAllowAnonymousLoginRequests(String path) throws Exception {
        mockMvc.perform(post(path))
                .andExpect(status().isOk())
                .andExpect(content().string("ok"));
    }

    /**
     * 使用独立的测试密钥签发令牌，验证实际的解码和角色转换流程。
     */
    private String createToken(String role) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("acgbox-admin")
                .audience(List.of("acgbox-admin"))
                .subject("1")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(60))
                .claim("role", role)
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    @TestConfiguration
    @EnableWebMvc
    @EnableWebSecurity
    @Import({SecurityConfig.class, JwtConfig.class, TestController.class})
    static class TestConfig {
    }

    /**
     * 仅在本测试上下文注册的接口，不参与正式应用的组件扫描。
     */
    @TestComponent
    @RestController
    static class TestController {

        @GetMapping("/security-test/content")
        public String getContent() {
            return "ok";
        }

        @PostMapping({"/security-test/content", "/admin/login/username", "/admin/login/email-password"})
        public String postContent() {
            return "ok";
        }
    }
}
