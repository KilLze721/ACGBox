package org.killze.acgbox.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Spring Security 安全配置类。
 *
 * 由启动类通过 @Import 引入，统一配置请求访问权限、登录方式和 CSRF 防护规则。
 */
@Configuration
public class SecurityConfig {

    /**
     * 配置并注册安全过滤器链，在请求进入控制器前执行安全检查。
     *
     * @param http HTTP 安全配置对象
     * @return 安全过滤器链
     * @throws Exception 构建安全过滤器链时发生的异常
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        // 将 JWT 中的 role 字段转换为 Spring Security 角色权限。
        JwtGrantedAuthoritiesConverter authoritiesConverter = new JwtGrantedAuthoritiesConverter();
        authoritiesConverter.setAuthoritiesClaimName("role");
        authoritiesConverter.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter jwtAuthenticationConverter = new JwtAuthenticationConverter();
        jwtAuthenticationConverter.setJwtGrantedAuthoritiesConverter(authoritiesConverter);

        // 登录入口允许匿名访问，其余接口只允许管理员访问。
        http.authorizeHttpRequests(authorize -> authorize
                .requestMatchers(HttpMethod.POST,
                        "/admin/login/username",
                        "/admin/login/email-password").permitAll()
                .anyRequest().hasRole("ADMIN")
        );

        // 关闭表单登录、HTTP Basic 和 CSRF，使用无状态认证。
        http.formLogin(form -> form.disable());
        http.httpBasic(basic -> basic.disable());
        http.csrf(csrf -> csrf.disable());
        http.sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
        );

        // 启用 JWT 请求认证，并应用角色转换规则。
        http.oauth2ResourceServer(resourceServer -> resourceServer
                .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter))
        );

        return http.build();
    }

    /**
     * 配置密码编码器，用于生成密码哈希和校验登录密码。
     *
     * @return 密码编码器
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
