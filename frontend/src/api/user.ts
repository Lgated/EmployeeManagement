import request from '../utils/request'
import type {
  User,
  UserWithEmployee,
  UserCreateRequest,
  UserUpdateRequest,
  AssignRoleRequest,
  ResetPasswordRequest,
  PageResponse
} from '../types/user'

/**
 * 获取用户列表
 */
export const getUserList = (params: {
  username?: string
  role?: string
  enabled?: boolean
  page?: number
  size?: number
}) => {
  return request.get<PageResponse<User>>('/users', { params })
}

/**
 * 获取用户列表（包含员工信息）
 */
export const getUserListWithEmployee = (params: {
  username?: string
  role?: string
  enabled?: boolean
  page?: number
  size?: number
}) => {
  return request.get<PageResponse<UserWithEmployee>>('/users/with-employee', { params })
}

/**
 * 获取单个用户详情
 */
export const getUserById = (id: number) => {
  return request.get<User>(`/users/${id}`)
}

/**
 * 根据员工姓名查找用户
 */
export const getUserByEmployeeName = (employeeName: string) => {
  return request.get<User>(`/users/by-employee-name/${encodeURIComponent(employeeName)}`)
}

/**
 * 根据员工ID查找关联用户列表
 */
export const getUsersByEmployeeId = (employeeId: number) => {
  return request.get<User[]>(`/users/by-employee-id/${employeeId}`)
}

/**
 * 创建用户
 */
export const createUser = (data: UserCreateRequest) => {
  return request.post<User>('/users', data)
}

/**
 * 创建用户并加载员工信息
 */
export const createUserWithEmployee = (data: UserCreateRequest) => {
  return request.post<User>('/users/with-employee', data)
}

/**
 * 更新用户信息
 */
export const updateUser = (id: number, data: UserUpdateRequest) => {
  return request.put<User>(`/users/${id}`, data)
}

/**
 * 启用/禁用用户
 */
export const updateUserStatus = (id: number, enabled: boolean) => {
  return request.put<void>(`/users/${id}/status`, null, {
    params: { enabled }
  })
}

/**
 * 分配角色
 */
export const assignRole = (id: number, data: AssignRoleRequest) => {
  return request.put<User>(`/users/${id}/role`, data)
}

/**
 * 删除用户
 */
export const deleteUser = (id: number) => {
  return request.delete<void>(`/users/${id}`)
}

/**
 * 重置密码
 */
export const resetPassword = (id: number, data: ResetPasswordRequest) => {
  return request.put<void>(`/users/${id}/password`, data)
}

/**
 * 处理员工离职，禁用关联账号
 */
export const handleEmployeeResignation = (employeeId: number) => {
  return request.put<void>(`/users/handle-resignation/${employeeId}`)
}