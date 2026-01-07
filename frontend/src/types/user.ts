/**
 * 用户管理相关类型定义
 * 与后端 DTO 保持一致
 */

/**
 * 角色枚举（与后端 Role enum 保持一致）
 */
export type Role = 'SUPER_ADMIN' | 'MANAGER' | 'EMPLOYEE'

/**
 * 角色显示名称映射
 */
export const RoleNames: Record<Role, string> = {
  SUPER_ADMIN: '超级管理员',
  MANAGER: '部门经理',
  EMPLOYEE: '普通员工'
}

/**
 * 用户信息类型
 * 对应后端 UserResponse
 */
export interface User {
  id: number
  username: string
  email?: string
  role: Role
  department?: string
  employeeId?: number
  enabled: boolean
  createdAt: string
  updatedAt?: string
}

/**
 * 包含员工详情的用户类型
 * 对应后端 UserWithEmployeeDTO
 */
export interface UserWithEmployee {
  // 用户信息
  userId: number
  username: string
  email?: string
  role: Role
  userDepartment?: string
  enabled: boolean
  userCreatedAt: string

  // 员工信息（可为空）
  employeeId?: number
  employeeName?: string
  employeeDepartment?: string
  employeePosition?: string
  employeeAge?: number
  employeeGender?: string
  employeeAvatar?: string
}

/**
 * 创建用户请求类型
 * 对应后端 UserCreateRequest
 */
export interface UserCreateRequest {
  username: string
  email?: string
  role: Role
  department?: string
  employeeId?: number
}

/**
 * 更新用户请求类型
 * 对应后端 UserUpdateRequest
 */
export interface UserUpdateRequest {
  email?: string
  department?: string
  employeeId?: number
}

/**
 * 分配角色请求类型
 * 对应后端 AssignRoleRequest
 */
export interface AssignRoleRequest {
  role: Role
  department?: string
  employeeId?: number
}

/**
 * 重置密码请求类型
 * 对应后端 ResetPasswordRequest
 */
export interface ResetPasswordRequest {
  newPassword: string
}

/**
 * 分页响应类型
 * 对应后端 PageResponse<T>
 */
export interface PageResponse<T> {
  records: T[]
  total: number
  page: number    // 当前页码（从1开始）
  size: number    // 每页大小
}

/**
 * 用户列表查询参数
 */
export interface UserQueryParams {
  username?: string
  role?: string
  enabled?: boolean
  employeeName?: string   // 按员工姓名查询
  employeeId?: number     // 按员工ID查询
  page: number
  size: number
}