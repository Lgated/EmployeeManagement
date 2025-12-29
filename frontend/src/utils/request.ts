/**
 * Axios请求封装
 * 统一处理请求拦截、响应拦截、错误处理、Token管理
 */
import axios, { AxiosInstance, AxiosError, InternalAxiosRequestConfig } from 'axios'
import { message } from 'antd'
import { ApiResult } from '../types'

// 创建axios实例
const request: AxiosInstance = axios.create({
  baseURL: '/api',  // 通过vite代理转发到后端
  timeout: 10000,  // 请求超时时间10秒
  headers: {
    'Content-Type': 'application/json',
  },
})

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
 * 统一处理响应数据和错误
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
  (error: AxiosError) => {
    // HTTP错误处理
    if (error.response) {
      const status = error.response.status
      
      switch (status) {
        case 401:
          // 未授权，清除Token并跳转到登录页
          localStorage.removeItem('token')
          message.error('登录已过期，请重新登录')
          window.location.href = '/login'
          break
        case 403:
          message.error('没有权限访问该资源')
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

