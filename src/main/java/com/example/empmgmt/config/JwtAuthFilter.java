package com.example.empmgmt.config;

import com.example.empmgmt.security.UserAuthentication;
import com.example.empmgmt.common.util.JwtUtil;
import com.example.empmgmt.service.Impl.AuthTokenService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Slf4j
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final AuthTokenService authTokenService;

    public JwtAuthFilter(JwtUtil jwtUtil, AuthTokenService authTokenService){
        this.jwtUtil = jwtUtil;
        this.authTokenService = authTokenService;
    }


    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        // 1、从请求头中获取token
        String authHeader = request.getHeader("Authorization");


        // 2、检查Token 格式（Bearer <token>）
        if (authHeader != null && authHeader.startsWith("Bearer ")){
            String token = authHeader.substring(7); //提取token了,去掉 Bearer

            try{
                // 3. 解析Token，提取用户信息
                Claims claims = jwtUtil.getClaimsFromToken(token);
                String jti = claims.get("jti", String.class);

                // 3. 检查是否在黑名单中（登出后的Token）
                if (authTokenService.isBlacklisted(jti)) {
                    log.warn("Token已被加入黑名单");
                    // 不设置认证信息，后续会被Spring Security拒绝
                    filterChain.doFilter(request, response);
                    return;
                }

                String username = jwtUtil.parseUsername(token);
                Long userId = jwtUtil.parseUserId(token);
                String role = jwtUtil.getRoleFromToken(token);
                String department = jwtUtil.getDepartmentFromToken(token);
                // 解析员工ID（可能为空）
                Long employeeId = claims.get("employeeId", Long.class);

                //4、验证Token是否有效
                //"Token 里有人且 Spring 还没认证过，才继续走 JWT 认证流程，避免重复干活。
                if(username != null && SecurityContextHolder.getContext().getAuthentication() == null){
                    //5、创建认证对象（包含完整用户信息）
                    UserAuthentication authentication = new UserAuthentication(
                            username,
                            null,
                            List.of(),
                            userId,
                            role,         // 传入角色
                            department,   // 传入部门
                            employeeId    // 传入员工ID
                    );

                    //6. 设置认证详情
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                    //7. 将认证信息存入 SecurityContext（Spring Security 的上下文）
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                    log.debug("用户 {} (ID: {}, 角色: {}) 认证成功", username, userId, role);
                }
            }catch (Exception e){
                // Token 无效或过期，继续执行（不设置认证信息）
                log.error("JWT Token 验证失败: {}", e.getMessage());
            }
        }
        // 8. 继续执行过滤器链
        filterChain.doFilter(request, response);
    }
}
