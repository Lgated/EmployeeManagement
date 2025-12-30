/**
 * 登录页面
 * 用户登录功能，成功后跳转到员工管理页面
 */
import { useState } from 'react'
import { useNavigate, Link } from 'react-router-dom'
import { Form, Input, Button, Card, message } from 'antd'
import { UserOutlined, LockOutlined } from '@ant-design/icons'
import { login } from '../api/auth'
import { useAuthStore } from '../stores/authStore'
import type { LoginRequest } from '../types'

const Login = () => {
  const navigate = useNavigate()
  const { setAuth } = useAuthStore()
  const [loading, setLoading] = useState(false)

  /**
   * 处理登录表单提交
   */
  const handleSubmit = async (values: LoginRequest) => {
    try {
      setLoading(true)
      
      // 调用登录API
      const response = await login(values)
      
      // 保存Token和用户名到状态管理
      setAuth(response.token, values.username)
      
      message.success('登录成功')
      
      // 跳转到员工列表页面
      navigate('/employees')
    } catch (error: any) {
      message.error(error.message || '登录失败，请检查用户名和密码')
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
        background: 'linear-gradient(135deg, #f0f1f7ff 0%, #a762f0ff 100%)',
      }}
    >
      <Card
        title={
          <div style={{ textAlign: 'center', fontSize: 24, fontWeight: 'bold' }}>
            APJ员工管理系统
          </div>
        }
        style={{ width: 400 }}
      >
        <Form
          name="login"
          onFinish={handleSubmit}
          autoComplete="off"
          size="large"
        >
          <Form.Item
            name="username"
            rules={[
              { required: true, message: '请输入用户名' },
            ]}
          >
            <Input
              prefix={<UserOutlined />}
              placeholder="用户名"
            />
          </Form.Item>

          <Form.Item
            name="password"
            rules={[
              { required: true, message: '请输入密码' },
            ]}
          >
            <Input.Password
              prefix={<LockOutlined />}
              placeholder="密码"
            />
          </Form.Item>

          <Form.Item>
            <Button
              type="primary"
              htmlType="submit"
              block
              loading={loading}
            >
              登录
            </Button>
          </Form.Item>

          <div style={{ textAlign: 'center' }}>
            <Link to="/register">还没有账号？立即注册</Link>
          </div>
        </Form>
      </Card>
    </div>
  )
}

export default Login

