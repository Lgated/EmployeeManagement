## 一、前置背景 & 目标

这段时间你已经把后端的 RBAC 权限体系搭得比较完整了：

- 后端有 `RoleEnum`：`SUPER_ADMIN` / `MANAGER` / `EMPLOYEE`
- 基于 `@RequiresPermission`、`@RequiresRole` + `PermissionAspect` 做方法级权限控制
- `User` 表里有 `role / department / employeeId`
- 登录时后端返回了带用户名、角色等信息的 `AuthResponse`
- 前端 `authStore` 里已经预留了 `role / department / employeeId`

现在缺的主要是 **前端“看得见的”权限效果**：

- 不同角色看到的菜单不同  
- 某些页面（比如用户管理）只有超管能进  
- 即便能进页面，按钮/操作也要按角色“显隐、禁用”

下面的方案专门围绕你现有的代码来设计，实现思路是：

> **后端继续做“硬权限”校验，前端做“软提示 + 体验优化”**  
> 真正的安全还是交给后端，前端只是把不同角色能做的事“提前告知”。

---

## 二、整体设计思路概览

从代码结构上分 4 层来做：

1. **登录时把角色等信息放进前端 `authStore`**  
   - 利用后端返回的 `AuthResponse`（已经有 `role/department/employeeId`）
2. **在前端封装一套简单的“权限工具函数”**  
   - `hasRole` / `hasAnyRole` 等，方便在任意组件里写条件
3. **路由 & 布局层做“粗粒度控制”**  
   - 哪些路由要登录  
   - 哪些路由只对某些角色开放（例如 `/users` 只给超级管理员）
   - 侧边菜单按角色动态过滤
4. **页面内部做“细粒度控制”**  
   - 某些按钮（新建用户、删除用户、重置密码）只给部分角色显示/启用  
   - 员工列表里的“新增/删除”按钮只给 SUPER_ADMIN / MANAGER

以下所有代码都是 **“在你现有代码基础上的补充示例”**，你可以按需复制到对应文件里修改。

---

## 三、登录：把角色等信息灌进 authStore

### 1. 后端 AuthResponse 已经准备好了

`AuthResponse`（后端）现在是这样的：

```java
public record AuthResponse(
        String token,
        String tokenType,
        Long expiresIn,
        String username,
        String role,
        String department,
        Long employeeId
) {
    public static AuthResponse of(
            String token,
            Long expiresIn,
            String username,
            String role,
            String department,
            Long employeeId
    ) {
        return new AuthResponse(
                token,
                "Bearer",
                expiresIn,
                username,
                role,
                department,
                employeeId
        );
    }
}
```

### 2. 前端类型补齐 `AuthResponse`

你前端的 `frontend/src/types/index.ts` 里 `AuthResponse` 目前只有 token 等字段，可以补齐为：

```ts
export interface AuthResponse {
  token: string
  tokenType: string
  expiresIn: number
  username?: string
  role?: string
  department?: string
  employeeId?: number
}
```

> 说明：`?` 是为了兼容以前只返回 token 的情况。

### 3. Login 页登录成功后，把角色等信息写入 authStore

`Login.tsx` 现在是：

```ts
const response = await login(values)
// 保存Token和用户名到状态管理
setAuth(response.token, values.username)
```

可以改造成利用后端返回的信息：

```ts
const response = await login(values)

// 使用后端返回的用户名和角色信息，而不是表单里的 username
setAuth(
  response.token,
  response.username || values.username,
  response.role || 'EMPLOYEE',
  response.department,
  response.employeeId
)
```

这样，`authStore` 里就有了完整的：

- `isAuthenticated`
- `token`
- `username`
- `role`（`SUPER_ADMIN` / `MANAGER` / `EMPLOYEE`）
- `department`
- `employeeId`

后面所有前端权限判断都基于这一份状态。

---

## 四、前端权限工具封装（通用小工具）

在 `frontend/src/stores/authStore.ts` 旁边新建一个小工具文件，例如 `frontend/src/stores/authSelectors.ts`：

```ts
import { useAuthStore } from './authStore'

// 获取当前角色
export const useCurrentRole = () => useAuthStore((s) => s.role)

// 判断是否具有某个角色
export const useHasRole = (role: string) => {
  return useAuthStore((s) => s.role === role)
}

// 判断是否在一组角色当中
export const useHasAnyRole = (roles: string[]) => {
  return useAuthStore((s) => !!s.role && roles.includes(s.role))
}

// 需要登录才能用，有时候也会用到
export const useIsAuthenticated = () => useAuthStore((s) => s.isAuthenticated)
```

这样在任意组件里，可以很轻松地写：

```ts
const isSuperAdmin = useHasRole('SUPER_ADMIN')
const canManageEmployee = useHasAnyRole(['SUPER_ADMIN', 'MANAGER'])
```

---

## 五、路由和菜单：粗粒度的“能不能看到页面”

### 1. Layout 侧边菜单按角色过滤

现在的 `Layout.tsx` 中菜单是写死的：

```tsx
const { username, clearAuth } = useAuthStore()

const menuItems: MenuProps['items'] = [
  { key: '/employees', icon: <TeamOutlined />, label: '员工管理' },
  { key: '/statistics', icon: <BarChartOutlined />, label: '数据统计' },
  { key: '/logs', icon: <FileTextOutlined />, label: '操作日志' },
]
```

可以先定义一份“带角色信息的菜单配置”，再根据 `role` 过滤：

```tsx
import { useAuthStore } from '../stores/authStore'
import type { MenuProps } from 'antd'

type RoleCode = 'SUPER_ADMIN' | 'MANAGER' | 'EMPLOYEE'

interface AppMenuItem {
  key: string
  label: string
  icon: React.ReactNode
  roles: RoleCode[]        // 允许访问的角色
}

const rawMenuItems: AppMenuItem[] = [
  {
    key: '/employees',
    icon: <TeamOutlined />,
    label: '员工管理',
    roles: ['SUPER_ADMIN', 'MANAGER', 'EMPLOYEE'], // 所有人可见
  },
  {
    key: '/statistics',
    icon: <BarChartOutlined />,
    label: '数据统计',
    roles: ['SUPER_ADMIN', 'MANAGER'],             // 管理层可见
  },
  {
    key: '/logs',
    icon: <FileTextOutlined />,
    label: '操作日志',
    roles: ['SUPER_ADMIN'],                        // 只有超管可见
  },
  {
    key: '/users',
    icon: <UserOutlined />,
    label: '用户管理',
    roles: ['SUPER_ADMIN'],                        // 只有超管可见
  },
]

const Layout = () => {
  const [collapsed, setCollapsed] = useState(false)
  const navigate = useNavigate()
  const location = useLocation()
  const { username, role, clearAuth } = useAuthStore()

  // 根据当前角色过滤菜单
  const menuItems: MenuProps['items'] = rawMenuItems
    .filter((item) => !role || item.roles.includes(role as RoleCode))
    .map((item) => ({
      key: item.key,
      icon: item.icon,
      label: item.label,
    }))

  // ...
}
```

这样：

- 超管能看到“员工管理 / 数据统计 / 操作日志 / 用户管理”
- 部门经理能看到“员工管理 / 数据统计”
- 普通员工只能看到“员工管理”

### 2. 路由层增加 `RequireAuth` / `RequireRole` 守卫

在 `App.tsx` 里，你已经用了 React Router。可以增加两个简单的“路由守卫”组件：

```tsx
// App.tsx 或单独文件 RouteGuards.tsx
import { Navigate } from 'react-router-dom'
import { useAuthStore } from './stores/authStore'

export const RequireAuth: React.FC<{ children: JSX.Element }> = ({ children }) => {
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated)
  if (!isAuthenticated) {
    return <Navigate to="/login" replace />
  }
  return children
}

export const RequireRole: React.FC<{ roles: string[]; children: JSX.Element }> = ({ roles, children }) => {
  const { role } = useAuthStore()
  if (!role || !roles.includes(role)) {
    // 可以跳到一个403页面，这里先简单重定向到员工列表
    return <Navigate to="/employees" replace />
  }
  return children
}
```

在路由定义中使用，比如只允许 SUPER_ADMIN 访问 `/users`：

```tsx
// App.tsx 中的路由配置片段
<Routes>
  <Route path="/login" element={<Login />} />
  <Route path="/register" element={<Register />} />

  <Route
    path="/"
    element={
      <RequireAuth>
        <Layout />
      </RequireAuth>
    }
  >
    <Route path="employees" element={<EmployeeList />} />
    <Route path="employees/new" element={<EmployeeForm />} />
    <Route path="employees/:id/edit" element={<EmployeeForm />} />

    {/* 只有超级管理员能访问用户管理页 */}
    <Route
      path="users"
      element={
        <RequireRole roles={['SUPER_ADMIN']}>
          <UserManagement />
        </RequireRole>
      }
    />

    {/* 统计、日志也可以根据角色做限制 */}
    <Route
      path="statistics"
      element={
        <RequireRole roles={['SUPER_ADMIN', 'MANAGER']}>
          <Statistics />
        </RequireRole>
      }
    />
    <Route
      path="logs"
      element={
        <RequireRole roles={['SUPER_ADMIN']}>
          <OperationLogs />
        </RequireRole>
      }
    />
  </Route>
</Routes>
```

这样，当普通员工或部门经理想“手动敲 URL 访问 `/users`”时，前端会把他拦回员工列表；后端还有 `@RequiresRole` / `@RequiresPermission` 兜底。

---

## 六、页面内部：按钮级“细粒度控制”示例

### 1. 用户管理页 `UserManagement.tsx`

你现在的 `UserManagement` 已经有很多操作按钮了，可以结合角色做进一步控制。

先在顶部获取当前角色：

```tsx
import { useAuthStore } from '../stores/authStore'

const UserManagement: React.FC = () => {
  const { role } = useAuthStore()
  const isSuperAdmin = role === 'SUPER_ADMIN'
  const isManager = role === 'MANAGER'
  // ...
}
```

#### （1）“新建用户”按钮：只给超级管理员

现在按钮是始终可见的：

```tsx
<Button
  type="primary"
  icon={<PlusOutlined />}
  onClick={() => setCreateModalVisible(true)}
>
  新建用户
</Button>
```

可以改成：

```tsx
{isSuperAdmin && (
  <Button
    type="primary"
    icon={<PlusOutlined />}
    onClick={() => setCreateModalVisible(true)}
  >
    新建用户
  </Button>
)}
```

#### （2）角色列：只是展示，无需改

这一块只是标签展示，保持不动即可。

#### （3）操作列：控制“分配角色/重置密码/删除”

目前操作列：

```tsx
render: (_, record) => (
  <Space>
    <Button ...>编辑</Button>
    <Button ...>分配角色</Button>
    <Button ...>重置密码</Button>
    {record.role !== 'SUPER_ADMIN' && (
      <Popconfirm ...>
        <Button type="link" danger icon={<DeleteOutlined />}>删除</Button>
      </Popconfirm>
    )}
  </Space>
)
```

你可以结合角色再做一层判断，比如：

- 只有 SUPER_ADMIN 可以分配角色 / 重置密码 / 删除用户
- MANAGER 只能“编辑自己部门的普通员工”（这个逻辑可以在后端已经控制，前端只做显示）

前端可以这样写：

```tsx
render: (_, record) => (
  <Space>
    {/* 所有人都可以看基本信息编辑（也可以根据需要限制） */}
    <Button
      type="link"
      icon={<EditOutlined />}
      onClick={() => openEditModal(record)}
    >
      编辑
    </Button>

    {/* 分配角色 & 重置密码：只给超级管理员 */}
    {isSuperAdmin && (
      <>
        <Button type="link" onClick={() => openRoleModal(record)}>
          分配角色
        </Button>
        <Button
          type="link"
          icon={<KeyOutlined />}
          onClick={() => openPasswordModal(record)}
        >
          重置密码
        </Button>
      </>
    )}

    {/* 删除：只允许超级管理员删除非超级管理员账号 */}
    {isSuperAdmin && record.role !== 'SUPER_ADMIN' && (
      <Popconfirm
        title="确定删除此用户？"
        onConfirm={() => handleDelete(record.id)}
      >
        <Button type="link" danger icon={<DeleteOutlined />}>
          删除
        </Button>
      </Popconfirm>
    )}
  </Space>
)
```

这样即便 MANAGER/EMPLOYEE“绕过路由守卫”进了这个页面（理论上不该），也只能看到有限的操作。

### 2. 员工列表页 `EmployeeList.tsx`

类似地，也可以在员工列表页根据角色控制“新增员工 / 删除员工”：

```tsx
import { useAuthStore } from '../stores/authStore'

const EmployeeList = () => {
  const { role } = useAuthStore()
  const canManageEmployee = role === 'SUPER_ADMIN' || role === 'MANAGER'
  // ...
}
```

#### （1）“新增员工”按钮

现在的按钮是：

```tsx
extra={
  <Button
    type="primary"
    icon={<PlusOutlined />}
    onClick={() => navigate('/employees/new')}
  >
    新增员工
  </Button>
}
```

可以改为：

```tsx
extra={
  canManageEmployee && (
    <Button
      type="primary"
      icon={<PlusOutlined />}
      onClick={() => navigate('/employees/new')}
    >
      新增员工
    </Button>
  )
}
```

#### （2）删除员工按钮

操作列里：

```tsx
<Popconfirm
  title="确定要删除这名员工吗？"
  onConfirm={() => handleDelete(record.id)}
  // ...
>
  <Button type="link" danger icon={<DeleteOutlined />}>
    删除
  </Button>
</Popconfirm>
```

可以加一层角色判断：

```tsx
{canManageEmployee && (
  <Popconfirm
    title="确定要删除这名员工吗？"
    onConfirm={() => handleDelete(record.id)}
    okText="确定"
    cancelText="取消"
  >
    <Button type="link" danger icon={<DeleteOutlined />}>
      删除
    </Button>
  </Popconfirm>
)}
```

---

## 七、和后端 `@RequiresPermission` 的对应关系

后端的权限控制更细，比如：

- `@RequiresPermission("employee:read")`
- `@RequiresPermission(value = "employee:update", checkOwner = true, checkDepartment = true)`

前端不需要完全一一还原这些细节（那样会很重，也容易和后端脱节），**只需要做到“角色层面大致一致”即可**：

- SUPER_ADMIN：前端几乎全开放（除了保护自己账号之类的小逻辑）
- MANAGER：只能管理本部门员工 + 查看统计
- EMPLOYEE：只能查看自己的信息 / 列表，只读

真正精确的“能不能改这条数据”，还是由后端的 `PermissionAspect` + `PermissionService` 来决定。  
前端的所有判断都可以理解为：

> “大概率你没权限，我就不让你点；  
>  万一你通过别的方法绕过，后端还是会拦你。”

---

## 八、总结 & 建议实践顺序

**建议你按这个顺序来落地：**

1. **先修好登录：**  
   - `AuthResponse` 类型补齐  
   - `Login.tsx` 调用 `setAuth` 时把 `role/department/employeeId` 带上

2. **封装前端权限工具：**  
   - `useHasRole` / `useHasAnyRole` / `useCurrentRole`

3. **改造 Layout 菜单 + 路由守卫：**  
   - 菜单按角色过滤  
   - `/users`、`/logs` 等路由加上 `RequireRole`

4. **最后改页面内部按钮：**  
   - `UserManagement` 的“新建/删除/分配角色/重置密码”  
   - `EmployeeList` 的“新增/删除”

做到这几步之后，前后端的 RBAC 就比较完整了：  

- **后端**：负责“真安全”，任何未授权请求必被拒绝  
- **前端**：负责“好体验”，普通员工登录之后看到的界面里，只会出现他该看到的东西。



