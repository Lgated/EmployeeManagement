/**
 * 认证状态管理
 * 使用Zustand管理用户登录状态和Token
 */
import { create } from 'zustand'
import { persist } from 'zustand/middleware'

interface AuthState {
  // 是否已登录
  isAuthenticated: boolean
  // Token
  token: string | null
  // 用户名
  username: string | null
  //角色
  role: string | null
  //部门
  department: string | null
  //员工id
  employeeId: number | null

  // 设置登录信息
  setAuth: (
    token: string,
    username: string,
    role: string,
    department?: string,
    employeeId?: number
  ) => void
  // 清除登录信息
  clearAuth: () => void
}

/**
 * 认证状态Store
 * 使用persist中间件持久化到localStorage
 */
export const useAuthStore = create<AuthState>()(
  persist(
    (set) => ({
      isAuthenticated: false,
      token: null,
      username: null,
      role: null,
      department: null,
      employeeId: null,

      // ✅ 改造：设置认证信息（包含角色）
            setAuth: (token, username, role, department, employeeId) => {
              localStorage.setItem('token', token)
              set({
                isAuthenticated: true,
                token,
                username,
                role,
                department,
                employeeId,
              })
            },

            // 清除认证信息
            clearAuth: () => {
              localStorage.removeItem('token')
              set({
                isAuthenticated: false,
                token: null,
                username: null,
                role: null,
                department: null,
                employeeId: null,
              })
            },
          }),
          {
            name: 'auth-storage',
          }
        )
      )
