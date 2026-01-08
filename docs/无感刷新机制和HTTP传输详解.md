# 无感刷新机制和HTTP传输详解

## 一、无感刷新机制详解

### 1.1 什么是无感刷新？

**无感刷新** = 用户在使用应用时，当Access Token过期后，前端自动使用Refresh Token获取新的Access Token，用户完全感觉不到这个过程，无需重新登录。

### 1.2 无感刷新的完整流程

#### 场景：用户正在浏览员工列表，AT过期了

```
时间线：
T0: 用户登录，获得 AT(30分钟) + RT(30天)
T1: 用户浏览员工列表，使用AT访问API ✅
T2: 用户继续浏览（AT还有效）✅
...
T30分钟: AT过期了！❌
```

**传统方式（有感知）：**
```
用户操作 → AT过期 → 401错误 → 跳转登录页 → 用户需要重新输入密码 😞
```

**无感刷新（无感知）：**
```
用户操作 → AT过期 → 401错误 → 前端自动用RT刷新 → 获得新AT → 自动重试原请求 → 用户无感知 😊
```

### 1.3 无感刷新的实现步骤（详细）

#### 步骤1：前端发送API请求（使用AT）

```javascript
// 用户点击"查看员工列表"
GET /api/employees
Headers:
  Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...  // AT
```

#### 步骤2：后端验证AT，发现已过期

```java
// JwtAuthFilter.doFilterInternal()
// 解析AT时抛出异常：Token已过期
catch (ExpiredJwtException e) {
    // AT过期了
    // 返回401状态码
}
```

#### 步骤3：前端收到401，触发自动刷新

```javascript
// axios响应拦截器
request.interceptors.response.use(
  (response) => response,
  async (error) => {
    if (error.response?.status === 401) {
      // AT过期了，尝试刷新
      try {
        // 调用刷新接口
        const newToken = await refreshToken()
        // 更新本地存储的AT
        localStorage.setItem('token', newToken)
        // 重试原来的请求（使用新AT）
        error.config.headers.Authorization = `Bearer ${newToken}`
        return request(error.config)  // 重新发送原请求
      } catch (refreshError) {
        // 刷新失败，跳转登录
        window.location.href = '/login'
      }
    }
  }
)
```

#### 步骤4：前端调用刷新接口

```javascript
// 刷新Token
POST /api/auth/refresh
// 注意：这里不需要手动传RT，浏览器会自动带上Cookie
// Cookie: rt=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...  // RT自动发送
```

#### 步骤5：后端验证RT，生成新Token

```java
// AuthController.refresh()
// 1. 从Cookie提取RT
String rt = extractRefreshToken(req);  // 从Cookie中获取

// 2. 从Redis验证RT
String storedRt = authTokenService.getRefreshToken(userId, device)
    .orElseThrow(() -> new PermissionDeniedException("刷新令牌不存在"));

// 3. 对比RT是否匹配
if (!storedRt.equals(rt)) {
    throw new PermissionDeniedException("刷新令牌已失效");
}

// 4. 生成新的AT和RT（Token旋转）
String newAt = jwtUtil.generateAccessToken(user, device);
String newRt = jwtUtil.generateRefreshToken(user, device);

// 5. 删除旧RT，存储新RT
authTokenService.deleteRefreshToken(userId, device);
authTokenService.saveRefreshToken(userId, device, newRt, ...);

// 6. 更新Cookie中的RT
setRefreshCookie(resp, newRt, ...);

// 7. 返回新AT
return Result.success("令牌刷新成功", AuthResponse.of(newAt, ...));
```

#### 步骤6：前端收到新AT，更新并重试

```javascript
// 刷新成功，收到新AT
{
  code: 200,
  data: {
    token: "新的AT字符串",
    expiresIn: 1800000,
    ...
  }
}

// 更新本地存储
localStorage.setItem('token', newToken)

// 重试原来的请求
GET /api/employees
Headers:
  Authorization: Bearer 新的AT字符串  // 使用新AT
```

#### 步骤7：原请求成功，用户无感知

```javascript
// 用户看到员工列表数据，完全不知道刚才AT过期了
```

### 1.4 Token旋转的作用

**Token旋转** = 每次刷新时，不仅生成新的AT，还生成新的RT，并删除旧的RT。

#### 为什么需要Token旋转？

**场景1：RT泄露了**

```
时间T0: 用户登录
  AT1, RT1 生成
  RT1 存入Redis: auth:rt:1:deviceA = "RT1"

时间T1: 黑客窃取了RT1（从Cookie中）
  黑客可以：使用RT1刷新，获得新AT

时间T2: 用户正常刷新（Token旋转）
  后端：生成新AT2 + 新RT2
  后端：删除旧RT1，存储新RT2
  Redis: auth:rt:1:deviceA = "RT2"  // RT1已被删除

时间T3: 黑客尝试使用旧RT1刷新
  后端：从Redis获取 → 得到RT2
  后端：对比 RT1 ≠ RT2 → 拒绝！❌
  结果：黑客的RT1失效了，无法继续使用
```

**场景2：没有Token旋转（危险）**

```
时间T0: 用户登录，RT1生成
时间T1: 黑客窃取RT1
时间T2: 用户刷新，但RT1仍然有效
  黑客可以一直使用RT1刷新，获得新AT
  结果：RT1泄露后，黑客可以长期使用
```

**总结：Token旋转让泄露的RT在下次刷新后立即失效，提高安全性。**

### 1.5 刷新机制总结

**你的理解基本正确！**

✅ **AT过期了之后，就去判断RT** - 正确！
✅ **RT验证成功就一起刷新，返回给前端新的AT** - 正确！

**完整流程：**
```
1. AT过期 → 401错误
2. 前端自动调用 /api/auth/refresh
3. 后端从Cookie提取RT
4. 后端从Redis验证RT是否有效
5. RT验证成功 → 生成新AT + 新RT（Token旋转）
6. 删除旧RT，存储新RT到Redis
7. 更新Cookie中的RT
8. 返回新AT给前端
9. 前端更新AT，重试原请求
10. 用户无感知，继续使用
```

---

## 二、HTTP传输详解

### 2.1 HTTP请求的结构

一个完整的HTTP请求包含以下部分：

```
┌─────────────────────────────────────┐
│ 请求行 (Request Line)                │
│ GET /api/employees HTTP/1.1         │
├─────────────────────────────────────┤
│ 请求头 (Request Headers)            │
│ Host: localhost:8080                │
│ Authorization: Bearer token123      │
│ User-Agent: Mozilla/5.0...          │
│ Cookie: rt=refresh_token_123        │
│ Content-Type: application/json       │
├─────────────────────────────────────┤
│ 空行                                 │
├─────────────────────────────────────┤
│ 请求体 (Request Body)               │
│ { "name": "张三" }                   │
└─────────────────────────────────────┘
```

### 2.2 Header（请求头）详解

**Header是什么？**
- Header是HTTP请求/响应的元数据（元信息）
- 用于传递额外的信息，如认证信息、内容类型等
- 格式：`键: 值`

#### 常见的Header示例

**1. Authorization Header（认证头）**
```
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```
- **作用**：传递Access Token
- **格式**：`Bearer <token>`
- **前端如何设置**：
  ```javascript
  // 在axios请求拦截器中
  config.headers.Authorization = `Bearer ${token}`
  ```
- **后端如何获取**：
  ```java
  String authHeader = request.getHeader("Authorization");
  // 结果: "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
  String token = authHeader.substring(7);  // 去掉"Bearer "
  ```

**2. User-Agent Header（用户代理头）**
```
User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36
```
- **作用**：告诉服务器客户端（浏览器）的信息
- **包含信息**：
    - 浏览器类型（Chrome、Firefox、Safari等）
    - 操作系统（Windows、Mac、Linux等）
    - 设备类型（PC、手机、平板等）
- **后端如何获取**：
  ```java
  String userAgent = request.getHeader("User-Agent");
  // 结果: "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36..."
  ```
- **在你的项目中的用途**：
  ```java
  // AuthController.resolveDevice()
  String userAgent = request.getHeader("User-Agent");
  // 用于生成设备指纹，区分不同设备
  ```

**3. Content-Type Header（内容类型头）**
```
Content-Type: application/json
```
- **作用**：告诉服务器请求体的格式
- **常见值**：
    - `application/json` - JSON格式
    - `application/x-www-form-urlencoded` - 表单格式
    - `multipart/form-data` - 文件上传

**4. Cookie Header（Cookie头）**
```
Cookie: rt=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...; sessionId=abc123
```
- **作用**：浏览器自动发送的Cookie信息
- **注意**：这是浏览器自动添加的，前端代码不需要手动设置
- **后端如何获取**：
  ```java
  // 从Cookie中提取RT
  Cookie[] cookies = request.getCookies();
  for (Cookie c : cookies) {
      if ("rt".equals(c.getName())) {
          String rt = c.getValue();
      }
  }
  ```

### 2.3 Cookie详解

**Cookie是什么？**
- Cookie是服务器发送给浏览器的小段数据
- 浏览器会自动保存Cookie
- 浏览器在后续请求中会自动发送Cookie

#### Cookie的完整流程

**1. 服务器设置Cookie（登录时）**

```java
// AuthController.setRefreshCookie()
ResponseCookie cookie = ResponseCookie.from("rt", rt)  // 名称: rt, 值: RT字符串
    .httpOnly(true)    // 只能通过HTTP访问，JS无法读取（防XSS）
    .secure(true)      // 只能通过HTTPS传输（生产环境）
    .sameSite("None")  // 跨域策略
    .path("/")         // Cookie生效的路径
    .maxAge(2592000)   // 有效期30天
    .build();

response.addHeader("Set-Cookie", cookie.toString());
```

**HTTP响应：**
```
HTTP/1.1 200 OK
Set-Cookie: rt=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...; HttpOnly; Secure; SameSite=None; Path=/; Max-Age=2592000
Content-Type: application/json

{
  "code": 200,
  "data": {
    "token": "AT字符串",
    ...
  }
}
```

**2. 浏览器保存Cookie**

```
浏览器收到响应后，自动保存：
Cookie存储：
  名称: rt
  值: eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
  属性: HttpOnly, Secure, SameSite=None, Path=/, Max-Age=2592000
```

**3. 浏览器自动发送Cookie（后续请求）**

```
用户访问 /api/auth/refresh
浏览器自动在请求头中添加：
Cookie: rt=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```

**后端接收：**
```java
// AuthController.extractRefreshToken()
Cookie[] cookies = request.getCookies();
for (Cookie c : cookies) {
    if ("rt".equals(c.getName())) {
        return c.getValue();  // 获取RT
    }
}
```

#### Cookie vs Header的区别

| 特性 | Cookie | Header |
|------|--------|--------|
| **设置方式** | 服务器通过`Set-Cookie`响应头设置 | 前端代码手动设置 |
| **发送方式** | 浏览器自动发送 | 前端代码手动添加 |
| **存储位置** | 浏览器本地存储 | 不存储，每次请求都要设置 |
| **安全性** | 可以设置HttpOnly（JS无法访问） | JS可以完全控制 |
| **用途** | 适合存储敏感信息（如RT） | 适合存储临时信息（如AT） |

**在你的项目中：**
- **AT放在Header**：前端手动设置，每次请求都要添加
- **RT放在Cookie**：服务器设置，浏览器自动发送，前端无需关心

### 2.4 IP地址详解

**IP地址是什么？**
- IP地址是网络设备的唯一标识
- 就像门牌号，用于在网络中找到设备

#### IP地址的获取

**1. 直接获取（局域网）**
```
客户端IP: 192.168.1.100
服务器直接获取: request.getRemoteAddr()
结果: "192.168.1.100"
```

**2. 通过代理/负载均衡（生产环境）**

```
真实客户端IP: 123.45.67.89
    ↓
代理服务器/负载均衡器: 10.0.0.1
    ↓
应用服务器: 10.0.0.2
```

**问题**：应用服务器直接获取IP，得到的是代理服务器的IP（10.0.0.1），不是真实客户端IP。

**解决方案**：代理服务器会在Header中传递真实IP

```
X-Forwarded-For: 123.45.67.89, 10.0.0.1
X-Real-IP: 123.45.67.89
```

**后端获取真实IP：**
```java
// AuthController.getClientIpAddress()
String ip = request.getHeader("X-Forwarded-For");  // 优先
if (ip == null || ip.isEmpty()) {
    ip = request.getHeader("X-Real-IP");  // 备选
}
if (ip == null || ip.isEmpty()) {
    ip = request.getRemoteAddr();  // 最后备选
}
```

#### IP地址的用途（在你的项目中）

```java
// 生成设备指纹
String deviceFingerprint = userAgent + "|" + ip;
// 例如: "Mozilla/5.0...|192.168.1.100"
// 哈希后: "-1234567890"
```

**为什么需要IP？**
- 同一用户在不同网络环境登录，IP不同
- 结合User-Agent，可以更准确地区分设备

### 2.5 设备指纹生成详解

**设备指纹 = User-Agent + IP地址的哈希值**

```java
// AuthController.resolveDevice()
private String resolveDevice(HttpServletRequest request) {
    // 1. 获取User-Agent
    String userAgent = request.getHeader("User-Agent");
    // 结果: "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36..."
    
    // 2. 获取IP地址
    String ip = getClientIpAddress(request);
    // 结果: "192.168.1.100"
    
    // 3. 组合并哈希
    String deviceFingerprint = userAgent + "|" + ip;
    // 结果: "Mozilla/5.0...|192.168.1.100"
    
    // 4. 计算哈希值（得到固定长度的字符串）
    return String.valueOf(deviceFingerprint.hashCode());
    // 结果: "-1234567890"
}
```

**为什么用哈希？**
- User-Agent字符串很长，直接存储占用空间大
- 哈希后得到固定长度的数字，便于存储和比较

**设备指纹的作用：**
```
用户A在电脑1登录:
  User-Agent: "Chrome on Windows"
  IP: "192.168.1.100"
  设备指纹: "-1234567890"
  Redis: auth:rt:1:-1234567890 = "RT1"

用户A在手机登录:
  User-Agent: "Chrome Mobile on Android"
  IP: "192.168.1.101"
  设备指纹: "-9876543210"
  Redis: auth:rt:1:-9876543210 = "RT2"

两个设备可以同时在线，互不影响！
```

### 2.6 实际传输示例

#### 示例1：登录请求

**前端发送：**
```http
POST /api/auth/login HTTP/1.1
Host: localhost:8080
Content-Type: application/json
User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36

{
  "username": "admin",
  "password": "123456"
}
```

**后端处理：**
```java
// 1. 获取User-Agent
String userAgent = request.getHeader("User-Agent");
// "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"

// 2. 获取IP
String ip = getClientIpAddress(request);
// "192.168.1.100"

// 3. 生成设备指纹
String device = resolveDevice(request);
// "-1234567890"

// 4. 生成Token
String at = jwtUtil.generateAccessToken(user, device);
String rt = jwtUtil.generateRefreshToken(user, device);

// 5. 存储RT到Redis
authTokenService.saveRefreshToken(1L, "-1234567890", rt, ...);
// Redis: auth:rt:1:-1234567890 = "RT字符串"
```

**后端响应：**
```http
HTTP/1.1 200 OK
Set-Cookie: rt=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...; HttpOnly; Secure; SameSite=None; Path=/; Max-Age=2592000
Content-Type: application/json

{
  "code": 200,
  "message": "登录成功",
  "data": {
    "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",  // AT
    "expiresIn": 1800000,
    "username": "admin",
    ...
  }
}
```

**前端收到：**
```javascript
// 1. 响应体中的AT
const { token } = response.data;
localStorage.setItem('token', token);

// 2. Cookie中的RT（浏览器自动保存，前端无需处理）
// Cookie: rt=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```

#### 示例2：API请求（使用AT）

**前端发送：**
```http
GET /api/employees HTTP/1.1
Host: localhost:8080
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...  // AT
Cookie: rt=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...  // RT（自动发送，但这里不需要）
```

**后端处理：**
```java
// JwtAuthFilter.doFilterInternal()
// 1. 从Header提取AT
String authHeader = request.getHeader("Authorization");
// "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
String token = authHeader.substring(7);

// 2. 解析AT
Claims claims = jwtUtil.getClaimsFromToken(token);
String jti = claims.get("jti", String.class);

// 3. 检查黑名单
if (authTokenService.isBlacklisted(jti)) {
    // 拒绝请求
}

// 4. 设置认证信息
SecurityContextHolder.getContext().setAuthentication(authentication);
```

#### 示例3：刷新Token请求

**前端发送：**
```http
POST /api/auth/refresh HTTP/1.1
Host: localhost:8080
Cookie: rt=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...  // RT（浏览器自动发送）
```

**注意：前端不需要手动传RT，浏览器会自动从Cookie中发送！**

**后端处理：**
```java
// AuthController.refresh()
// 1. 从Cookie提取RT
String rt = extractRefreshToken(req);
// 从Cookie: rt=... 中提取

// 2. 解析RT
Claims claims = jwtUtil.parseToken(rt);
Long userId = claims.get("userId", Long.class);
String device = claims.get("device", String.class);
// device: "-1234567890"

// 3. 从Redis验证RT
String storedRt = authTokenService.getRefreshToken(userId, device);
// Redis查询: auth:rt:1:-1234567890
// 结果: "RT字符串"

// 4. 对比验证
if (!storedRt.equals(rt)) {
    throw new PermissionDeniedException("刷新令牌已失效");
}

// 5. 生成新Token（Token旋转）
String newAt = jwtUtil.generateAccessToken(user, device);
String newRt = jwtUtil.generateRefreshToken(user, device);

// 6. 删除旧RT，存储新RT
authTokenService.deleteRefreshToken(userId, device);
authTokenService.saveRefreshToken(userId, device, newRt, ...);
// Redis: auth:rt:1:-1234567890 = "新RT字符串"

// 7. 更新Cookie
setRefreshCookie(resp, newRt, ...);
```

**后端响应：**
```http
HTTP/1.1 200 OK
Set-Cookie: rt=新的RT字符串; HttpOnly; Secure; SameSite=None; Path=/; Max-Age=2592000
Content-Type: application/json

{
  "code": 200,
  "message": "令牌刷新成功",
  "data": {
    "token": "新的AT字符串",
    "expiresIn": 1800000,
    ...
  }
}
```

---

## 三、完整流程图（结合传输细节）

### 3.1 登录流程（完整）

```
┌─────────┐
│  前端    │
└────┬────┘
     │ POST /api/auth/login
     │ Headers:
     │   Content-Type: application/json
     │   User-Agent: Mozilla/5.0...
     │ Body: {username, password}
     ↓
┌─────────────────────────────────┐
│  后端 AuthController.login()    │
│  1. 获取User-Agent: "Mozilla..." │
│  2. 获取IP: "192.168.1.100"     │
│  3. 生成设备指纹: "-1234567890" │
│  4. 生成AT + RT                 │
│  5. Redis存储:                  │
│     auth:rt:1:-1234567890 = RT  │
└────┬────────────────────────────┘
     │ Response:
     │   Set-Cookie: rt=RT字符串
     │   Body: {token: AT字符串}
     ↓
┌─────────┐
│  前端    │
│  1. 保存AT到localStorage        │
│  2. 浏览器自动保存Cookie (RT)   │
└─────────┘
```

### 3.2 API请求流程（完整）

```
┌─────────┐
│  前端    │
│  从localStorage获取AT           │
└────┬────┘
     │ GET /api/employees
     │ Headers:
     │   Authorization: Bearer AT字符串
     │   (Cookie自动发送: rt=RT字符串)
     ↓
┌─────────────────────────────────┐
│  JwtAuthFilter                   │
│  1. 提取AT: "Bearer AT字符串"    │
│  2. 解析AT，获取jti              │
│  3. 检查黑名单:                  │
│     Redis查询: auth:bl:jti       │
│  4. 设置认证信息                 │
└────┬────────────────────────────┘
     │
     ↓
┌─────────────────────────────────┐
│  EmployeeController              │
│  处理请求，返回数据              │
└─────────────────────────────────┘
```

### 3.3 无感刷新流程（完整）

```
┌─────────┐
│  前端    │
│  发送API请求，AT已过期           │
└────┬────┘
     │ GET /api/employees
     │ Headers:
     │   Authorization: Bearer 过期AT
     ↓
┌─────────────────────────────────┐
│  JwtAuthFilter                   │
│  解析AT失败（已过期）            │
│  返回401                         │
└────┬────────────────────────────┘
     │ 401 Unauthorized
     ↓
┌─────────────────────────────────┐
│  前端响应拦截器                  │
│  检测到401，自动调用刷新接口     │
└────┬────────────────────────────┘
     │ POST /api/auth/refresh
     │ (Cookie自动发送: rt=RT字符串)
     ↓
┌─────────────────────────────────┐
│  AuthController.refresh()        │
│  1. 从Cookie提取RT               │
│  2. 从Redis验证RT:               │
│     auth:rt:1:-1234567890 = RT? │
│  3. 生成新AT + 新RT              │
│  4. 删除旧RT，存储新RT           │
│  5. 更新Cookie                   │
└────┬────────────────────────────┘
     │ Response:
     │   Set-Cookie: rt=新RT字符串
     │   Body: {token: 新AT字符串}
     ↓
┌─────────────────────────────────┐
│  前端响应拦截器                  │
│  1. 更新localStorage中的AT       │
│  2. 重试原请求                   │
└────┬────────────────────────────┘
     │ GET /api/employees
     │ Headers:
     │   Authorization: Bearer 新AT
     ↓
┌─────────────────────────────────┐
│  JwtAuthFilter                   │
│  验证新AT成功 ✅                 │
└────┬────────────────────────────┘
     │
     ↓
┌─────────────────────────────────┐
│  EmployeeController              │
│  返回数据 ✅                     │
└─────────────────────────────────┘
```

---

## 四、总结

### 4.1 无感刷新机制

✅ **流程**：
1. AT过期 → 401错误
2. 前端自动调用刷新接口
3. 后端验证RT，生成新AT+新RT（Token旋转）
4. 前端更新AT，重试原请求
5. 用户无感知

✅ **Token旋转的作用**：
- 每次刷新生成新RT，删除旧RT
- 防止RT泄露后被长期使用
- 提高安全性

### 4.2 HTTP传输

✅ **Header（请求头）**：
- 前端手动设置（如Authorization）
- 每次请求都要添加
- 适合存储AT

✅ **Cookie**：
- 服务器设置，浏览器自动保存和发送
- 前端无需关心
- 适合存储RT（HttpOnly，更安全）

✅ **User-Agent**：
- 浏览器信息（类型、操作系统等）
- 用于生成设备指纹

✅ **IP地址**：
- 客户端网络地址
- 结合User-Agent生成设备指纹
- 支持多设备登录

### 4.3 关键点

1. **AT放在Header** - 前端控制，每次请求添加
2. **RT放在Cookie** - 浏览器自动管理，更安全
3. **设备指纹** - User-Agent + IP的哈希值
4. **Token旋转** - 每次刷新生成新RT，删除旧RT
5. **无感刷新** - 前端自动处理，用户无感知

---

**文档版本：** v1.0  
**最后更新：** 2024年
