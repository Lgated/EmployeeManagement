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