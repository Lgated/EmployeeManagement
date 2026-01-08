import request from '../utils/request'
import axios from 'axios'
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

/**
 * 导出用户信息为Excel
 * @param role 角色筛选（可选）
 * @param department 部门筛选（可选）
 */
export const exportUsers = async (role?: string, department?: string): Promise<void> => {
  const params = new URLSearchParams()
  if (role) params.append('role', role)
  if (department) params.append('department', department)
  
  // 直接使用axios，绕过request拦截器（因为拦截器会处理JSON响应，但导出需要blob）
  const token = localStorage.getItem('token')
  
  const response = await axios.get(`/api/users/export?${params.toString()}`, {
    responseType: 'blob',
    withCredentials: true,
    headers: {
      Authorization: token ? `Bearer ${token}` : ''
    }
  })
  
  // 确保响应数据是Blob类型
  const blob = response.data instanceof Blob 
    ? response.data 
    : new Blob([response.data], { 
        type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' 
      })
  
  // 创建下载链接
  const url = window.URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  
  // 从响应头获取文件名
  const contentDisposition = response.headers['content-disposition']
  let fileName = '用户信息.xlsx'
  if (contentDisposition) {
    // 处理UTF-8编码的文件名
    const fileNameMatch = contentDisposition.match(/filename\*=UTF-8''(.+)|filename="?([^"]+)"?/i)
    if (fileNameMatch) {
      fileName = fileNameMatch[1] 
        ? decodeURIComponent(fileNameMatch[1])
        : fileNameMatch[2] || fileName
    }
  }
  
  link.setAttribute('download', fileName)
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  window.URL.revokeObjectURL(url)
}