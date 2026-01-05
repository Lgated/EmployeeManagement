/**
 * 应用主组件
 * 配置路由和全局布局
 */
import { Routes, Route, Navigate } from 'react-router-dom'
import { useAuthStore } from './stores/authStore'
import Layout from './components/Layout'
import Login from './pages/Login'
import Register from './pages/Register'
import EmployeeList from './pages/EmployeeList'
import EmployeeForm from './pages/EmployeeForm'
import Statistics from './pages/Statistics'
import OperationLogs from './pages/OperationLogs'
import UserManagement from './pages/UserManagement'



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

/**
 * 私有路由组件
 * 用于保护需要登录才能访问的页面
 */
const PrivateRoute = ({ children }: { children: React.ReactNode }) => {
  const { isAuthenticated } = useAuthStore()
  
  // 如果未登录，重定向到登录页
  if (!isAuthenticated) {
    return <Navigate to="/login" replace />
  }
  
  return <>{children}</>
}

function App() {
  return (
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
  )
}

export default App















