import React, { useState } from 'react'
import { Upload, message } from 'antd'
import { LoadingOutlined, PlusOutlined } from '@ant-design/icons'
import { uploadFile } from '../api/file'

interface ImageUploadProps {
  value?: string
  onChange?: (url: string) => void
}

const ImageUpload: React.FC<ImageUploadProps> = ({ value, onChange }) => {
  const [loading, setLoading] = useState(false)

  // 自定义上传逻辑
  const customRequest = async (options: any) => {
    const { file, onSuccess, onError } = options
    setLoading(true)
    try {
      const url = await uploadFile(file as File)
      onChange?.(url)
      onSuccess(url)
      message.success('上传成功')
    } catch (err) {
      onError(err)
      message.error('上传失败')
    } finally {
      setLoading(false)
    }
  }

  // 上传前校验
  const beforeUpload = (file: File) => {
    const isImage = file.type.startsWith('image/')
    if (!isImage) {
      message.error('只能上传图片文件！')
      return false
    }
    const isLt5M = file.size / 1024 / 1024 < 5
    if (!isLt5M) {
      message.error('图片大小不能超过 5MB！')
      return false
    }
    return true
  }

  const uploadButton = (
    <div>
      {loading ? <LoadingOutlined /> : <PlusOutlined />}
      <div style={{ marginTop: 8 }}>上传头像</div>
    </div>
  )

  return (
    <Upload
      name="avatar"
      listType="picture-card"
      showUploadList={false}
      customRequest={customRequest}
      beforeUpload={beforeUpload}
    >
      {value ? (
        <img
          src={value.startsWith('http') ? value : `http://localhost:8080${value}`}
          alt="avatar"
          style={{ width: '100%', height: '100%', objectFit: 'cover' }}
        />
      ) : (
        uploadButton
      )}
    </Upload>
  )
}

export default ImageUpload
