/**
 * 员工管理相关API
 * 处理员工的增删改查和统计请求
 */
import request from '../utils/request'
import { Employee, EmployeeCreateRequest, EmployeeUpdateRequest, DeptStats } from '../types'

/**
 * 获取所有员工列表
 * @param name 可选：按姓名搜索
 * @param department 可选：按部门搜索
 * @returns 员工列表
 */
export const getEmployeeList = (name?: string, department?: string): Promise<Employee[]> => {
  const params: Record<string, string> = {}
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















