package com.example.empmgmt.controller;

import com.example.empmgmt.common.Exception.BusinessException;
import com.example.empmgmt.common.Exception.PermissionDeniedException;
import com.example.empmgmt.common.util.JwtUtil;
import com.example.empmgmt.domain.User;
import com.example.empmgmt.dto.request.LoginRequest;
import com.example.empmgmt.dto.request.RegisterRequest;
import com.example.empmgmt.dto.response.AuthResponse;
import com.example.empmgmt.dto.response.Result;
import com.example.empmgmt.dto.response.UserResponse;
import com.example.empmgmt.service.Impl.AuthTokenService;
import com.example.empmgmt.service.Impl.RateLimitService;
import com.example.empmgmt.service.UserService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

@RestController
@RequestMapping("/api/auth")
@Slf4j
public class AuthController {

    @Value("${app.https.enabled}")
    private boolean httpEnabled;

    private final UserService userService;
    private final JwtUtil jwtUtil;
    private final AuthTokenService authTokenService;
    private final RateLimitService rateLimitService;


    public AuthController(UserService userService,
                          JwtUtil jwtUtil,
                          AuthTokenService authTokenService,
                          RateLimitService rateLimitService) {
        this.jwtUtil = jwtUtil;
        this.authTokenService = authTokenService;
        this.userService = userService;
        this.rateLimitService = rateLimitService;
    }

    /**
     * 用户注册
     */
    @PostMapping("/register")
    public Result<AuthResponse> register(@Valid @RequestBody RegisterRequest request){
        try{
            AuthResponse register = userService.register(request);
            return Result.success(register);
        }catch (IllegalArgumentException e){
            return Result.error(500,e.getMessage());
        }
    }


    // 登录 ： 签发AT + RT ，RT 写cookie（可以改为返回Header）
    @PostMapping("/login")
    public Result<AuthResponse> login(@Valid @RequestBody LoginRequest request, HttpServletResponse resp, HttpServletRequest req) {

        // 1. 未登录前按 IP 限流
        String ip = getClientIpAddress(req);

        //TODO : 现在capacity 为 2，仅测试，实际可调大一些
        // 例如：容量 30，每秒 0.5 个令牌 => 每分钟约补充 30 个
        boolean allowed = rateLimitService.tryAcquire(
                "rate:login:ip:",
                ip,
                2,
                0.5
        );
        if (!allowed) {
            throw new BusinessException("请求过于频繁，请稍后再试");
        }

        //限流通过，继续登录流程
        User user = userService.loginAndGetUser(request); // 假设返回 User，包含角色等
        String device = resolveDevice(req);

        String at = jwtUtil.generateAccessToken(user, device);
        String rt = jwtUtil.generateRefreshToken(user, device);

        // RT 存 Redis
        authTokenService.saveRefreshToken(user.getId(), device, rt, Duration.ofMillis(jwtUtil.getRefreshTtlMs()));

        // RT 放 HttpOnly Cookie（如需 Header 自行调整）
        setRefreshCookie(resp, rt, (int) (jwtUtil.getRefreshTtlMs() / 1000));

        AuthResponse ar = AuthResponse.of(at, jwtUtil.getAccessTtlMs(), user.getUsername(),
                user.getRole(), user.getDepartment(), user.getEmployeeId());
        return Result.success("登录成功", ar);
    }


    /**
     * 刷新 ： 校验RT、旋转RT
     * @param req
     * @param resp
     * @return
     */
    @PostMapping("/refresh")
    //TODO : 需要创建认证异常类
    public Result<AuthResponse> refresh(HttpServletRequest req, HttpServletResponse resp) {
        String rt = extractRefreshToken(req);

        if (rt == null) {
            throw new PermissionDeniedException("缺少刷新令牌");
        }

        // 解析 RT
        Claims claims = jwtUtil.parseToken(rt);
        Long userId = claims.get("userId", Long.class);
        String device = claims.get("device", String.class);
        String jti = claims.get("jti", String.class);

        // Redis 对比
        String keyRt = authTokenService.getRefreshToken(userId, device)
                .orElseThrow(() -> new PermissionDeniedException("刷新令牌不存在"));
        if (!keyRt.equals(rt)){
            throw new PermissionDeniedException("刷新令牌已失效");
        }

        // 旋转：颁发新 AT + 新 RT
        UserResponse byId = userService.findById(userId);// 验证用户是否存在
        User user = userService.toEntity(byId);
        String newAt = jwtUtil.generateAccessToken(user, device);
        String newRt = jwtUtil.generateRefreshToken(user, device);

        // 删除旧的RT（显式删除，确保安全）
        authTokenService.deleteRefreshToken(userId, device);

        // 存新的RT到 Redis
        authTokenService.saveRefreshToken(userId, device, newRt, Duration.ofMillis(jwtUtil.getRefreshTtlMs()));
        // 重写 Cookie
        setRefreshCookie(resp, newRt, (int) (jwtUtil.getRefreshTtlMs() / 1000));
        AuthResponse ar = AuthResponse.of(newAt, jwtUtil.getAccessTtlMs(), user.getUsername(),
                user.getRole(), user.getDepartment(), user.getEmployeeId());
        return Result.success("令牌刷新成功", ar);
    }


    /**
     * 登出：删除RT，把当前AT加入黑名单
     * @param req
     * @param resp
     * @return
     */
    @PostMapping("/logout")
    public Result<Void> logout(HttpServletRequest req, HttpServletResponse resp) {
        // 提取 AT 和 RT
        String at = extractAccessToken(req);
        String rt = extractRefreshToken(req);

        if (at != null) {
            try {
                // 将当前 AT 加入黑名单 - AT过期就不要加入黑名单了 不然jwtUtil.parseToken(at) 就会抛异常
                // JWT 在解析一个过期 token 时会直接抛出 ExpiredJwtException，没有被你捕获，于是全局异常处理器报“系统异常
                Claims atClaims = jwtUtil.parseToken(at);
                String jti = atClaims.get("jti", String.class);
                Long expMillis = atClaims.getExpiration().getTime() - System.currentTimeMillis();
                // at剩余时间为黑名单存活时间，不需要手动清楚黑名单
                if (expMillis > 0) {
                    authTokenService.blacklistAccessToken(jti, Duration.ofMillis(expMillis));
                }
            }catch (io.jsonwebtoken.ExpiredJwtException e){
                // AT 已过期：认为用户本身就已经“失效”，不再做黑名单处理，直接忽略
                // token 已过期，无需加入黑名单
                log.debug("Logout with expired access token, ignore blacklist\", e");
            }
        }
        if (rt != null) {
            try{
            Claims rtClaims = jwtUtil.parseToken(rt);
            Long userId = rtClaims.get("userId", Long.class);
            String device = rtClaims.get("device", String.class);
            authTokenService.deleteRefreshToken(userId, device);
            }catch (io.jsonwebtoken.ExpiredJwtException e){
                // RT 已过期：说明刷新令牌已经失效，Redis 里的 RT 很可能也自然过期了
                // token 已过期，Redis 中已自动删除，无需处理
                log.debug("Logout with expired refresh token, ignore delete from redis\", e");
            }
        }

        // 使 Cookie 过期
        expireRefreshCookie(resp);

        return Result.success("注销成功", null);
    }

    // 设置 Refresh Token Cookie
    private void setRefreshCookie(HttpServletResponse resp, String rt, int maxAgeSeconds) {
        ResponseCookie cookie = ResponseCookie.from("rt", rt)
                .httpOnly(true)         // 防止XSS攻击，JS无法访问
                .secure(httpEnabled)    // 根据环境配置（开发false，生产true）
                .sameSite(httpEnabled ? "None" : "Lax")  // 开发环境Lax，生产环境None
                .path("/")
                .maxAge(maxAgeSeconds)
                .build();
        resp.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    // 注销时使 Refresh Token Cookie 过期
    private void expireRefreshCookie(HttpServletResponse resp) {
        ResponseCookie cookie = ResponseCookie.from("rt", "")
                .httpOnly(true)         // 仅后端可访问
                .secure(httpEnabled)    // 根据环境配置
                .sameSite(httpEnabled ? "None" : "Lax")
                .path("/")
                .maxAge(0)
                .build();
        resp.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    //提取 Refresh Token
    private String extractRefreshToken(HttpServletRequest req) {
        // 优先 Cookie
        if (req.getCookies() != null) {
            for (Cookie c : req.getCookies()) {
                if ("rt".equals(c.getName())) return c.getValue();
            }
        }
        // 也可支持 Header: x-refresh-token
        String h = req.getHeader("x-refresh-token");
        return (h == null || h.isBlank()) ? null : h.trim();
    }

    //提取 Access Token
    private String extractAccessToken(HttpServletRequest req) {
        String auth = req.getHeader(HttpHeaders.AUTHORIZATION);
        if (auth != null && auth.startsWith("Bearer ")) {
            return auth.substring(7);
        }
        return null;
    }

    /**
     * 解析设备指纹
     * 基于User-Agent和IP地址生成设备标识
     */
    private String resolveDevice(HttpServletRequest request) {
        // 获取User-Agent
        String userAgent = request.getHeader("User-Agent");
        if (userAgent == null || userAgent.isBlank()) {
            userAgent = "Unknown";
        }

        // 获取IP地址
        String ip = getClientIpAddress(request);

        // 组合生成设备指纹（使用User-Agent + IP的哈希值）
        String deviceFingerprint = userAgent + "|" + ip;
        return String.valueOf(deviceFingerprint.hashCode());
    }

    /**
     * 获取客户端真实IP地址
     */
    private String getClientIpAddress(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        // 处理多个IP的情况，取第一个
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip != null ? ip : "0.0.0.0";
    }
}
