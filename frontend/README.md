# APJ员工管理系统 - 前端

## 项目简介

APJ员工管理系统前端，基于 React + TypeScript + Vite + Ant Design 构建的现代化员工管理系统。

## 技术栈

- **React 18** - UI框架
- **TypeScript** - 类型安全
- **Vite** - 构建工具
- **Ant Design 5** - UI组件库
- **React Router 6** - 路由管理
- **Zustand** - 状态管理
- **Axios** - HTTP请求
- **Day.js** - 日期处理

## 项目结构

```
frontend/
├── src/
│   ├── api/              # API接口定义
│   │   ├── auth.ts       # 认证相关API
│   │   └── employee.ts   # 员工管理API
│   ├── components/       # 公共组件
│   │   └── Layout.tsx    # 主布局组件
│   ├── pages/            # 页面组件
│   │   ├── Login.tsx     # 登录页
│   │   ├── Register.tsx  # 注册页
│   │   ├── EmployeeList.tsx    # 员工列表页
│   │   ├── EmployeeForm.tsx    # 员工表单页（新增/编辑）
│   │   └── Statistics.tsx      # 统计页
│   ├── stores/           # 状态管理
│   │   └── authStore.ts  # 认证状态管理
│   ├── types/            # 类型定义
│   │   └── index.ts      # TypeScript类型
│   ├── utils/            # 工具函数
│   │   └── request.ts    # Axios请求封装
│   ├── App.tsx           # 应用主组件
│   ├── main.tsx          # 入口文件
│   └── index.css         # 全局样式
├── index.html            # HTML模板
├── package.json          # 依赖配置
├── tsconfig.json         # TypeScript配置
└── vite.config.ts        # Vite配置
```

## 安装依赖

```bash
cd frontend
npm install
```

## 开发运行

```bash
npm run dev
```

应用将在 `http://localhost:3000` 启动。

## 构建生产版本

```bash
npm run build
```

构建产物在 `dist` 目录。

## 功能特性

### 1. 用户认证
- ✅ 用户登录
- ✅ 用户注册
- ✅ Token管理
- ✅ 路由守卫（未登录自动跳转）

### 2. 员工管理
- ✅ 员工列表展示
- ✅ 按姓名/部门搜索
- ✅ 新增员工
- ✅ 编辑员工信息
- ✅ 删除员工
- ✅ 分页显示

### 3. 数据统计
- ✅ 各部门员工数量统计
- ✅ 各部门平均薪资统计
- ✅ 总员工数统计
- ✅ 平均薪资统计

## API接口说明

### 认证接口

- `POST /api/auth/login` - 用户登录
- `POST /api/auth/register` - 用户注册

### 员工管理接口

- `GET /api/employ` - 获取员工列表（支持name、department参数）
- `GET /api/employ/{id}` - 获取员工详情
- `POST /api/employ` - 创建员工
- `PUT /api/employ/{id}` - 更新员工
- `DELETE /api/employ/{id}` - 删除员工
- `GET /api/employ/{id}/years` - 获取员工工龄

### 统计接口

- `GET /api/employ/stats/dept-count` - 部门人数统计
- `GET /api/employ/stats/dept-avg-salary` - 部门平均薪资统计

## 前后端联调说明

### 1. 后端配置

确保后端已配置CORS，允许前端访问：

```java
// SecurityConfig.java
.cors(cors -> cors.configurationSource(corsConfigurationSource()))
```

### 2. 代理配置

前端通过Vite代理转发请求到后端（`vite.config.ts`）：

```typescript
server: {
  proxy: {
    '/api': {
      target: 'http://localhost:8080',
      changeOrigin: true,
    }
  }
}
```

### 3. Token管理

- Token存储在 `localStorage` 中
- 请求时自动添加到 `Authorization` 头
- Token过期自动跳转登录页

## 开发注意事项

1. **类型安全**：所有API响应都有TypeScript类型定义
2. **错误处理**：统一的错误处理和提示
3. **加载状态**：所有异步操作都有loading状态
4. **表单验证**：使用Ant Design Form进行表单验证
5. **响应式设计**：支持不同屏幕尺寸

## 常见问题

### 1. 跨域问题

如果遇到CORS错误，检查：
- 后端是否配置了CORS
- Vite代理配置是否正确
- 后端服务是否正常运行

### 2. Token失效

Token失效会自动跳转到登录页，重新登录即可。

### 3. 接口调用失败

检查：
- 后端服务是否启动（端口8080）
- 网络连接是否正常
- 浏览器控制台错误信息

## 代码注释说明

所有代码文件都包含详细的中文注释，说明：
- 文件用途
- 函数功能
- 参数说明
- 业务逻辑

## 后续优化建议

1. 添加员工详情页面
2. 添加批量操作功能
3. 添加数据导出功能
4. 优化移动端体验
5. 添加更多统计图表






















