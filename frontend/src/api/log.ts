/**
 * 操作日志相关 API
 */
import request from '../utils/request'
import type { OperationLog } from '../types/log'

/**
 * 分页响应类型（与后端 PageResponse 保持一致）
 */
interface PageResponse<T> {
  records: T[]      // 后端使用 records，不是 content
  total: number     // 后端使用 total，不是 totalElements
  page: number
  size: number
}

/**
 * 获取操作日志列表（分页 + 筛选）
 */
export const getOperationLogs = async (
  module?: string,
  operationType?: string,
  page: number = 1,
  size: number = 10
): Promise<PageResponse<OperationLog>> => {
  const params: any = { page, size }
  
  if (module) {
    params.module = module
  }
  
  if (operationType) {
    params.operationType = operationType
  }
  
  // request 拦截器已经返回 res.data，所以这里直接返回
  return request.get('/logs', { params })
}
