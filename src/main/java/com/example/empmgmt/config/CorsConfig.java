package com.example.empmgmt.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 *  全局 CORS 配置
 */
@Configuration
public class CorsConfig {

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        // 1. 允许哪些前端域名访问
        // 开发环境：Vite 默认是 http://localhost:3000
        config.setAllowedOrigins(List.of("http://localhost:3000"));

        // 2. 允许哪些 HTTP 方法
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));

        // 3. 允许携带哪些请求头
        config.setAllowedHeaders(List.of("*"));

        // 4. 是否允许携带 Cookie / Authorization 之类的凭证
        config.setAllowCredentials(true);

        // 5. 预检请求（OPTIONS）的缓存时间，单位秒
        config.setMaxAge(3600L);

        // 应用于所有接口
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
