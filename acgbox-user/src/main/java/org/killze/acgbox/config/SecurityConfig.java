package org.killze.acgbox.config;

import org.killze.acgbox.mapper.UserAccountMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
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
     * @param userAccountMapper 用户账号数据访问接口
     * @return 安全过滤器链
     * @throws Exception 构建安全过滤器链时发生的异常
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, UserAccountMapper userAccountMapper) throws Exception {
        // 校验数据库中的账号状态和令牌版本，再使用 JWT 中的角色生成权限。
        JwtAccountAuthoritiesConverter authoritiesConverter = new JwtAccountAuthoritiesConverter(userAccountMapper);
        JwtAuthenticationConverter jwtAuthenticationConverter = new JwtAuthenticationConverter();
        jwtAuthenticationConverter.setJwtGrantedAuthoritiesConverter(authoritiesConverter);

        // 登录入口允许匿名访问，用户管理仅允许管理员，其余接口允许管理员和编辑。
        http.authorizeHttpRequests(authorize -> authorize
                .requestMatchers(HttpMethod.POST,
                        "/admin/login/username",
                        "/admin/login/email-password").permitAll()
                .requestMatchers("/admin/users/**").hasRole("ADMIN")
                .anyRequest().hasAnyRole("ADMIN", "EDITOR")
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
