/**
 * 文件上传相关API
 */
import request from '../utils/request'

/**
 * 上传文件（头像）
 * @param file 文件对象
 * @returns 文件访问路径（如：/uploads/avatars/xxx.jpg）
 */
export const uploadFile = (file: File): Promise<string> => {
  const formData = new FormData()
  formData.append('file', file)
  
  // 注意：这里不要加 /api 前缀，因为 request.ts 中已经配置了 baseURL: '/api'
  return request.post('/files/upload', formData, {
    headers: {
      'Content-Type': 'multipart/form-data',
    },
  })
}
