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
  
  // 设置登录信息
  setAuth: (token: string, username: string) => void
  // 清除登录信息（退出登录）
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
      
      // 设置认证信息
      setAuth: (token: string, username: string) => {
        // 同时保存到localStorage（用于axios拦截器）
        localStorage.setItem('token', token)
        set({
          isAuthenticated: true,
          token,
          username,
        })
      },
      
      // 清除认证信息
      clearAuth: () => {
        localStorage.removeItem('token')
        set({
          isAuthenticated: false,
          token: null,
          username: null,
        })
      },
    }),
    {
      name: 'auth-storage', // localStorage的key
    }
  )
)

