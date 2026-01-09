## Redis 进阶实践文档：分页缓存 + 登录令牌桶限流

> 目标：  
> 1. 给“员工列表 / 用户列表”增加 **分页缓存 + 写后删除**，提升读取性能。  
> 2. 给“登录接口”实现基于 Redis 的 **令牌桶限流**，防止暴力登录和恶意请求。  
>  
> 文档风格是“边学边做”：每一节都有 **思路 → 步骤 → 示例代码**。你可以按顺序在项目里照着实现。

---

## 一、员工/用户分页缓存 + 写后删除

### 1.1 设计思路

- **缓存目标**
  - 员工分页接口：`GET /api/employ?name=&department=&page=&size=`
  - 用户分页接口：`GET /api/users?username=&role=&page=&size=`
  - 典型特征：读多写少，非常适合缓存。

- **缓存模式：Cache Aside（旁路缓存）**
  - **读：**
    - 先查 Redis → 有就直接返回；
    - 没有就查 DB → 把结果写入 Redis，再返回。
  - **写（新增/修改/删除）：**
    - 先更新 DB；
    - 成功后 **删除相关缓存 key**（而不是立即重建）。

- **Redis Key 设计**

  - 员工分页：
    - `employee:list:{name}:{department}:{page}:{size}`
  - 用户分页：
    - `user:list:{username}:{role}:{page}:{size}`

  为了方便统一删除，可以额外维护 **索引集合**：

  - `employee:list:keys` 里存所有员工列表 key  
  - `user:list:keys` 里存所有用户列表 key  

  这样在“写后删除”时，不需要知道所有具体条件，只要按集合批量删除即可。

---

### 1.2 实现步骤总览

1. 创建一个通用的 `CacheKeyUtil`，负责生成分页 key。  
2. 在 `EmployeeService` / `UserService` 的分页查询方法中加入“读缓存逻辑”。  
3. 在员工 / 用户的新增、更新、删除方法中加入“写后删除缓存逻辑”。  
4. （可选）在 Redis 中增加“key 索引集合”，方便批量删除。

下面用“员工分页”为例，用户分页同理。

---

### 1.3 步骤一：编写缓存 Key 工具

新建文件：`src/main/java/com/example/empmgmt/common/util/CacheKeyUtil.java`

```java
package com.example.empmgmt.common.util;

/**
 * Redis 缓存 Key 生成工具
 * 统一管理 Key 格式，减少硬编码字符串
 */
public class CacheKeyUtil {

    /**
     * 员工分页列表 key
     */
    public static String buildEmployeeListKey(String name, String department, int page, int size) {
        String safeName = name == null ? "" : name.trim();
        String safeDept = department == null ? "" : department.trim();
        return String.format("employee:list:%s:%s:%d:%d", safeName, safeDept, page, size);
    }

    /**
     * 用户分页列表 key
     */
    public static String buildUserListKey(String username, String role, int page, int size) {
        String safeUsername = username == null ? "" : username.trim();
        String safeRole = role == null ? "" : role.trim();
        return String.format("user:list:%s:%s:%d:%d", safeUsername, safeRole, page, size);
    }

    /**
     * 员工列表 key 集合，用于记录所有分页 key，方便统一删除
     */
    public static String employeeListKeySet() {
        return "employee:list:keys";
    }

    /**
     * 用户列表 key 集合
     */
    public static String userListKeySet() {
        return "user:list:keys";
    }
}
```

---

### 1.4 步骤二：在员工分页查询中加入缓存逻辑

假设你的员工分页 Service 方法类似：

```java
// 现有方法示意（简化版）
public PageResponse<EmployeeResponse> pageQuery(String name, String department, int page, int size) {
    // 现有逻辑：直接查数据库
    // ...
}
```

现在改成带缓存版本（核心是 **读缓存** + **回填缓存**）：

```java
import org.springframework.data.redis.core.RedisTemplate;
import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class EmployeeServiceImpl implements EmployeeService {

    private final EmployeeRepository employeeRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    public EmployeeServiceImpl(EmployeeRepository employeeRepository,
                               RedisTemplate<String, Object> redisTemplate) {
        this.employeeRepository = employeeRepository;
        this.redisTemplate = redisTemplate;
    }

    @Override
    public PageResponse<EmployeeResponse> pageQuery(String name, String department, int page, int size) {
        // 1. 生成缓存 key
        String cacheKey = CacheKeyUtil.buildEmployeeListKey(name, department, page, size);

        // 2. 先查缓存
        @SuppressWarnings("unchecked")
        PageResponse<EmployeeResponse> cached =
                (PageResponse<EmployeeResponse>) redisTemplate.opsForValue().get(cacheKey);

        if (cached != null) {
            return cached; // 命中缓存，直接返回
        }

        // 3. 未命中缓存，查询数据库（这里用你的原有分页逻辑）
        PageResponse<EmployeeResponse> pageResult = doQueryFromDb(name, department, page, size);

        // 4. 写入缓存（加一点随机 TTL，避免雪崩）
        long baseTtlSeconds = 5 * 60; // 5分钟
        long randomExtra = ThreadLocalRandom.current().nextLong(0, 60); // 0~60秒
        redisTemplate.opsForValue().set(
                cacheKey,
                pageResult,
                Duration.ofSeconds(baseTtlSeconds + randomExtra)
        );

        // 5. 把 key 记到“索引集合”中，方便写后删除
        redisTemplate.opsForSet().add(CacheKeyUtil.employeeListKeySet(), cacheKey);

        return pageResult;
    }

    /**
     * 实际的数据库查询逻辑（可复用你原来 Service 中的实现）
     */
    private PageResponse<EmployeeResponse> doQueryFromDb(String name, String department, int page, int size) {
        // 使用 repository 分页查询，然后转换为 PageResponse<EmployeeResponse>
        // ...
    }
}
```

> 注意：  
> - RedisTemplate 默认用 JDK 序列化，你可以根据实际项目已经配置的序列化方式来调整类型。  
> - `PageResponse<EmployeeResponse>` 必须是可序列化的（一般没问题）。

---

### 1.5 步骤三：在“员工写操作”中删除缓存

员工相关的写操作包括：

- 新增员工
- 更新员工
- 删除员工

在这些方法执行 **成功更新 DB 之后**，统一删除与员工列表相关的缓存。

示例（以 `create` / `update` / `delete` 为例）：

```java
@Override
public EmployeeResponse create(EmployeeCreateRequest request) {
    EmployeeResponse res = doCreateInDb(request);·······

    // 写后删除缓存
    clearEmployeeListCache();

    return res;
}

@Override
public EmployeeResponse update(Long id, EmployeeUpdateRequest request) {
    EmployeeResponse res = doUpdateInDb(id, request);

    // 写后删除缓存
    clearEmployeeListCache();

    return res;
}

@Override
public void delete(Long id) {
    doDeleteInDb(id);

    // 写后删除缓存
    clearEmployeeListCache();
}

/**
 * 删除所有员工列表相关缓存
 */
private void clearEmployeeListCache() {
    String keySet = CacheKeyUtil.employeeListKeySet();
    // 1. 取出所有列表 key
    Set<Object> keys = redisTemplate.opsForSet().members(keySet);
    if (keys != null && !keys.isEmpty()) {
        // 2. 删除这些 key
        redisTemplate.delete(
            keys.stream().map(Object::toString).toList()
        );
    }
    // 3. 清空 key 集合本身
    redisTemplate.delete(keySet);
}
```

> 思路解释：  
> - 我们不知道哪些具体条件的列表被查询过，因此无法只删某几个；  
> - 用“索引集合”记录所有生成过的分页 key，写入时添加，写后删除时一起删；  
> - 写操作频率通常远低于读，全部删除是可以接受的。

---

### 1.6 用户分页缓存：同样的模式

你可以在 `UserService` 中照抄一份逻辑，只是改成 `buildUserListKey` 和 `user:list:keys`。

---

## 二、登录接口的令牌桶限流（Redis + Lua）

### 2.1 设计目标

- 对 **登录接口** 做限流，防止暴力破解和恶意刷新：
  - 未登录前按 **IP 地址** 限流，例如每分钟最多 30 次尝试。
- 使用 **令牌桶算法** 实现：  
  - 桶容量 `capacity`：最多存多少个令牌。  
  - 令牌填充速率 `rate`：每秒补充多少个令牌。  
  - 每次登录请求从桶里“取一个令牌”，没有令牌则拒绝。

---

### 2.2 整体实现结构

1. 写一个 **Lua 脚本**，在 Redis 中原子执行“补充令牌 + 扣减令牌”。  
2. 在后端写 `RateLimitService`，加载 Lua 脚本，提供 `tryAcquire(...)` 方法。  
3. 在 `AuthController.login` 里调用 `rateLimitService.tryAcquire`，不通过则抛出异常。

---

### 2.3 步骤一：编写 Lua 脚本

新建文件：`src/main/resources/lua/token_bucket.lua`

```lua
-- 令牌桶限流 Lua 脚本
-- KEYS[1] = 令牌数 key，例如 rate:login:ip:127.0.0.1:tokens
-- KEYS[2] = 上次刷新时间 key，例如 rate:login:ip:127.0.0.1:ts
-- ARGV[1] = 容量 capacity
-- ARGV[2] = 每秒填充速率 rate
-- ARGV[3] = 当前时间戳（毫秒）

local tokens_key = KEYS[1]
local ts_key = KEYS[2]

local capacity = tonumber(ARGV[1])
local rate = tonumber(ARGV[2])
local now = tonumber(ARGV[3])

-- 当前 token 数与上次刷新时间
local tokens = tonumber(redis.call("GET", tokens_key))
if tokens == nil then
  tokens = capacity  -- 第一次访问，视为桶已满
end

local last_ts = tonumber(redis.call("GET", ts_key))
if last_ts == nil then
  last_ts = now      -- 第一次访问，初始化时间
end

-- 计算经过的时间（秒），补充令牌
local delta = math.max(0, now - last_ts)
local refill = (delta / 1000.0) * rate
tokens = math.min(capacity, tokens + refill)

-- 判断是否还有令牌
local allowed = 0
if tokens >= 1 then
  tokens = tokens - 1
  allowed = 1
end

-- 写回 Redis
redis.call("SET", tokens_key, tokens)
redis.call("SET", ts_key, now)

return allowed
```

> 说明：  
> - 这里使用的是“浮点 token”，允许 `rate` 是小数，例如 0.5/s。  
> - 令牌数不会超过 `capacity`，防止无限增长。  
> - 返回值：`1` 表示允许请求；`0` 表示拒绝（限流）。

---

### 2.4 步骤二：封装 RateLimitService

新建文件：`src/main/java/com/example/empmgmt/common/util/RateLimitService.java`

```java
package com.example.empmgmt.common.util;

import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.Arrays;

/**
 * 基于 Redis + Lua 的令牌桶限流服务
 */
@Service
public class RateLimitService {

    private final StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<Long> tokenBucketScript;

    public RateLimitService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.tokenBucketScript = new DefaultRedisScript<>();
        // 加载 Lua 脚本
        this.tokenBucketScript.setLocation(new ClassPathResource("lua/token_bucket.lua"));
        this.tokenBucketScript.setResultType(Long.class);
    }

    /**
     * 尝试获取一个令牌
     *
     * @param keyPrefix 限流 key 前缀，例如 "rate:login:ip:"
     * @param id        标识，例如 IP 或 userId
     * @param capacity  桶容量
     * @param rate      每秒填充速率（可以是小数）
     * @return true = 允许；false = 限流
     */
    public boolean tryAcquire(String keyPrefix, String id, int capacity, double rate) {
        String tokensKey = keyPrefix + id + ":tokens";
        String tsKey = keyPrefix + id + ":ts";

        long now = System.currentTimeMillis();

        Long result = redisTemplate.execute(
                tokenBucketScript,
                Arrays.asList(tokensKey, tsKey),
                String.valueOf(capacity),
                String.valueOf(rate),
                String.valueOf(now)
        );
        return result != null && result == 1L;
    }
}
```

> 说明：  
> - 使用 `StringRedisTemplate` 即可，因为脚本只读写 string。  
> - 脚本路径 `lua/token_bucket.lua` 对应的是 `resources/lua/token_bucket.lua`。

---

### 2.5 步骤三：在登录接口中接入限流

在 `AuthController` 中的 `/auth/login` 方法开头增加限流逻辑。

假设你已有类似方法：

```java
@PostMapping("/login")
public Result<LoginResponse> login(@RequestBody LoginRequest req, HttpServletRequest request) {
    // 原有登录逻辑...
}
```

现在改造为：

```java

import com.example.empmgmt.common.Exception.BusinessException;
// ...

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final RateLimitService rateLimitService;
    // 其他依赖...

    public AuthController(RateLimitService rateLimitService, /* 其他依赖 */) {
        this.rateLimitService = rateLimitService;
        // ...
    }

    @PostMapping("/login")
    public Result<LoginResponse> login(@RequestBody LoginRequest req, HttpServletRequest request) {
        // 1. 未登录前按 IP 限流
        String ip = getClientIpAddress(request);

        // 例如：容量 30，每秒 0.5 个令牌 => 每分钟约补充 30 个
        boolean allowed = rateLimitService.tryAcquire(
                "rate:login:ip:",
                ip,
                30,
                0.5
        );

        if (!allowed) {
            // 你可以抛自定义异常，交给全局异常处理器返回统一 JSON
            throw new BusinessException("请求过于频繁，请稍后再试");
        }

        // 2. 通过限流校验后，执行原有登录逻辑
        //    - 校验用户名密码
        //    - 生成 AT/RT
        //    - 写 Redis
        //    - 返回响应
        // ...
    }

    /**
     * 复用你之前写的获取客户端 IP 方法
     */
    private String getClientIpAddress(HttpServletRequest request) {
        // 你项目中已经有类似方法，可以直接调用
        // 这里仅示意
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isBlank() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isBlank() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip == null ? "0.0.0.0" : ip;
    }
}
```

> 调参建议：  
> - 开发环境可以把 `capacity` 调小一点，方便测试；  
> - 生产中可以按风险程度调整，比如 login 更严格，普通接口稍微宽松；  
> - 如果后续要对**已登录用户**限流，也可以改用 `userId` 做 key。

---

## 三、实践顺序建议

1. **先实现员工分页缓存**：  
   - 写好 `CacheKeyUtil`；  
   - 在 `EmployeeService.pageQuery` 中加入缓存读写；  
   - 在 `create/update/delete` 中调用 `clearEmployeeListCache`；  
   - 测试：  
     - 第一次请求时观察 DB 查询，后续同样条件下看是否直接命中 Redis。  
2. **再复制一份逻辑到用户分页**（`UserService`）。  
3. **实现 Lua 脚本 + RateLimitService**：  
   - 写 `token_bucket.lua`；  
   - 写 `RateLimitService`；  
   - 在 `AuthController.login` 中接入 `tryAcquire`。  
4. **压测 / 手动测试登录限流**：  
   - 快速多次刷新登录请求，观察是否返回“请求过于频繁”；  
   - 调整 `capacity` / `rate` 到你满意的体验。

