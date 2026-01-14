# 📊 企业员工管理系统

> 🎓 **开箱即用的期末作业项目** | 基于 Spring Boot 3 + React 的完整企业级员工管理系统

本项目是一个功能完整的企业员工管理系统，采用前后端分离架构，包含用户管理、员工管理、权限控制、数据导出、操作日志等核心功能。**适合作为期末作业、课程设计或学习项目使用。**

## ✨ 核心特性

- ✅ **RBAC权限系统**：基于角色的访问控制（SUPER_ADMIN、MANAGER、EMPLOYEE）
- ✅ **JWT双Token认证**：Access Token（30分钟）+ Refresh Token（30天），支持无感刷新
- ✅ **员工管理**：完整的CRUD功能，支持软删除、分页查询、多条件筛选
- ✅ **用户管理**：用户创建、角色分配、部门管理、启用/禁用
- ✅ **异步导出**：基于 RabbitMQ + Redis 防重锁的异步Excel导出
- ✅ **操作日志**：AOP自动记录所有接口操作，支持分页查询和筛选
- ✅ **Redis缓存**：分页数据缓存，提升查询性能
- ✅ **令牌桶限流**：基于 Redis + Lua 的登录接口限流，防止暴力破解
- ✅ **文件上传**：员工头像上传，支持图片预览
- ✅ **数据统计**：部门统计、角色统计等数据分析功能

## 🎯 功能一览

### 后端功能（Spring Boot + Spring Security + JPA）

#### 认证授权
- 用户注册、登录、登出
- JWT双Token机制（AT + RT）
- Redis存储Refresh Token和黑名单
- 自定义权限注解（`@RequiresPermission`、`@RequiresRole`）
- 部门级权限控制

#### 员工管理
- 员工信息CRUD操作
- 软删除机制（逻辑删除）
- 分页查询（支持Redis缓存）
- 多条件筛选（部门、职位、姓名等）
- 部门统计、职位统计

#### 用户管理
- 用户信息CRUD操作
- 角色分配（SUPER_ADMIN、MANAGER、EMPLOYEE）
- 部门分配
- 用户启用/禁用
- 关联员工信息

#### 数据导出
- 同步导出：直接下载Excel文件
- 异步导出：基于RabbitMQ的异步任务
- 导出任务管理：任务状态跟踪、文件下载
- Redis防重复消费锁

#### 操作日志
- AOP自动记录接口调用
- 记录操作类型、模块、参数、结果
- 记录IP地址、执行时间
- 支持按模块、操作类型筛选

#### 性能优化
- Redis分页缓存
- 令牌桶限流（登录接口）
- 异步消息队列（导出任务）

### 前端功能（React + TypeScript + Ant Design）

#### 用户界面
- 登录/注册页面
- 员工列表页面（表格展示、分页、筛选）
- 员工表单页面（新增/编辑）
- 用户管理页面（角色分配、部门管理）
- 操作日志页面（日志查询、筛选）
- 数据统计页面（图表展示）

#### 交互体验
- 响应式布局
- 表单验证
- 消息提示（成功/错误）
- 加载状态提示
- 图片上传预览
- 文件下载

## 📚 技术栈

### 后端
- **Spring Boot 3.3.1** - 主框架
- **Spring Security** - 安全认证框架
- **Spring Data JPA** - ORM框架
- **PostgreSQL** - 关系型数据库
- **Redis** - 缓存、分布式锁、限流
- **RabbitMQ** - 异步消息队列
- **JWT (jjwt 0.11.5)** - Token认证
- **EasyExcel 3.3.2** - Excel导入导出
- **Lombok** - 代码简化

### 前端
- **React 18** - UI框架
- **TypeScript 5** - 类型安全
- **Vite 5** - 构建工具
- **Ant Design 5** - UI组件库
- **Zustand** - 状态管理
- **Axios** - HTTP客户端
- **React Router** - 路由管理

## 🛠️ 运行环境

### 必需环境
- **JDK 17+**
- **Node.js 18+**
- **Maven 3.6+**

### 中间件
- **PostgreSQL 12+**（主数据库）
- **Redis 6+**（缓存、分布式锁）
- **RabbitMQ 3.8+**（消息队列）

## 🐳 快速开始

### 1. 克隆项目

```bash
git clone https://github.com/你的账号/EmployManagement.git
cd EmployManagement
```

### 2. 数据库准备

#### PostgreSQL

```bash
# Docker 方式（推荐）
docker run -d \
  --name postgres-empmgmt \
  -e POSTGRES_PASSWORD=123456 \
  -e POSTGRES_DB=empmgmt \
  -p 5432:5432 \
  postgres:15

# 或使用本地PostgreSQL，创建数据库
createdb empmgmt
```

#### Redis

```bash
# Docker 方式（推荐）
docker run -d \
  --name redis-empmgmt \
  -p 6379:6379 \
  redis:7-alpine
```

#### RabbitMQ

```bash
# Docker 方式（推荐）
docker run -d \
  --name rabbitmq-empmgmt \
  -p 5672:5672 \
  -p 15672:15672 \
  -e RABBITMQ_DEFAULT_USER=guest \
  -e RABBITMQ_DEFAULT_PASS=guest \
  rabbitmq:3-management
```

访问 RabbitMQ 管理界面：http://localhost:15672（guest/guest）

### 3. 后端配置

修改 `src/main/resources/application.yml`：

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/empmgmt
    username: postgres
    password: 123456  # 修改为你的密码
    driver-class-name: org.postgresql.Driver
  
  redis:
    host: 127.0.0.1
    port: 6379
  
  rabbitmq:
    host: localhost
    port: 5672
    username: guest
    password: guest

# JWT配置（生产环境请修改密钥）
jwt:
  secret: replace-with-256-bit-secret-key-xxxx
  access-ttl-ms: 1800000  # Access Token 30分钟
  refresh-ttl-ms: 2592000000  # Refresh Token 30天

# 文件上传路径
file:
  upload-path: D:/uploads/employee  # Windows路径，Linux请修改
```

### 4. 数据库初始化

项目使用 JPA 自动建表，首次启动会自动创建表结构。

如需初始化数据，可以执行 SQL 脚本（可选）：

```sql
-- 创建超级管理员（密码：admin123）
INSERT INTO "user" (username, password, email, role, enabled, created_at, updated_at)
VALUES ('admin', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iwy8pL5O', 'admin@example.com', 'SUPER_ADMIN', true, NOW(), NOW());
```

### 5. 启动后端

#### 使用 IDEA（推荐）

1. 使用 IntelliJ IDEA 打开项目根目录
2. 等待 Maven 依赖下载完成
3. 找到 `src/main/java/com/example/empmgmt/EmpMgmtApplication.java`
4. 右键 → Run 'EmpMgmtApplication'
5. 后端启动成功后，默认监听：http://localhost:8080

#### 使用命令行

```bash
# 编译项目
mvn clean compile

# 运行项目
mvn spring-boot:run
```

### 6. 启动前端

```bash
cd frontend

# 安装依赖
npm install

# 启动开发服务器
npm run dev
```

前端启动成功后，默认访问：http://localhost:5173

### 7. 访问系统

- **前端地址**：http://localhost:5173
- **后端API**：http://localhost:8080/api

**默认账号**：
- 用户名：`admin`
- 密码：`admin123`（如已初始化）

## 📖 使用说明

### 登录系统
1. 访问前端地址：http://localhost:5173
2. 输入用户名和密码登录
3. 首次使用可点击"注册"创建新账号

### 员工管理
1. 登录后进入"员工管理"页面
2. 点击"新增员工"按钮添加员工信息
3. 支持按部门、职位、姓名等条件筛选
4. 点击"编辑"或"删除"按钮管理员工
5. 点击"导出"按钮导出员工数据（支持同步/异步导出）

### 用户管理
1. 进入"用户管理"页面（需要管理员权限）
2. 可以创建新用户、分配角色和部门
3. 支持启用/禁用用户
4. 可以查看用户关联的员工信息

### 操作日志
1. 进入"操作日志"页面
2. 查看所有接口操作记录
3. 支持按模块、操作类型筛选
4. 可以查看详细的请求参数和执行结果

### 数据统计
1. 进入"数据统计"页面
2. 查看部门统计、角色统计等数据
3. 支持图表可视化展示

## 📂 项目结构

```
EmployManagement/
├── src/main/java/com/example/empmgmt/
│   ├── config/              # 配置类（Security、Redis、RabbitMQ、CORS）
│   ├── controller/          # REST控制器（Auth、Employee、User、Export、Log）
│   ├── domain/              # 实体类（User、Employee、ExportTask、OperationLog）
│   ├── repository/          # 数据访问层（JPA Repository）
│   ├── service/             # 业务逻辑接口
│   │   └── Impl/           # 业务逻辑实现
│   ├── mq/                 # 消息队列（Consumer、DTO）
│   ├── common/             # 通用类（注解、异常、工具类）
│   └── security/           # 安全相关（JWT Filter、Authentication）
├── src/main/resources/
│   ├── application.yml      # 应用配置
│   └── lua/                 # Lua脚本（令牌桶限流）
├── frontend/
│   ├── src/
│   │   ├── api/            # API封装（auth、employee、user、log）
│   │   ├── pages/          # 页面组件（Login、EmployeeList、UserManagement）
│   │   ├── components/     # 公共组件（Layout、ImageUpload）
│   │   ├── stores/         # 状态管理（authStore）
│   │   └── utils/          # 工具类（request）
│   └── package.json
└── docs/                    # 项目文档
```

## 🔑 核心知识点

### 认证授权
- **JWT双Token机制**：Access Token短期有效，Refresh Token长期有效
- **Redis存储**：RT存储在Redis，支持主动撤销
- **无感刷新**：前端自动刷新Token，用户无感知
- **RBAC权限模型**：基于角色的访问控制
- **自定义注解**：`@RequiresPermission`、`@RequiresRole`实现权限控制

### 异步处理
- **RabbitMQ消息队列**：异步导出任务，提升用户体验
- **Redis分布式锁**：防止重复消费，保证幂等性
- **死信队列**：处理失败消息，支持重试机制

### 性能优化
- **Redis缓存**：分页数据缓存，减少数据库查询
- **令牌桶限流**：Lua脚本实现原子性限流
- **连接池优化**：数据库和Redis连接池配置

### 前端实践
- **状态管理**：Zustand管理用户认证状态
- **路由守卫**：基于角色的路由权限控制
- **API封装**：统一的请求拦截和错误处理
- **组件化开发**：可复用的UI组件


## 🎓 适合场景

- ✅ **期末作业**：功能完整，代码规范，可直接使用
- ✅ **课程设计**：涵盖前后端、数据库、中间件等知识点
- ✅ **学习项目**：学习 Spring Boot、React、Redis、RabbitMQ 等
- ✅ **毕业设计**：可作为毕业设计的基础框架

## 🔮 后续扩展建议

- 📊 接入 xxl-job 实现定时报表生成
- 🔍 集成 Elasticsearch 实现全文搜索
- 📈 添加数据可视化大屏
- 📧 实现邮件通知功能
- 🔔 添加消息推送功能
- 📱 开发移动端应用
- 🌐 支持多租户功能
- 📦 集成对象存储（OSS/MinIO）

## ⚠️ 注意事项

1. **生产环境配置**：
   - 修改 JWT Secret 为安全的随机字符串
   - 修改数据库密码
   - 配置 HTTPS
   - 设置文件上传路径为安全的目录

2. **性能优化**：
   - 根据实际数据量调整 Redis 缓存策略
   - 优化数据库索引

3. **安全建议**：
   - 启用 HTTPS
   - 配置 CORS 白名单
   - 添加接口限流
   - 定期更新依赖版本

## 👥 贡献

欢迎提交 Issue 和 Pull Request！

## 📮 联系方式

如有问题或建议，欢迎通过 Issue 反馈。

## ⭐ Star

如果这个项目对你有帮助，欢迎 Star！⭐



