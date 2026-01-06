import React, { useEffect, useState } from 'react'
import {
  Table,
  Button,
  Space,
  Tag,
  Modal,
  Form,
  Input,
  InputNumber,
  Select,
  Switch,
  message,
  Popconfirm,
  Card,
  Alert,
  Avatar,
  Tooltip,
  Collapse
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import {
  PlusOutlined,
  EditOutlined,
  DeleteOutlined,
  KeyOutlined,
  SearchOutlined,
  ReloadOutlined,
  LockOutlined,
  UserOutlined,
  InfoCircleOutlined
} from '@ant-design/icons'
import {
  getUserListWithEmployee,
  getUserByEmployeeName,
  getUsersByEmployeeId,
  createUserWithEmployee,
  handleEmployeeResignation,
  updateUser,
  updateUserStatus,
  assignRole,
  deleteUser,
  resetPassword
} from '../api/user'
import type {
  UserWithEmployee,
  UserCreateRequest,
  UserUpdateRequest,
  AssignRoleRequest,
  ResetPasswordRequest,
  Role
} from '../types/user'
import { RoleNames } from '../types/user'
import { getEmployeeList } from '../api/employee'
import type { Employee } from '../types'

const { Option } = Select
const { Panel } = Collapse

const UserManagement: React.FC = () => {
  // ========== 状态 ==========
  const [users, setUsers] = useState<UserWithEmployee[]>([])
  const [loading, setLoading] = useState(false)
  const [pagination, setPagination] = useState({
    current: 1,
    pageSize: 10,
    total: 0
  })
  const [permissionError, setPermissionError] = useState(false)
  const [filters, setFilters] = useState({
    username: '',
    role: undefined as string | undefined,
    enabled: undefined as boolean | undefined,
    employeeName: '',
    employeeId: undefined as number | undefined
  })
  const [advancedSearchVisible, setAdvancedSearchVisible] = useState(false)

  // 员工下拉
  const [employeeList, setEmployeeList] = useState<Employee[]>([])
  const [employeeLoading, setEmployeeLoading] = useState(false)

  // 模态框
  const [createModalVisible, setCreateModalVisible] = useState(false)
  const [editModalVisible, setEditModalVisible] = useState(false)
  const [roleModalVisible, setRoleModalVisible] = useState(false)
  const [passwordModalVisible, setPasswordModalVisible] = useState(false)
  const [resignationModalVisible, setResignationModalVisible] = useState(false)
  const [currentUser, setCurrentUser] = useState<UserWithEmployee | null>(null)

  // 表单
  const [createForm] = Form.useForm()
  const [editForm] = Form.useForm()
  const [roleForm] = Form.useForm()
  const [passwordForm] = Form.useForm()
  const [resignationForm] = Form.useForm()

  // 加载员工列表（选择用）
  const loadEmployeeList = async () => {
    setEmployeeLoading(true)
    try {
      const res = await getEmployeeList(undefined, undefined, 1, 1000)
      setEmployeeList(res.records)
    } catch (e) {
      console.error('加载员工列表失败', e)
    } finally {
      setEmployeeLoading(false)
    }
  }

  // 加载用户列表（带员工信息）
  const loadUsers = async () => {
    setLoading(true)
    try {
      setPermissionError(false)

      let searchType: 'normal' | 'employeeName' | 'employeeId' = 'normal'
      let searchValue: string | number | undefined = undefined

      if (filters.employeeId) {
        searchType = 'employeeId'
        searchValue = filters.employeeId
      } else if (filters.employeeName && filters.employeeName.trim()) {
        searchType = 'employeeName'
        searchValue = filters.employeeName.trim()
      }

      if (searchType === 'employeeId' && searchValue) {
        const list = await getUsersByEmployeeId(searchValue as number)
        setUsers(
          list.map((u) => ({
            userId: u.id,
            username: u.username,
            email: u.email,
            role: u.role,
            userDepartment: u.department,
            enabled: u.enabled,
            userCreatedAt: u.createdAt,
            employeeId: u.employeeId
          }))
        )
        setPagination({ ...pagination, total: list.length, current: 1 })
      } else if (searchType === 'employeeName' && searchValue) {
        try {
          const user = await getUserByEmployeeName(searchValue as string)
          setUsers([
            {
              userId: user.id,
              username: user.username,
              email: user.email,
              role: user.role,
              userDepartment: user.department,
              enabled: user.enabled,
              userCreatedAt: user.createdAt,
              employeeId: user.employeeId
            }
          ])
          setPagination({ ...pagination, total: 1, current: 1 })
        } catch (error: any) {
          if (error.response?.status === 404 || error.message?.includes('没有关联')) {
            message.warning('该员工没有关联的用户账号')
            setUsers([])
            setPagination({ ...pagination, total: 0, current: 1 })
          } else {
            throw error
          }
        }
      } else {
        const res = await getUserListWithEmployee({
          username: filters.username || undefined,
          role: filters.role,
          enabled: filters.enabled,
          page: pagination.current,
          size: pagination.pageSize
        })
        setUsers(res.records)
        setPagination({ ...pagination, total: res.total })
      }
    } catch (error: any) {
      const isPermissionError =
        error.response?.status === 403 ||
        error.status === 403 ||
        error.message?.includes('403') ||
        error.message?.includes('权限') ||
        error.message?.includes('没有权限')

      if (isPermissionError) {
        setPermissionError(true)
      } else {
        message.error('加载失败: ' + (error.message || '未知错误'))
      }
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    loadUsers()
  }, [pagination.current, pagination.pageSize])

  useEffect(() => {
    if (createModalVisible) {
      loadEmployeeList()
    }
  }, [createModalVisible])

  const handleSearch = () => {
    setPagination({ ...pagination, current: 1 })
    loadUsers()
  }

  const handleReset = () => {
    setFilters({
      username: '',
      role: undefined,
      enabled: undefined,
      employeeName: '',
      employeeId: undefined
    })
    setPagination({ ...pagination, current: 1 })
    setTimeout(() => loadUsers(), 50)
  }

  // 创建用户（使用新接口）
  const handleCreate = async (values: UserCreateRequest) => {
    try {
      await createUserWithEmployee(values)
      message.success('创建成功')
      setCreateModalVisible(false)
      createForm.resetFields()
      loadUsers()
    } catch (error: any) {
      message.error('创建失败: ' + (error.message || '未知错误'))
    }
  }

  const handleUpdate = async (values: UserUpdateRequest) => {
    if (!currentUser) return
    try {
      await updateUser(currentUser.userId, values)
      message.success('更新成功')
      setEditModalVisible(false)
      editForm.resetFields()
      loadUsers()
    } catch (error) {
      message.error('更新失败')
    }
  }

  const handleAssignRole = async (values: AssignRoleRequest) => {
    if (!currentUser) return
    try {
      await assignRole(currentUser.userId, values)
      message.success('角色分配成功')
      setRoleModalVisible(false)
      roleForm.resetFields()
      loadUsers()
    } catch (error) {
      message.error('角色分配失败')
    }
  }

  const handleResetPassword = async (values: ResetPasswordRequest) => {
    if (!currentUser) return
    try {
      await resetPassword(currentUser.userId, values)
      message.success('密码重置成功')
      setPasswordModalVisible(false)
      passwordForm.resetFields()
    } catch (error) {
      message.error('密码重置失败')
    }
  }

  const handleToggleStatus = async (user: UserWithEmployee) => {
    try {
      await updateUserStatus(user.userId, !user.enabled)
      message.success(user.enabled ? '已禁用' : '已启用')
      loadUsers()
    } catch (error) {
      message.error('操作失败')
    }
  }

  const handleDelete = async (id: number) => {
    try {
      await deleteUser(id)
      message.success('删除成功')
      loadUsers()
    } catch (error) {
      message.error('删除失败')
    }
  }

  const handleResignation = async (values: { employeeId: number }) => {
    try {
      await handleEmployeeResignation(values.employeeId)
      message.success('已禁用该员工关联的所有用户账号')
      setResignationModalVisible(false)
      resignationForm.resetFields()
      loadUsers()
    } catch (error: any) {
      message.error('处理失败: ' + (error.message || '未知错误'))
    }
  }

  const openEditModal = (user: UserWithEmployee) => {
    setCurrentUser(user)
    editForm.setFieldsValue({
      email: user.email,
      department: user.userDepartment,
      employeeId: user.employeeId
    })
    setEditModalVisible(true)
  }

  const openRoleModal = (user: UserWithEmployee) => {
    setCurrentUser(user)
    roleForm.setFieldsValue({
      role: user.role,
      department: user.userDepartment,
      employeeId: user.employeeId
    })
    setRoleModalVisible(true)
  }

  const openPasswordModal = (user: UserWithEmployee) => {
    setCurrentUser(user)
    passwordForm.resetFields()
    setPasswordModalVisible(true)
  }

  const openResignationModal = () => {
    resignationForm.resetFields()
    setResignationModalVisible(true)
  }

  // 表格列
  const columns: ColumnsType<UserWithEmployee> = [
    {
      title: 'ID',
      dataIndex: 'userId',
      width: 80
    },
    {
      title: '用户名',
      dataIndex: 'username',
      width: 150
    },
    {
      title: '邮箱',
      dataIndex: 'email',
      width: 200
    },
    {
      title: '角色',
      dataIndex: 'role',
      width: 120,
      render: (role: Role) => {
        const color = { SUPER_ADMIN: 'red', MANAGER: 'blue', EMPLOYEE: 'green' }[role]
        return <Tag color={color}>{RoleNames[role]}</Tag>
      }
    },
    {
      title: '用户部门',
      dataIndex: 'userDepartment',
      width: 120,
      render: (dept: string) => dept || '-'
    },
    {
      title: '员工姓名',
      dataIndex: 'employeeName',
      width: 140,
      render: (name: string, record) => {
        if (name) {
          return (
            <Space>
              {record.employeeAvatar ? (
                <Avatar size="small" src={record.employeeAvatar} />
              ) : (
                <Avatar size="small" icon={<UserOutlined />} />
              )}
              <span>{name}</span>
            </Space>
          )
        }
        return <span style={{ color: '#999' }}>-</span>
      }
    },
    {
      title: '员工部门',
      dataIndex: 'employeeDepartment',
      width: 120,
      render: (dept: string) => dept || '-'
    },
    {
      title: '员工职位',
      dataIndex: 'employeePosition',
      width: 120,
      render: (pos: string) => pos || '-'
    },
    {
      title: '状态',
      dataIndex: 'enabled',
      width: 100,
      render: (enabled: boolean, record) => (
        <Switch
          checked={enabled}
          onChange={() => handleToggleStatus(record)}
          checkedChildren="启用"
          unCheckedChildren="禁用"
        />
      )
    },
    {
      title: '创建时间',
      dataIndex: 'userCreatedAt',
      width: 180,
      render: (date: string) => new Date(date).toLocaleString('zh-CN')
    },
    {
      title: '操作',
      key: 'action',
      width: 360,
      fixed: 'right',
      render: (_, record) => (
        <Space>
          <Button type="link" icon={<EditOutlined />} onClick={() => openEditModal(record)}>
            编辑
          </Button>
          <Button type="link" onClick={() => openRoleModal(record)}>
            分配角色
          </Button>
          <Button type="link" icon={<KeyOutlined />} onClick={() => openPasswordModal(record)}>
            重置密码
          </Button>
          {record.role !== 'SUPER_ADMIN' && (
            <Popconfirm title="确定删除此用户？" onConfirm={() => handleDelete(record.userId)}>
              <Button type="link" danger icon={<DeleteOutlined />}>
                删除
              </Button>
            </Popconfirm>
          )}
        </Space>
      )
    }
  ]

  if (permissionError) {
    return (
      <div style={{ padding: 24 }}>
        <Card>
          <Alert
            message="权限不足"
            description="您当前没有权限访问用户管理功能，请联系管理员获取相应权限。"
            type="warning"
            icon={<LockOutlined />}
            showIcon
            style={{ marginBottom: 16 }}
          />
        </Card>
      </div>
    )
  }

  return (
    <div style={{ padding: 24 }}>
      <Card>
        {/* 筛选区域（统一搜索，不重复搜索框） */}
        <Space direction="vertical" style={{ width: '100%', marginBottom: 16 }}>
          <Space wrap>
            <Input
              placeholder="用户名"
              value={filters.username}
              onChange={(e) => setFilters({ ...filters, username: e.target.value })}
              style={{ width: 200 }}
              allowClear
            />
            <Select
              placeholder="角色"
              value={filters.role}
              onChange={(role) => setFilters({ ...filters, role })}
              style={{ width: 150 }}
              allowClear
            >
              <Option value="SUPER_ADMIN">超级管理员</Option>
              <Option value="MANAGER">部门经理</Option>
              <Option value="EMPLOYEE">普通员工</Option>
            </Select>
            <Select
              placeholder="状态"
              value={filters.enabled}
              onChange={(enabled) => setFilters({ ...filters, enabled })}
              style={{ width: 120 }}
              allowClear
            >
              <Option value={true}>启用</Option>
              <Option value={false}>禁用</Option>
            </Select>
            <Button type="primary" icon={<SearchOutlined />} onClick={handleSearch}>
              搜索
            </Button>
            <Button icon={<ReloadOutlined />} onClick={handleReset}>
              重置
            </Button>
            <Button type="primary" icon={<PlusOutlined />} onClick={() => setCreateModalVisible(true)}>
              新建用户
            </Button>
            <Button type="default" icon={<InfoCircleOutlined />} onClick={openResignationModal}>
              处理员工离职
            </Button>
          </Space>

          <Collapse
            ghost
            activeKey={advancedSearchVisible ? ['advanced'] : []}
            onChange={(keys) => setAdvancedSearchVisible(keys.includes('advanced'))}
          >
            <Panel
              header={<span style={{ fontSize: 12, color: '#666' }}>高级搜索：按员工信息查询</span>}
              key="advanced"
            >
              <Space wrap>
                <Input
                  placeholder="员工姓名（支持中文）"
                  value={filters.employeeName}
                  onChange={(e) =>
                    setFilters({ ...filters, employeeName: e.target.value, employeeId: undefined })
                  }
                  style={{ width: 200 }}
                  allowClear
                />
                <Input
                  type="number"
                  placeholder="员工ID（纯数字）"
                  value={filters.employeeId || ''}
                  onChange={(e) => {
                    const value = e.target.value ? Number(e.target.value) : undefined
                    setFilters({ ...filters, employeeId: value, employeeName: '' })
                  }}
                  style={{ width: 200 }}
                  allowClear
                />
                <Tooltip title="输入员工姓名或员工ID进行查询，支持模糊匹配">
                  <InfoCircleOutlined style={{ color: '#999' }} />
                </Tooltip>
              </Space>
            </Panel>
          </Collapse>
        </Space>

        <Table
          columns={columns}
          dataSource={users}
          rowKey="userId"
          loading={loading}
          pagination={{
            ...pagination,
            showTotal: (total) => `共 ${total} 条`,
            showSizeChanger: true,
            onChange: (page, pageSize) => setPagination({ ...pagination, current: page, pageSize })
          }}
          scroll={{ x: 1600 }}
        />
      </Card>

      {/* 创建用户 */}
      <Modal
        title="创建用户"
        open={createModalVisible}
        onCancel={() => {
          setCreateModalVisible(false)
          createForm.resetFields()
        }}
        onOk={() => createForm.submit()}
        width={600}
      >
        <Form form={createForm} onFinish={handleCreate} layout="vertical">
          <Form.Item
            label="用户名"
            name="username"
            rules={[
              { required: true, message: '请输入用户名' },
              {
                pattern: /^[a-zA-Z0-9_]{3,20}$/,
                message: '用户名只能包含字母、数字、下划线，长度3-20'
              }
            ]}
          >
            <Input placeholder="请输入用户名" />
          </Form.Item>
          <Form.Item label="邮箱" name="email" rules={[{ type: 'email', message: '邮箱格式不正确' }]}>
            <Input placeholder="请输入邮箱" />
          </Form.Item>
          <Form.Item label="角色" name="role" rules={[{ required: true, message: '请选择角色' }]}>
            <Select placeholder="请选择角色">
              <Option value="SUPER_ADMIN">超级管理员</Option>
              <Option value="MANAGER">部门经理</Option>
              <Option value="EMPLOYEE">普通员工</Option>
            </Select>
          </Form.Item>
          <Form.Item noStyle shouldUpdate={(prev, curr) => prev.role !== curr.role}>
            {({ getFieldValue }) =>
              getFieldValue('role') === 'MANAGER' && (
                <Form.Item
                  label="部门"
                  name="department"
                  rules={[{ required: true, message: '部门经理必须指定部门' }]}
                >
                  <Input placeholder="请输入部门" />
                </Form.Item>
              )
            }
          </Form.Item>
          <Form.Item noStyle shouldUpdate={(prev, curr) => prev.role !== curr.role}>
            {({ getFieldValue }) =>
              getFieldValue('role') === 'EMPLOYEE' && (
                <Form.Item
                  label="关联员工"
                  name="employeeId"
                  tooltip="选择要关联的员工，创建后会自动加载员工信息"
                >
                  <Select
                    placeholder="请选择员工"
                    showSearch
                    optionFilterProp="label"
                    loading={employeeLoading}
                    filterOption={(input, option) =>
                      (option?.label ?? '').toLowerCase().includes(input.toLowerCase())
                    }
                  >
                    {employeeList.map((emp) => (
                      <Option key={emp.id} value={emp.id} label={`${emp.name} (${emp.department})`}>
                        {emp.name} - {emp.department} - {emp.position}
                      </Option>
                    ))}
                  </Select>
                </Form.Item>
              )
            }
          </Form.Item>
          <p style={{ color: '#999', fontSize: 12 }}>默认密码为：123456</p>
        </Form>
      </Modal>

  {/* 编辑用户 */}
      <Modal
        title="编辑用户"
        open={editModalVisible}
        onCancel={() => {
          setEditModalVisible(false)
          editForm.resetFields()
        }}
        onOk={() => editForm.submit()}
      >
        <Form form={editForm} onFinish={handleUpdate} layout="vertical">
          <Form.Item label="邮箱" name="email" rules={[{ type: 'email', message: '邮箱格式不正确' }]}>
            <Input placeholder="请输入邮箱" />
          </Form.Item>
          <Form.Item label="部门" name="department">
            <Input placeholder="请输入部门" />
          </Form.Item>
          <Form.Item label="关联员工ID" name="employeeId">
            <Input type="number" placeholder="请输入员工ID" />
          </Form.Item>
        </Form>
      </Modal>

      {/* 分配角色 */}
      <Modal
        title="分配角色"
        open={roleModalVisible}
        onCancel={() => {
          setRoleModalVisible(false)
          roleForm.resetFields()
        }}
        onOk={() => roleForm.submit()}
      >
        <Form form={roleForm} onFinish={handleAssignRole} layout="vertical">
          <Form.Item label="角色" name="role" rules={[{ required: true, message: '请选择角色' }]}>
            <Select placeholder="请选择角色">
              <Option value="SUPER_ADMIN">超级管理员</Option>
              <Option value="MANAGER">部门经理</Option>
              <Option value="EMPLOYEE">普通员工</Option>
            </Select>
          </Form.Item>
          <Form.Item noStyle shouldUpdate={(prev, curr) => prev.role !== curr.role}>
            {({ getFieldValue }) =>
              getFieldValue('role') === 'MANAGER' && (
                <Form.Item
                  label="部门"
                  name="department"
                  rules={[{ required: true, message: '部门经理必须指定部门' }]}
                >
                  <Input placeholder="请输入部门" />
                </Form.Item>
              )
            }
          </Form.Item>
          <Form.Item noStyle shouldUpdate={(prev, curr) => prev.role !== curr.role}>
            {({ getFieldValue }) =>
              getFieldValue('role') === 'EMPLOYEE' && (
                <Form.Item label="关联员工ID" name="employeeId">
                  <Input type="number" placeholder="请输入员工ID" />
                </Form.Item>
              )
            }
          </Form.Item>
        </Form>
      </Modal>

      {/* 重置密码 */}
      <Modal
        title="重置密码"
        open={passwordModalVisible}
        onCancel={() => {
          setPasswordModalVisible(false)
          passwordForm.resetFields()
        }}
        onOk={() => passwordForm.submit()}
      >
        <Form form={passwordForm} onFinish={handleResetPassword} layout="vertical">
          <Form.Item
            label="新密码"
            name="newPassword"
            rules={[
              { required: true, message: '请输入新密码' },
              { min: 6, max: 20, message: '密码长度必须在6-20之间' }
            ]}
          >
            <Input.Password placeholder="请输入新密码" />
          </Form.Item>
        </Form>
      </Modal>

      {/* 处理员工离职 */}
      <Modal
        title="处理员工离职"
        open={resignationModalVisible}
        onCancel={() => {
          setResignationModalVisible(false)
          resignationForm.resetFields()
        }}
        onOk={() => resignationForm.submit()}
      >
        <Form form={resignationForm} onFinish={handleResignation} layout="vertical">
          <Form.Item
            label="员工ID"
            name="employeeId"
            rules={[
              { required: true, message: '请输入员工ID' },
              // 使用 InputNumber，本身就是数字，这里不用再做 type 校验
            ]}
            tooltip="输入离职员工的ID，系统将禁用关联的所有用户账号"
          >
            <InputNumber
              min={1}
              style={{ width: '100%' }}
              placeholder="请输入员工ID"
            />
          </Form.Item>
          <Alert
            message="提示"
            description="此操作将禁用该员工关联的所有用户账号，请谨慎操作。"
            type="warning"
            showIcon
            style={{ marginTop: 16 }}
          />
        </Form>
      </Modal>
    </div>
  )
}

export default UserManagement