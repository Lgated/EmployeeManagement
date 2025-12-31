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
      {/* 公开路由：登录和注册页面 */}
      <Route path="/login" element={<Login />} />
      <Route path="/register" element={<Register />} />
      
      {/* 私有路由：需要登录才能访问 */}
      <Route
        path="/"
        element={
          <PrivateRoute>
            <Layout />
          </PrivateRoute>
        }
      >
        {/* 默认重定向到员工列表 */}
        <Route index element={<Navigate to="/employees" replace />} />
        
        {/* 员工管理路由 */}
        <Route path="employees" element={<EmployeeList />} />
        <Route path="employees/new" element={<EmployeeForm />} />
        <Route path="employees/:id/edit" element={<EmployeeForm />} />
        
        {/* 统计页面 */}
        <Route path="statistics" element={<Statistics />} />
      </Route>
      
      {/* 404页面 */}
      <Route path="*" element={<Navigate to="/employees" replace />} />
    </Routes>
  )
}

export default App














