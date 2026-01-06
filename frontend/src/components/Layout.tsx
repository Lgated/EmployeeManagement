/**
 * 主布局组件
 * 包含侧边栏、顶部导航、内容区域
 */
import { useState } from 'react'
import { Outlet, useNavigate, useLocation } from 'react-router-dom'
import { Layout as AntLayout, Menu, Avatar, Dropdown, Button } from 'antd'
import type { MenuProps } from 'antd'
import {
  UserOutlined,
  TeamOutlined,
  BarChartOutlined,
  LogoutOutlined,
  MenuFoldOutlined,
  MenuUnfoldOutlined,
  FileTextOutlined,
} from '@ant-design/icons'
import { useAuthStore } from '../stores/authStore'

type RoleCode = 'SUPER_ADMIN' | 'MANAGER' | 'EMPLOYEE'

interface AppMenuItem {
  key: string
  label: string
  icon: React.ReactNode
  roles: RoleCode[]        // 允许访问的角色
}

const rawMenuItems: AppMenuItem[] = [
  {
    key: '/employees',
    icon: <TeamOutlined />,
    label: '员工管理',
    roles: ['SUPER_ADMIN', 'MANAGER', 'EMPLOYEE'], // 所有人可见
  },
  {
    key: '/users',
    icon: <UserOutlined />,
    label: '用户管理',
    roles: ['SUPER_ADMIN'],                        // 只有超管可见
  },
  {
    key: '/statistics',
    icon: <BarChartOutlined />,
    label: '数据统计',
    roles: ['SUPER_ADMIN', 'MANAGER'],             // 管理层可见
  },
  {
    key: '/logs',
    icon: <FileTextOutlined />,
    label: '操作日志',
    roles: ['SUPER_ADMIN'],                        // 只有超管可见
  },
]


const { Header, Sider, Content } = AntLayout

const Layout = () => {
  const [collapsed, setCollapsed] = useState(false)
  const navigate = useNavigate()
  const location = useLocation()
  const { username, role, clearAuth } = useAuthStore()

  // 菜单项配置：根据当前角色过滤菜单
  const menuItems: MenuProps['items'] = rawMenuItems
    .filter((item) => !role || item.roles.includes(role as RoleCode))
    .map((item) => ({
      key: item.key,
      icon: item.icon,
      label: item.label,
    }))

  // 处理菜单点击
  const handleMenuClick = ({ key }: { key: string }) => {
    navigate(key)
  }

  // 处理退出登录
  const handleLogout = () => {
    clearAuth()
    navigate('/login')
  }

  // 用户下拉菜单
  const userMenuItems: MenuProps['items'] = [
    {
      key: 'logout',
      icon: <LogoutOutlined />,
      label: '退出登录',
      onClick: handleLogout,
    },
  ]

  return (
    <AntLayout style={{ minHeight: '100vh' }}>
      {/* 侧边栏 */}
      <Sider trigger={null} collapsible collapsed={collapsed}>
        <div
          style={{
            height: 64,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            color: '#fff',
            fontSize: collapsed ? 16 : 20,
            fontWeight: 'bold',
            background: 'rgba(255, 255, 255, 0.1)',
          }}
        >
          {collapsed ? 'APJ' : 'APJ员工管理系统'}
        </div>
        <Menu
          theme="dark"
          mode="inline"
          selectedKeys={[location.pathname]}
          items={menuItems}
          onClick={handleMenuClick}
        />
      </Sider>

      <AntLayout>
        {/* 顶部导航栏 */}
        <Header
          style={{
            padding: '0 24px',
            background: '#fff',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
          }}
        >
          <Button
            type="text"
            icon={collapsed ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />}
            onClick={() => setCollapsed(!collapsed)}
            style={{ fontSize: 16 }}
          />

          <Dropdown menu={{ items: userMenuItems }} placement="bottomRight">
            <div style={{ cursor: 'pointer', display: 'flex', alignItems: 'center', gap: 8 }}>
              <Avatar icon={<UserOutlined />} />
              <span>{username || '用户'}</span>
            </div>
          </Dropdown>
        </Header>

        {/* 内容区域 */}
        <Content
          style={{
            margin: '24px',
            padding: 24,
            background: '#fff',
            borderRadius: 8,
            minHeight: 280,
          }}
        >
          <Outlet />
        </Content>
      </AntLayout>
    </AntLayout>
  )
}

export default Layout














