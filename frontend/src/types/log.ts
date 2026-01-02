/**
 * 操作日志类型定义
 * 与后端 OperationLog 实体类保持一致
 */
export interface OperationLog {
  id: number
  userId: number
  username: string
  operationType: string
  module: string
  description: string
  method: string
  params?: string
  result?: string
  ipAddress: string
  executionTime: number
  status: string
  errorMessage?: string
  createdAt: string
}