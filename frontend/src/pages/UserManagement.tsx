import React, { useEffect, useState } from 'react'
import {
    Table,
    Button,
    Space,
    Tag,
    Modal,
    Form,
    Input,
    Select,
    Switch,
    message,
    Popconfirm,
    Card
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import {
    PlusOutlined,
    EditOutlined,
    DeleteOutlined,
    KeyOutlined,
    SearchOutlined,
    ReloadOutlined,
    DownloadOutlined
} from '@ant-design/icons'
import {

    getUserListWithEmployee,        // 新增：带员工信息的列表
    getUserByEmployeeName,           // 新增：按员工姓名查询
    getUsersByEmployeeId,            // 新增：按员工ID查询
    createUserWithEmployee,          // 新增：创建用户并加载员工信息
    handleEmployeeResignation,       // 新增：处理员工离职
    getUserList,
    createUser,
    updateUser,
    updateUserStatus,
    assignRole,
    deleteUser,
    resetPassword
} from '../api/user'
import type {
    User,
    UserWithEmployee,      // 新增：包含员工信息的用户类型
    UserCreateRequest,
    UserUpdateRequest,
    AssignRoleRequest,
    ResetPasswordRequest,
    Role
} from '../types/user'
import { RoleNames } from '../types/user'
import { getEmployeeList } from '../api/employee'  // 用于选择员工
import { useAuthStore } from '../stores/authStore'
import { exportUsers } from '../api/user'

const { Option } = Select

const UserManagement: React.FC = () => {
    const { role } = useAuthStore()
    const isSuperAdmin = role === 'SUPER_ADMIN'
    const isManager = role === 'MANAGER'
    const [users, setUsers] = useState<User[]>([])
    const [loading, setLoading] = useState(false)
    const [pagination, setPagination] = useState({
        current: 1,
        pageSize: 10,
        total: 0
    })

    // 筛选条件
    const [filters, setFilters] = useState({
        username: '',
        role: undefined as string | undefined,
        enabled: undefined as boolean | undefined
    })

    // 模态框状态
    const [createModalVisible, setCreateModalVisible] = useState(false)
    const [editModalVisible, setEditModalVisible] = useState(false)
    const [roleModalVisible, setRoleModalVisible] = useState(false)
    const [passwordModalVisible, setPasswordModalVisible] = useState(false)
    const [currentUser, setCurrentUser] = useState<User | null>(null)

    // 表单实例
    const [createForm] = Form.useForm()
    const [editForm] = Form.useForm()
    const [roleForm] = Form.useForm()
    const [passwordForm] = Form.useForm()

    const handleExport = async () => {
        try {
            // 使用当前的筛选条件
            const role = filters.role || undefined
            // 注意：UserManagement页面目前没有department筛选，所以传undefined
            const department = undefined

            await exportUsers(role, department)
            message.success('导出成功')
        } catch (error: any) {
            console.error('导出失败:', error)
            message.error('导出失败: ' + (error.message || '未知错误'))
        }
    }

    // 加载用户列表
    const loadUsers = async () => {
        setLoading(true)
        try {
            const res = await getUserList({
                ...filters,
                page: pagination.current,
                size: pagination.pageSize
            })
            setUsers(res.records)
            setPagination({
                ...pagination,
                total: res.total
            })
        } catch (error) {
            message.error('加载失败')
        } finally {
            setLoading(false)
        }
    }

    useEffect(() => {
        loadUsers()
    }, [pagination.current, pagination.pageSize])

    // 搜索
    const handleSearch = () => {
        setPagination({ ...pagination, current: 1 })
        loadUsers()
    }

    // 重置
    const handleReset = () => {
        setFilters({
            username: '',
            role: undefined,
            enabled: undefined
        })
        setPagination({ ...pagination, current: 1 })
    }

    // 创建用户
    const handleCreate = async (values: UserCreateRequest) => {
        try {
            await createUser(values)
            message.success('创建成功')
            setCreateModalVisible(false)
            createForm.resetFields()
            loadUsers()
        } catch (error) {
            message.error('创建失败')
        }
    }

    // 更新用户
    const handleUpdate = async (values: UserUpdateRequest) => {
        if (!currentUser) return
        try {
            await updateUser(currentUser.id, values)
            message.success('更新成功')
            setEditModalVisible(false)
            editForm.resetFields()
            loadUsers()
        } catch (error) {
            message.error('更新失败')
        }
    }

    // 分配角色
    const handleAssignRole = async (values: AssignRoleRequest) => {
        if (!currentUser) return
        try {
            await assignRole(currentUser.id, values)
            message.success('角色分配成功')
            setRoleModalVisible(false)
            roleForm.resetFields()
            loadUsers()
        } catch (error) {
            message.error('角色分配失败')
        }
    }

    // 重置密码
    const handleResetPassword = async (values: ResetPasswordRequest) => {
        if (!currentUser) return
        try {
            await resetPassword(currentUser.id, values)
            message.success('密码重置成功')
            setPasswordModalVisible(false)
            passwordForm.resetFields()
        } catch (error) {
            message.error('密码重置失败')
        }
    }

    // 切换用户状态
    const handleToggleStatus = async (user: User) => {
        try {
            await updateUserStatus(user.id, !user.enabled)
            message.success(user.enabled ? '已禁用' : '已启用')
            loadUsers()
        } catch (error) {
            message.error('操作失败')
        }
    }

    // 删除用户
    const handleDelete = async (id: number) => {
        try {
            await deleteUser(id)
            message.success('删除成功')
            loadUsers()
        } catch (error) {
            message.error('删除失败')
        }
    }

    // 打开编辑模态框
    const openEditModal = (user: User) => {
        setCurrentUser(user)
        editForm.setFieldsValue({
            email: user.email,
            department: user.department,
            employeeId: user.employeeId
        })
        setEditModalVisible(true)
    }

    // 打开角色分配模态框
    const openRoleModal = (user: User) => {
        setCurrentUser(user)
        roleForm.setFieldsValue({
            role: user.role,
            department: user.department,
            employeeId: user.employeeId
        })
        setRoleModalVisible(true)
    }

    // 打开密码重置模态框
    const openPasswordModal = (user: User) => {
        setCurrentUser(user)
        passwordForm.resetFields()
        setPasswordModalVisible(true)
    }

    // 表格列定义
    const columns: ColumnsType<User> = [
        {
            title: 'ID',
            dataIndex: 'id',
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
                const color = {
                    SUPER_ADMIN: 'red',
                    MANAGER: 'blue',
                    EMPLOYEE: 'green'
                }[role]
                return <Tag color={color}>{RoleNames[role]}</Tag>
            }
        },
        {
            title: '部门',
            dataIndex: 'department',
            width: 120
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
            dataIndex: 'createdAt',
            width: 180,
            render: (date: string) => new Date(date).toLocaleString('zh-CN')
        },
        {
            title: '操作',
            key: 'action',
            width: 300,
            fixed: 'right',
            render: (_, record) => (
                <Space>
                    <Button
                        type="link"
                        icon={<EditOutlined />}
                        onClick={() => openEditModal(record)}
                    >
                        编辑
                    </Button>
                    <Button
                        type="link"
                        onClick={() => openRoleModal(record)}
                    >
                        分配角色
                    </Button>
                    <Button
                        type="link"
                        icon={<KeyOutlined />}
                        onClick={() => openPasswordModal(record)}
                    >
                        重置密码
                    </Button>
                    {record.role !== 'SUPER_ADMIN' && (
                        <Popconfirm
                            title="确定删除此用户？"
                            onConfirm={() => handleDelete(record.id)}
                        >
                            <Button type="link" danger icon={<DeleteOutlined />}>
                                删除
                            </Button>
                        </Popconfirm>
                    )}
                </Space>
            )
        }
    ]

    return (
        <div style={{ padding: 24 }}>
            <Card>
                {/* 筛选区域 */}
                <Space style={{ marginBottom: 16 }}>
                    <Input
                        placeholder="用户名"
                        value={filters.username}
                        onChange={(e) => setFilters({ ...filters, username: e.target.value })}
                        style={{ width: 200 }}
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
                    {isSuperAdmin && (
                        <>

                            <Button
                                type="primary"
                                icon={<PlusOutlined />}
                                onClick={() => setCreateModalVisible(true)}
                            >
                                新建用户
                            </Button>

                            <Button
                                type="primary"
                                icon={<DownloadOutlined />}
                                onClick={handleExport}
                            >
                                导出Excel
                            </Button>
                        </>
                    )}
                </Space>

                {/* 表格 */}
                <Table
                    columns={columns}
                    dataSource={users}
                    rowKey="id"
                    loading={loading}
                    pagination={{
                        ...pagination,
                        showTotal: (total) => `共 ${total} 条`,
                        showSizeChanger: true,
                        onChange: (page, pageSize) => {
                            setPagination({ ...pagination, current: page, pageSize })
                        }
                    }}
                    scroll={{ x: 1400 }}
                />
            </Card>

            {/* 创建用户模态框 */}
            <Modal
                title="创建用户"
                open={createModalVisible}
                onCancel={() => {
                    setCreateModalVisible(false)
                    createForm.resetFields()
                }}
                onOk={() => createForm.submit()}
            >
                <Form form={createForm} onFinish={handleCreate} layout="vertical">
                    <Form.Item
                        label="用户名"
                        name="username"
                        rules={[
                            { required: true, message: '请输入用户名' },
                            { pattern: /^[a-zA-Z0-9_]{3,20}$/, message: '用户名只能包含字母、数字、下划线，长度3-20' }
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
                    <Form.Item
                        noStyle
                        shouldUpdate={(prev, curr) => prev.role !== curr.role}
                    >
                        {({ getFieldValue }) =>
                            getFieldValue('role') === 'MANAGER' && (
                                <Form.Item label="部门" name="department" rules={[{ required: true, message: '部门经理必须指定部门' }]}>
                                    <Input placeholder="请输入部门" />
                                </Form.Item>
                            )
                        }
                    </Form.Item>
                    <Form.Item
                        noStyle
                        shouldUpdate={(prev, curr) => prev.role !== curr.role}
                    >
                        {({ getFieldValue }) =>
                            getFieldValue('role') === 'EMPLOYEE' && (
                                <Form.Item label="关联员工ID" name="employeeId">
                                    <Input type="number" placeholder="请输入员工ID" />
                                </Form.Item>
                            )
                        }
                    </Form.Item>
                    <p style={{ color: '#999', fontSize: 12 }}>默认密码为：123456</p>
                </Form>
            </Modal>

            {/* 编辑用户模态框 */}
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

            {/* 分配角色模态框 */}
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
                    <Form.Item
                        noStyle
                        shouldUpdate={(prev, curr) => prev.role !== curr.role}
                    >
                        {({ getFieldValue }) =>
                            getFieldValue('role') === 'MANAGER' && (
                                <Form.Item label="部门" name="department" rules={[{ required: true, message: '部门经理必须指定部门' }]}>
                                    <Input placeholder="请输入部门" />
                                </Form.Item>
                            )
                        }
                    </Form.Item>
                    <Form.Item
                        noStyle
                        shouldUpdate={(prev, curr) => prev.role !== curr.role}
                    >
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

            {/* 重置密码模态框 */}
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
        </div>
    )
}

export default UserManagement