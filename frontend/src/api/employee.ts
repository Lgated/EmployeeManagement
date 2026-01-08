/**
 * 员工管理相关API
 * 处理员工的增删改查和统计请求
 */
import request from '../utils/request'
import { Employee, EmployeeCreateRequest, EmployeeUpdateRequest, DeptStats , PageResult} from '../types'
import axios from 'axios'


/**
 * 获取所有员工列表
 * @param name 可选：按姓名搜索
 * @param department 可选：按部门搜索
 * @returns 员工列表
 */
export const getEmployeeList = (
  name?: string,
  department?: string,
  page: number = 1,
  size: number = 10
): Promise<PageResult<Employee>> => {
  const params: Record<string, any> = { page, size }
  if (name) params.name = name
  if (department) params.department = department

  return request.get('/employ', { params })
}

/**
 * 根据ID获取员工详情
 * @param id 员工ID
 * @returns 员工信息
 */
export const getEmployeeById = (id: number): Promise<Employee> => {
  return request.get(`/employ/${id}`)
}

/**
 * 创建新员工
 * @param data 员工创建请求数据
 * @returns 创建后的员工信息
 */
export const createEmployee = (data: EmployeeCreateRequest): Promise<Employee> => {
  return request.post('/employ', data)
}

/**
 * 更新员工信息
 * @param id 员工ID
 * @param data 员工更新请求数据
 * @returns 更新后的员工信息
 */
export const updateEmployee = (id: number, data: EmployeeUpdateRequest): Promise<Employee> => {
  return request.put(`/employ/${id}`, data)
}

/**
 * 删除员工
 * @param id 员工ID
 * @returns void
 */
export const deleteEmployee = (id: number): Promise<void> => {
  return request.delete(`/employ/${id}`)
}

/**
 * 获取员工工龄（年）
 * @param id 员工ID
 * @returns 工龄（年，数字）
 */
export const getEmployeeYears = (id: number): Promise<number> => {
  return request.get(`/employ/${id}/years`)
}

/**
 * 获取各部门员工数量统计
 * @returns 部门统计列表
 */
export const getDeptCountStats = (): Promise<DeptStats[]> => {
  return request.get('/employ/stats/dept-count')
}

/**
 * 获取各部门平均薪资统计
 * @returns 部门统计列表
 */
export const getDeptAvgSalaryStats = (): Promise<DeptStats[]> => {
  return request.get('/employ/stats/dept-avg-salary')
}


/**
 * 导出员工信息为Excel
 * @param department 部门筛选（可选）
 * @param position 职位筛选（可选）
 */
export const exportEmployees = async (department?: string, position?: string): Promise<void> => {
  const params = new URLSearchParams()
  if (department) params.append('department', department)
  if (position) params.append('position', position)
  
  // 直接使用axios，绕过request拦截器（因为拦截器会处理JSON响应，但导出需要blob）
  const token = localStorage.getItem('token')
  
  const response = await axios.get(`/api/employ/export?${params.toString()}`, {
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
  let fileName = '员工信息.xlsx'
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




