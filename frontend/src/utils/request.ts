/**
 * Axios请求封装
 * 统一处理请求拦截、响应拦截、错误处理、Token管理、无感刷新
 */
import axios, { AxiosInstance, AxiosError, InternalAxiosRequestConfig } from 'axios'
import { message } from 'antd'
import { ApiResult } from '../types'
import { useAuthStore } from '../stores/authStore'

// 创建axios实例
const request: AxiosInstance = axios.create({
  baseURL: '/api',  // 通过vite代理转发到后端
  timeout: 10000,  // 请求超时时间10秒
  headers: {
    'Content-Type': 'application/json',
  },
  withCredentials: true,  // 允许发送Cookie（重要：用于RT）
})

// 标记是否正在刷新Token，防止并发刷新
let isRefreshing = false
// 存储待重试的请求
let failedQueue: Array<{
  resolve: (value?: any) => void
  reject: (reason?: any) => void
}> = []

/**
 * 刷新Token
 * 注意：RT会自动从Cookie中发送，无需手动传递
 * 刷新成功后，同时更新authStore中的用户信息
 */
const refreshToken = async (): Promise<string | null> => {
  try {
    // 调用刷新接口，RT会自动从Cookie发送
    const response = await axios.post('/api/auth/refresh', {}, {
      withCredentials: true,  // 确保发送Cookie
    })
    
    const res = response.data as ApiResult<any>
    if (res.code === 200 && res.data?.token) {
      const authData = res.data
      
      // 更新localStorage中的AT
      localStorage.setItem('token', authData.token)
      
      // ✅ 关键：如果后端返回了用户信息，更新authStore
      // 这样可以确保刷新Token后，用户信息（role、department等）保持最新
      if (authData.username) {
        useAuthStore.getState().setAuth(
          authData.token,
          authData.username,
          authData.role,
          authData.department,
          authData.employeeId
        )
      }
      
      return authData.token
    }
    return null
  } catch (error) {
    console.error('刷新Token失败:', error)
    return null
  }
}

/**
 * 处理队列中的请求
 */
const processQueue = (error: any, token: string | null = null) => {
  failedQueue.forEach((promise) => {
    if (error) {
      promise.reject(error)
    } else {
      promise.resolve(token)
    }
  })
  failedQueue = []
}

/**
 * 请求拦截器
 * 在发送请求前添加Token到请求头
 */
request.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    // 从localStorage获取Token
    const token = localStorage.getItem('token')
    
    // 如果存在Token，添加到请求头
    if (token && config.headers) {
      config.headers.Authorization = `Bearer ${token}`
    }
    
    return config
  },
  (error: AxiosError) => {
    // 请求错误处理
    console.error('请求错误:', error)
    return Promise.reject(error)
  }
)

/**
 * 响应拦截器
 * 统一处理响应数据和错误，实现无感刷新
 */
request.interceptors.response.use(
  (response) => {
    const res = response.data as ApiResult<any>
    
    // 如果后端返回的code不是200，视为错误
    if (res.code !== 200) {
      message.error(res.message || '请求失败')
      return Promise.reject(new Error(res.message || '请求失败'))
    }
    
    // 返回数据部分
    return res.data
  },
  async (error: AxiosError) => {
    const originalRequest = error.config as InternalAxiosRequestConfig & { _retry?: boolean }
    
    // HTTP错误处理
    if (error.response) {
      const status = error.response.status
      
      // 401错误：Token过期，尝试刷新
      if (status === 401 && originalRequest && !originalRequest._retry) {
        // 如果是刷新接口本身返回401，说明RT也过期了，直接跳转登录
        if (originalRequest.url?.includes('/auth/refresh')) {
          // 清除前端状态
          localStorage.removeItem('token')
          useAuthStore.getState().clearAuth()
          message.error('登录已过期，请重新登录')
          window.location.href = '/login'
          return Promise.reject(error)
        }
        
        // 如果正在刷新，将请求加入队列
        if (isRefreshing) {
          return new Promise((resolve, reject) => {
            failedQueue.push({ resolve, reject })
          })
            .then((token) => {
              if (originalRequest.headers) {
                originalRequest.headers.Authorization = `Bearer ${token}`
              }
              return request(originalRequest)
            })
            .catch((err) => {
              return Promise.reject(err)
            })
        }
        
        // 标记正在刷新
        originalRequest._retry = true
        isRefreshing = true
        
        try {
          // 刷新Token（RT会自动从Cookie发送）
          const newToken = await refreshToken()
          
          if (newToken) {
            // 刷新成功，处理队列中的请求
            processQueue(null, newToken)
            
            // 更新原请求的Token并重试
            if (originalRequest.headers) {
              originalRequest.headers.Authorization = `Bearer ${newToken}`
            }
            
            isRefreshing = false
            return request(originalRequest)
          } else {
            // 刷新失败，跳转登录
            throw new Error('刷新Token失败')
          }
        } catch (refreshError) {
          // 刷新失败，处理队列并跳转登录
          processQueue(refreshError, null)
          isRefreshing = false
          // 清除前端状态
          localStorage.removeItem('token')
          useAuthStore.getState().clearAuth()
          message.error('登录已过期，请重新登录')
          window.location.href = '/login'
          return Promise.reject(refreshError)
        }
      }
      
      // 其他HTTP错误
      switch (status) {
        case 403:
          message.error('没有权限访问')
          break
        case 404:
          message.error('请求的资源不存在')
          break
        case 500:
          message.error('服务器内部错误')
          break
        default:
          message.error(`请求失败: ${status}`)
      }
    } else if (error.request) {
      // 请求已发出但没有收到响应
      message.error('网络错误，请检查网络连接')
    } else {
      // 其他错误
      message.error('请求配置错误')
    }
    
    return Promise.reject(error)
  }
)

export default request























