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

/**
 * 刷新Token
 * 注意：RT会自动从Cookie中发送，无需手动传递
 * 通常由axios拦截器自动调用，无需手动调用
 * @returns 认证响应（包含新的Token）
 */
export const refresh = (): Promise<AuthResponse> => {
  return request.post('/auth/refresh')
}

/**
 * 用户登出
 * 调用后端登出接口，后端会：
 * 1. 将AT加入黑名单
 * 2. 删除Redis中的RT
 * 3. 使Cookie过期
 * @returns 登出响应
 */
export const logout = (): Promise<void> => {
  return request.post('/auth/logout')
}























