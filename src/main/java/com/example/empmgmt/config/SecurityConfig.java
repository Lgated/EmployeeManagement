package com.example.empmgmt.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter) {
        this.jwtAuthFilter = jwtAuthFilter;
    }

    /**
     * 配置安全过滤器链
     * 这是 Spring Security 的核心配置方法
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http


                // 1. 禁用 CSRF（跨站请求伪造）保护
                // 因为使用 JWT，不需要 CSRF 保护
                .csrf(AbstractHttpConfigurer::disable)

                // 2. 配置会话管理策略
                // STATELESS：无状态，不使用 Session（JWT 是无状态的）
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // 3. 配置请求授权规则
                .authorizeHttpRequests(auth -> auth
                        // 允许未认证访问的路径（登录、注册接口）
                        .requestMatchers("/api/auth/**").permitAll()
                        // 其他所有请求都需要认证
                        .anyRequest().authenticated()
                )
                // 4. 添加 JWT 认证过滤器
                // 在 UsernamePasswordAuthenticationFilter 之前执行
                // 这样可以在 Spring Security 默认认证之前先验证 JWT
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);


        return http.build();
    }

    /**
     * 配置密码编码器
     * 使用 BCrypt 算法加密密码
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
