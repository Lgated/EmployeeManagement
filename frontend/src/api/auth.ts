/**
 * 认证相关API
 * 处理登录、注册等认证相关请求
 */
import request from '../utils/request'
import { LoginRequest, RegisterRequest, AuthResponse } from '../types'

/**
 * 用户登录
 * @param data 登录请求数据（用户名和密码）
 * @returns 认证响应（包含Token）
 */
export const login = (data: LoginRequest): Promise<AuthResponse> => {
  return request.post('/auth/login', data)
}

/**
 * 用户注册
 * @param data 注册请求数据（用户名、密码、邮箱）
 * @returns 认证响应（包含Token）
 */
export const register = (data: RegisterRequest): Promise<AuthResponse> => {
  return request.post('/auth/register', data)
}

















