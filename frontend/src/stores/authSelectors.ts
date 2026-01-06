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