# Redis + 双Token机制详解

## 一、为什么需要双Token机制？

### 1.1 单Token的问题

**传统单Token方案：**
- 只有一个JWT Token，有效期通常较长（如7天、30天）
- **问题1：安全性差** - Token一旦泄露，在有效期内都可以被使用
- **问题2：无法主动撤销** - 用户登出后，Token仍然有效，无法立即失效
- **问题3：刷新困难** - 如果要刷新Token，需要重新登录

### 1.2 双Token方案的优势

**双Token = Access Token (AT) + Refresh Token (RT)**

- **Access Token (AT)**：短期有效（如30分钟），用于访问API
- **Refresh Token (RT)**：长期有效（如30天），用于刷新AT

**优势：**
1. ✅ **安全性更高** - AT有效期短，即使泄露影响时间短
2. ✅ **可主动撤销** - RT存储在Redis，可以随时删除
3. ✅ **无感刷新** - 用户无需重新登录，自动刷新AT
4. ✅ **支持多设备** - 每个设备有独立的RT

---

## 二、核心原理

### 2.1 Token的生命周期

```
用户登录
    ↓
生成 AT (30分钟) + RT (30天)
    ↓
AT 放在 Header (Authorization: Bearer <token>)
RT 放在 HttpOnly Cookie (更安全，防止XSS)
    ↓
每次请求API → 使用 AT
AT过期 → 使用 RT 刷新 → 得到新的 AT + RT
用户登出 → 删除 RT + AT加入黑名单
```

### 2.2 Redis的作用

**Redis存储两类数据：**

1. **刷新令牌存储** (auth:rt:userId:device)
   - Key: `auth:rt:1:device123`
   - Value: 完整的RT字符串
   - TTL: 30天（与RT有效期一致）
   - **作用**：验证RT是否有效，支持主动撤销

2. **黑名单存储** (auth:bl:jti)
   - Key: `auth:bl:uuid-xxx-xxx`
   - Value: "1" (标记存在即可)
   - TTL: AT剩余有效期
   - **作用**：登出后，让已签发的AT立即失效

---

## 三、项目实现详解

### 3.1 整体架构

```
┌─────────────┐
│  前端应用    │
└──────┬──────┘
       │
       │ 1. 登录请求
       ↓
┌─────────────────────────────────┐
│      AuthController              │
│  - login()  登录                 │
│  - refresh() 刷新                │
│  - logout() 登出                 │
└──────┬──────────────────────────┘
       │
       ├──→ JwtUtil (生成/解析Token)
       │
       └──→ AuthTokenService (Redis操作)
              │
              ↓
       ┌──────────────┐
       │    Redis     │
       │ - RT存储     │
       │ - 黑名单     │
       └──────────────┘

每次API请求:
前端 → JwtAuthFilter (验证AT + 检查黑名单) → Controller
```

---

## 四、详细实现步骤

### 步骤1：用户登录 - 生成双Token

**代码位置：** `AuthController.login()`

```java
@PostMapping("/login")
public Result<AuthResponse> login(@Valid @RequestBody LoginRequest request, 
                                   HttpServletResponse resp, 
                                   HttpServletRequest req) {
    // 1. 验证用户名密码，获取用户信息
    User user = userService.loginAndGetUser(request);
    
    // 2. 生成设备指纹（用于多设备管理）
    String device = resolveDevice(req);
    
    // 3. 生成两个Token
    String at = jwtUtil.generateAccessToken(user, device);  // AT: 30分钟
    String rt = jwtUtil.generateRefreshToken(user, device); // RT: 30天
    
    // 4. 将RT存入Redis（用于后续验证和撤销）
    authTokenService.saveRefreshToken(
        user.getId(), 
        device, 
        rt, 
        Duration.ofMillis(jwtUtil.getRefreshTtlMs())
    );
    
    // 5. RT放入HttpOnly Cookie（前端无法通过JS访问，更安全）
    setRefreshCookie(resp, rt, (int) (jwtUtil.getRefreshTtlMs() / 1000));
    
    // 6. AT返回给前端（放在响应体中）
    AuthResponse ar = AuthResponse.of(at, jwtUtil.getAccessTtlMs(), ...);
    return Result.success("登录成功", ar);
}
```

**关键点解析：**

1. **设备指纹生成** (`resolveDevice`)
   ```java
   // 基于User-Agent + IP生成设备标识
   String deviceFingerprint = userAgent + "|" + ip;
   return String.valueOf(deviceFingerprint.hashCode());
   ```
   - 同一用户在不同设备登录，会有不同的device值
   - 支持多设备同时在线

2. **Token生成** (`JwtUtil.buildToken`)
   ```java
   // 每个Token都有唯一的jti (JWT ID)
   String jti = UUID.randomUUID().toString();
   claims.put("jti", jti);  // 用于黑名单管理
   claims.put("device", device);  // 设备标识
   claims.put("userId", user.getId());
   // ... 其他用户信息
   ```

3. **Redis存储RT**
   ```java
   // Key格式: auth:rt:userId:device
   // 例如: auth:rt:1:-1234567890
   redisTemplate.opsForValue().set(
       "auth:rt:1:-1234567890", 
       "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
       Duration.ofDays(30)
   );
   ```

4. **Cookie设置**
   ```java
   ResponseCookie cookie = ResponseCookie.from("rt", rt)
       .httpOnly(true)    // 防止XSS攻击，JS无法访问
       .secure(true)      // 仅HTTPS传输
       .sameSite("None")  // 支持跨域
       .maxAge(2592000)   // 30天
       .build();
   ```

**登录后的数据流：**
```
前端收到:
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",  // AT
  "expiresIn": 1800000,  // 30分钟
  "username": "admin",
  ...
}
Cookie: rt=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...  // RT

Redis中:
auth:rt:1:-1234567890 = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
```

---

### 步骤2：API请求 - 验证Access Token

**代码位置：** `JwtAuthFilter.doFilterInternal()`

```java
@Override
protected void doFilterInternal(HttpServletRequest request,
                                HttpServletResponse response,
                                FilterChain filterChain) {
    // 1. 从Header提取AT
    String authHeader = request.getHeader("Authorization");
    // 格式: "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
    
    if (authHeader != null && authHeader.startsWith("Bearer ")) {
        String token = authHeader.substring(7);
        
        try {
            // 2. 解析Token，提取用户信息
            Claims claims = jwtUtil.getClaimsFromToken(token);
            String jti = claims.get("jti", String.class);
            
            // 3. 检查是否在黑名单中（登出后的Token）
            if (authTokenService.isBlacklisted(jti)) {
                log.warn("Token已被加入黑名单");
                // 不设置认证信息，后续会被Spring Security拒绝
                filterChain.doFilter(request, response);
                return;
            }
            
            // 4. 提取用户信息
            String username = jwtUtil.parseUsername(token);
            Long userId = jwtUtil.parseUserId(token);
            String role = jwtUtil.getRoleFromToken(token);
            
            // 5. 创建认证对象，存入SecurityContext
            UserAuthentication authentication = new UserAuthentication(...);
            SecurityContextHolder.getContext().setAuthentication(authentication);
            
        } catch (Exception e) {
            // Token无效或过期，不设置认证信息
            log.error("JWT Token 验证失败: {}", e.getMessage());
        }
    }
    
    // 继续过滤器链
    filterChain.doFilter(request, response);
}
```

**验证流程：**
```
请求: GET /api/users
Header: Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...

JwtAuthFilter:
  1. 提取Token
  2. 解析JWT (验证签名、过期时间)
  3. 检查黑名单 (Redis查询: auth:bl:jti)
  4. 如果通过 → 设置认证信息
  5. 如果失败 → 不设置认证信息 → Spring Security拒绝请求
```

**黑名单检查：**
```java
// AuthTokenService.isBlacklisted()
public boolean isBlacklisted(String jti) {
    // 检查Redis中是否存在: auth:bl:jti
    return redisTemplate.hasKey("auth:bl:" + jti);
}
```

---

### 步骤3：Token刷新 - 旋转机制

**代码位置：** `AuthController.refresh()`

**为什么需要刷新？**
- AT有效期只有30分钟，过期后需要刷新
- 使用RT获取新的AT，用户无需重新登录

```java
@PostMapping("/refresh")
public Result<AuthResponse> refresh(HttpServletRequest req, 
                                    HttpServletResponse resp) {
    // 1. 从Cookie或Header提取RT
    String rt = extractRefreshToken(req);
    if (rt == null) {
        throw new PermissionDeniedException("缺少刷新令牌");
    }
    
    // 2. 解析RT，获取userId和device
    Claims claims = jwtUtil.parseToken(rt);
    Long userId = claims.get("userId", Long.class);
    String device = claims.get("device", String.class);
    
    // 3. 从Redis获取存储的RT，进行对比验证
    String storedRt = authTokenService.getRefreshToken(userId, device)
        .orElseThrow(() -> new PermissionDeniedException("刷新令牌不存在"));
    
    // 4. 验证RT是否匹配（防止RT被篡改）
    if (!storedRt.equals(rt)) {
        throw new PermissionDeniedException("刷新令牌已失效");
    }
    
    // 5. 验证用户是否还存在
    UserResponse byId = userService.findById(userId);
    User user = userService.toEntity(byId);
    
    // 6. 生成新的AT和RT（Token旋转）
    String newAt = jwtUtil.generateAccessToken(user, device);
    String newRt = jwtUtil.generateRefreshToken(user, device);
    
    // 7. 删除旧的RT（重要：防止旧RT被重复使用）
    authTokenService.deleteRefreshToken(userId, device);
    
    // 8. 存储新的RT到Redis
    authTokenService.saveRefreshToken(userId, device, newRt, ...);
    
    // 9. 更新Cookie中的RT
    setRefreshCookie(resp, newRt, ...);
    
    // 10. 返回新的AT
    return Result.success("令牌刷新成功", AuthResponse.of(newAt, ...));
}
```

**Token旋转机制：**
```
旧状态:
  AT: token1 (已过期)
  RT: token2 (存储在Redis)
  Cookie: rt=token2

刷新后:
  1. 验证 token2 是否在Redis中 ✅
  2. 生成新的 token3 (AT) 和 token4 (RT)
  3. 删除Redis中的 token2 ❌
  4. 存储 token4 到Redis ✅
  5. 更新Cookie为 token4 ✅
  6. 返回 token3 (新AT) ✅

新状态:
  AT: token3 (新的，30分钟有效)
  RT: token4 (新的，30天有效)
  Cookie: rt=token4
```

**为什么删除旧RT？**
- 防止旧RT被重复使用（重放攻击）
- 如果RT泄露，每次刷新都会生成新的，旧的自动失效
- 提高安全性

---

### 步骤4：用户登出 - 撤销Token

**代码位置：** `AuthController.logout()`

```java
@PostMapping("/logout")
public Result<Void> logout(HttpServletRequest req, HttpServletResponse resp) {
    // 1. 提取AT和RT
    String at = extractAccessToken(req);
    String rt = extractRefreshToken(req);
    
    // 2. 如果AT存在，加入黑名单
    if (at != null) {
        Claims atClaims = jwtUtil.parseToken(at);
        String jti = atClaims.get("jti", String.class);
        
        // 计算AT剩余有效期
        Long expMillis = atClaims.getExpiration().getTime() - System.currentTimeMillis();
        
        if (expMillis > 0) {
            // 将AT的jti加入黑名单，TTL为剩余有效期
            authTokenService.blacklistAccessToken(jti, Duration.ofMillis(expMillis));
        }
    }
    
    // 3. 如果RT存在，从Redis删除
    if (rt != null) {
        Claims rtClaims = jwtUtil.parseToken(rt);
        Long userId = rtClaims.get("userId", Long.class);
        String device = rtClaims.get("device", String.class);
        
        // 删除Redis中的RT
        authTokenService.deleteRefreshToken(userId, device);
    }
    
    // 4. 使Cookie过期
    expireRefreshCookie(resp);
    
    return Result.success("注销成功", null);
}
```

**登出流程：**
```
登出前:
  Redis: auth:rt:1:device123 = "token_rt_xxx"
  Redis: (无黑名单)
  用户持有: AT (token_at_xxx) 和 RT (token_rt_xxx)

登出操作:
  1. 解析AT，获取jti = "uuid-123"
  2. Redis存储: auth:bl:uuid-123 = "1" (TTL = AT剩余时间)
  3. Redis删除: auth:rt:1:device123
  4. Cookie设置为过期

登出后:
  Redis: auth:bl:uuid-123 = "1" (30分钟内有效)
  Redis: auth:rt:1:device123 (已删除)
  
结果:
  - AT立即失效（被黑名单拦截）
  - RT已删除，无法刷新
  - 用户必须重新登录
```

**黑名单机制：**
```java
// AuthTokenService.blacklistAccessToken()
public void blacklistAccessToken(String jti, Duration ttl) {
    // Key: auth:bl:jti
    // Value: "1" (仅标记存在即可)
    // TTL: AT的剩余有效期
    redisTemplate.opsForValue().set(
        "auth:bl:" + jti, 
        "1", 
        ttl
    );
}
```

**为什么黑名单TTL是AT剩余时间？**
- AT过期后，黑名单自动清除，节省Redis空间
- 不需要手动清理过期黑名单

---

## 五、Redis数据结构详解

### 5.1 刷新令牌存储

**Key格式：** `auth:rt:{userId}:{device}`

**示例：**
```
Key:   auth:rt:1:-1234567890
Value: eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJhZG1pbiIsInVzZXJJZCI6MSwiZGV2aWNlIjoiLTEyMzQ1Njc4OTAiLCJqdGkiOiJ1dWlkLTEyMyIsImV4cCI6MTcxMjM0NTY3OH0.xxx
TTL:   2592000秒 (30天)
```

**操作：**
```java
// 保存
saveRefreshToken(1L, "-1234567890", "token_string", Duration.ofDays(30));

// 获取
Optional<String> rt = getRefreshToken(1L, "-1234567890");

// 删除
deleteRefreshToken(1L, "-1234567890");
```

**多设备支持：**
```
用户1在设备A登录:
  auth:rt:1:deviceA = "rt_token_A"

用户1在设备B登录:
  auth:rt:1:deviceB = "rt_token_B"

两个设备可以同时在线，互不影响
```

### 5.2 黑名单存储

**Key格式：** `auth:bl:{jti}`

**示例：**
```
Key:   auth:bl:550e8400-e29b-41d4-a716-446655440000
Value: "1"
TTL:   1800秒 (30分钟，AT的剩余有效期)
```

**操作：**
```java
// 加入黑名单
blacklistAccessToken("550e8400-e29b-41d4-a716-446655440000", Duration.ofMinutes(30));

// 检查是否在黑名单
boolean isBlacklisted = isBlacklisted("550e8400-e29b-41d4-a716-446655440000");
```

**自动过期：**
- Redis的TTL机制自动删除过期黑名单
- 不需要手动清理

---

## 六、完整流程图

### 6.1 登录流程

```
用户 → POST /api/auth/login
      ↓
[AuthController.login()]
      ↓
1. 验证用户名密码
      ↓
2. 生成设备指纹 (User-Agent + IP)
      ↓
3. 生成AT (30分钟) + RT (30天)
      ↓
4. RT存入Redis: auth:rt:userId:device
      ↓
5. RT放入HttpOnly Cookie
      ↓
6. AT返回给前端 (响应体)
      ↓
前端收到: {token: "AT", ...} + Cookie: rt="RT"
```

### 6.2 API请求流程

```
前端 → GET /api/users
      Header: Authorization: Bearer AT
      ↓
[JwtAuthFilter.doFilterInternal()]
      ↓
1. 提取AT
      ↓
2. 解析JWT (验证签名、过期)
      ↓
3. 检查黑名单: Redis查询 auth:bl:jti
      ↓
4a. 如果在黑名单 → 拒绝请求 (401)
      ↓
4b. 如果不在黑名单 → 设置认证信息
      ↓
5. 继续过滤器链 → Controller处理请求
```

### 6.3 刷新流程

```
前端 → POST /api/auth/refresh
      Cookie: rt="RT"
      ↓
[AuthController.refresh()]
      ↓
1. 提取RT (Cookie或Header)
      ↓
2. 解析RT，获取userId和device
      ↓
3. 从Redis获取存储的RT: auth:rt:userId:device
      ↓
4. 对比RT是否匹配
      ↓
5a. 不匹配 → 抛出异常 (401)
      ↓
5b. 匹配 → 继续
      ↓
6. 生成新的AT + RT
      ↓
7. 删除旧的RT (Redis)
      ↓
8. 存储新的RT (Redis)
      ↓
9. 更新Cookie
      ↓
10. 返回新的AT
      ↓
前端收到: {token: "新AT", ...} + Cookie: rt="新RT"
```

### 6.4 登出流程

```
用户 → POST /api/auth/logout
      Header: Authorization: Bearer AT
      Cookie: rt="RT"
      ↓
[AuthController.logout()]
      ↓
1. 提取AT和RT
      ↓
2. 解析AT，获取jti
      ↓
3. 计算AT剩余有效期
      ↓
4. 将jti加入黑名单: auth:bl:jti (TTL=剩余时间)
      ↓
5. 解析RT，获取userId和device
      ↓
6. 删除RT: Redis删除 auth:rt:userId:device
      ↓
7. 使Cookie过期
      ↓
返回: {message: "注销成功"}
```

---

## 七、安全性分析

### 7.1 防护措施

1. **XSS攻击防护**
   - RT使用HttpOnly Cookie，JS无法访问
   - 即使XSS攻击，也无法窃取RT

2. **CSRF攻击防护**
   - AT放在Header中，不受CSRF影响
   - RT在Cookie中，但刷新接口需要验证RT，CSRF无法获取新AT

3. **Token泄露防护**
   - AT有效期短（30分钟），泄露影响时间短
   - RT存储在Redis，可以主动撤销
   - 登出后AT立即加入黑名单

4. **重放攻击防护**
   - Token旋转机制：每次刷新都生成新的RT
   - 旧RT被删除，无法重复使用

5. **多设备管理**
   - 每个设备有独立的RT
   - 可以单独撤销某个设备的访问权限

### 7.2 潜在风险与建议

1. **Cookie SameSite设置**
   - 当前设置为"None"，需要HTTPS
   - 生产环境必须使用HTTPS

2. **设备指纹精度**
   - 当前基于User-Agent + IP
   - 建议：可以加入更多设备特征（屏幕分辨率、时区等）

3. **刷新频率限制**
   - 当前无限制，可能被滥用
   - 建议：添加刷新频率限制（如1分钟内最多刷新1次）

4. **Token类型区分**
   - 当前AT和RT结构相同，仅通过有效期区分
   - 建议：在claims中添加`type: "access"`或`type: "refresh"`

---

## 八、总结

### 8.1 核心要点

1. **双Token分工**
   - AT：短期有效，用于API访问
   - RT：长期有效，用于刷新AT

2. **Redis的作用**
   - 存储RT：支持验证和主动撤销
   - 黑名单：让登出后的AT立即失效

3. **Token旋转**
   - 每次刷新都生成新的AT和RT
   - 删除旧RT，防止重放攻击

4. **多设备支持**
   - 基于设备指纹区分不同设备
   - 每个设备独立的RT管理

### 8.2 优势

✅ **安全性高** - AT短期有效，RT可主动撤销  
✅ **用户体验好** - 无感刷新，无需频繁登录  
✅ **灵活性强** - 支持多设备，可单独管理  
✅ **可扩展** - 易于添加设备管理、登录历史等功能  

### 8.3 适用场景

- ✅ 需要高安全性的Web应用
- ✅ 需要支持多设备登录
- ✅ 需要无感刷新Token
- ✅ 需要主动撤销Token的能力

---

## 九、代码关键点速查

| 功能 | 代码位置 | 关键方法 |
|------|---------|---------|
| 登录生成Token | `AuthController.login()` | `jwtUtil.generateAccessToken()`<br>`jwtUtil.generateRefreshToken()` |
| 存储RT到Redis | `AuthTokenService.saveRefreshToken()` | `redisTemplate.opsForValue().set()` |
| 验证AT | `JwtAuthFilter.doFilterInternal()` | `jwtUtil.getClaimsFromToken()`<br>`authTokenService.isBlacklisted()` |
| 刷新Token | `AuthController.refresh()` | `authTokenService.getRefreshToken()`<br>`authTokenService.deleteRefreshToken()` |
| 登出撤销 | `AuthController.logout()` | `authTokenService.blacklistAccessToken()`<br>`authTokenService.deleteRefreshToken()` |

---

**文档版本：** v1.0  
**最后更新：** 2024年
