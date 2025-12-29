/**
 * 类型定义文件
 * 定义前后端交互的数据类型，与后端DTO保持一致
 */

/**
 * 统一响应结果类型
 * 对应后端 Result<T> 类
 */
export interface ApiResult<T> {
  code: number
  message: string
  data: T
}

/**
 * 员工信息类型
 * 对应后端 EmployeeResponse
 */
export interface Employee {
  id: number
  name: string
  gender?: string
  age?: number
  department: string
  position?: string
  hireDate: string  // LocalDate 序列化为字符串 "YYYY-MM-DD"
  salary: number    // BigDecimal 序列化为数字
  createdAt?: string
  updatedAt?: string
}

/**
 * 创建员工请求类型
 * 对应后端 EmployeeCreateRequest
 */
export interface EmployeeCreateRequest {
  name: string
  gender?: string
  age?: number
  department: string
  position?: string
  hireDate: string  // "YYYY-MM-DD"
  salary: number
}

/**
 * 更新员工请求类型
 * 对应后端 EmployeeUpdateRequest
 */
export interface EmployeeUpdateRequest {
  name?: string
  gender?: string
  age?: number
  department?: string
  position?: string
  hireDate?: string
  salary?: number
}

/**
 * 部门统计类型
 * 对应后端 DeptStatsResponse
 */
export interface DeptStats {
  department: string
  empCount?: number
  avgSalary?: number
}

/**
 * 登录请求类型
 * 对应后端 LoginRequest
 */
export interface LoginRequest {
  username: string
  password: string
}

/**
 * 注册请求类型
 * 对应后端 RegisterRequest
 */
export interface RegisterRequest {
  username: string
  password: string
  email?: string
}

/**
 * 认证响应类型
 * 对应后端 AuthResponse
 */
export interface AuthResponse {
  token: string
  tokenType: string
  expiresIn: number
}

