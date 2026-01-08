/**
 * 注册页面
 * 新用户注册功能，注册成功后自动登录
 */
import { useState } from 'react'
import { useNavigate, Link } from 'react-router-dom'
import { Form, Input, Button, Card, message } from 'antd'
import { UserOutlined, LockOutlined, MailOutlined } from '@ant-design/icons'
import { register } from '../api/auth'
import { useAuthStore } from '../stores/authStore'
import type { RegisterRequest } from '../types'

const Register = () => {
  const navigate = useNavigate()
  const { setAuth } = useAuthStore()
  const [loading, setLoading] = useState(false)

  /**
   * 处理注册表单提交
   */
  const handleSubmit = async (values: RegisterRequest) => {
    try {
      setLoading(true)
      
      // 调用注册API
      const response = await register(values)
      
      // 保存Token和用户名到状态管理
      setAuth(response.token, values.username)
      
      message.success('注册成功，已自动登录')
      
      // 跳转到员工列表页面
      navigate('/employees')
    } catch (error: any) {
      message.error(error.message || '注册失败，请检查输入信息')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div
      style={{
        minHeight: '100vh',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        background: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
      }}
    >
      <Card
        title={
          <div style={{ textAlign: 'center', fontSize: 24, fontWeight: 'bold' }}>
            用户注册
          </div>
        }
        style={{ width: 400 }}
      >
        <Form
          name="register"
          onFinish={handleSubmit}
          autoComplete="off"
          size="large"
        >
          <Form.Item
            name="username"
            rules={[
              { required: true, message: '请输入用户名' },
              { min: 3, message: '用户名长度至少3个字符' },
              { max: 50, message: '用户名长度不能超过50个字符' },
            ]}
          >
            <Input
              prefix={<UserOutlined />}
              placeholder="用户名（3-50个字符）"
            />
          </Form.Item>

          <Form.Item
            name="password"
            rules={[
              { required: true, message: '请输入密码' },
              { min: 6, message: '密码长度至少6个字符' },
            ]}
          >
            <Input.Password
              prefix={<LockOutlined />}
              placeholder="密码（至少6个字符）"
            />
          </Form.Item>

          <Form.Item
            name="email"
            rules={[
              { type: 'email', message: '请输入有效的邮箱地址' },
            ]}
          >
            <Input
              prefix={<MailOutlined />}
              placeholder="邮箱（可选）"
            />
          </Form.Item>

          <Form.Item>
            <Button
              type="primary"
              htmlType="submit"
              block
              loading={loading}
            >
              注册
            </Button>
          </Form.Item>

          <div style={{ textAlign: 'center' }}>
            <Link to="/login">已有账号？立即登录</Link>
          </div>
        </Form>
      </Card>
    </div>
  )
}

export default Register



























