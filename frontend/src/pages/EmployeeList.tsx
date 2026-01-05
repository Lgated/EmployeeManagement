/**
 * 员工列表页面
 * 显示员工列表，支持搜索、新增、编辑、删除操作
 */
import { useState, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  Table,
  Button,
  Input,
  Space,
  Popconfirm,
  message,
  Card,
  Select,
} from 'antd'
import {
  PlusOutlined,
  EditOutlined,
  DeleteOutlined,
  SearchOutlined,
} from '@ant-design/icons'
import type { ColumnsType } from 'antd/es/table'
import {
  getEmployeeList,
  deleteEmployee,
} from '../api/employee'
import type { Employee } from '../types'
import dayjs from 'dayjs'
import { useAuthStore } from '../stores/authStore'

const { Search } = Input
const { Option } = Select

const EmployeeList = () => {
  const navigate = useNavigate()
  const [employees, setEmployees] = useState<Employee[]>([])
  const [loading, setLoading] = useState(false)
  const [searchName, setSearchName] = useState('')
  const [searchDepartment, setSearchDepartment] = useState<string>()
  const [page, setPage] = useState(1)
  const [pageSize, setPageSize] = useState(10)
  const [total, setTotal] = useState(0)
  const { role } = useAuthStore()
  const canManageEmployee = role === 'SUPER_ADMIN' || role === 'MANAGER'


  /**
   * 加载员工列表
   */
  const loadEmployees = async () => {
    try {
      setLoading(true)
      const res = await getEmployeeList(searchName, searchDepartment, page, pageSize)
      // res: PageResult<Employee>
      setEmployees(res.records)
      setTotal(res.total)
    } catch (error: any) {
      message.error('加载员工列表失败: ' + (error.message || '未知错误'))
    } finally {
      setLoading(false)
    }
  }

  // 组件挂载/查询条件变化时调用
  useEffect(() => {
    loadEmployees()
  }, [page, pageSize])   // 搜索点击时也会调用（见下）

  // 处理搜索 :搜索按钮点击时：重置到第一页
  const handleSearch = () => {
    setPage(1)
    loadEmployees()
  }

  /**
   * 处理删除员工
   */
  const handleDelete = async (id: number) => {
    try {
      await deleteEmployee(id)
      message.success('删除成功')
      // 重新加载列表
      loadEmployees()
    } catch (error: any) {
      message.error('删除失败: ' + (error.message || '未知错误'))
    }
  }


  /**
   * 获取所有部门列表（用于下拉选择）
   */
  const departments = Array.from(new Set(employees.map(emp => emp.department)))

  // 表格列定义
  const columns: ColumnsType<Employee> = [
    {
      title: 'ID',
      dataIndex: 'id',
      key: 'id',
      width: 80,
    },
    // 在 name 列之前添加
    {
      title: '头像',
      dataIndex: 'avatar',
      key: 'avatar',
      width: 80,
      render: (avatar: string) => (
        avatar ? (
          <img
            src={avatar.startsWith('http') ? avatar : `http://localhost:8080${avatar}`}
            alt="avatar"
            style={{ width: 50, height: 50, borderRadius: '50%', objectFit: 'cover' }}
          />
        ) : (
          <div style={{
            width: 50,
            height: 50,
            borderRadius: '50%',
            background: '#f0f0f0',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            color: '#999'
          }}>
            暂无
          </div>
        )
      ),
    },
    {
      title: '姓名',
      dataIndex: 'name',
      key: 'name',
      width: 120,
    },
    {
      title: '性别',
      dataIndex: 'gender',
      key: 'gender',
      width: 80,
      render: (gender: string) => {
        const genderMap: Record<string, string> = {
          M: '男',
          F: '女',
          '男': '男',
          '女': '女',
        }
        return genderMap[gender] || gender || '-'
      },
    },
    {
      title: '年龄',
      dataIndex: 'age',
      key: 'age',
      width: 80,
    },
    {
      title: '部门',
      dataIndex: 'department',
      key: 'department',
      width: 150,
    },
    {
      title: '职位',
      dataIndex: 'position',
      key: 'position',
      width: 150,
    },
    {
      title: '入职日期',
      dataIndex: 'hireDate',
      key: 'hireDate',
      width: 120,
      render: (date: string) => dayjs(date).format('YYYY-MM-DD'),
    },
    {
      title: '薪资',
      dataIndex: 'salary',
      key: 'salary',
      width: 120,
      render: (salary: number) => `¥${salary.toLocaleString()}`,
    },
    {
      title: '操作',
      key: 'action',
      width: 150,
      fixed: 'right',
      render: (_: any, record: Employee) => (
        <Space size="middle">
          <Button
            type="link"
            icon={<EditOutlined />}
            onClick={() => navigate(`/employees/${record.id}/edit`)}
          >
            编辑
          </Button>
          {canManageEmployee && (
            <Popconfirm
              title="确定要删除这名员工吗？"
              onConfirm={() => handleDelete(record.id)}
              okText="确定"
              cancelText="取消"
            >
              <Button type="link" danger icon={<DeleteOutlined />}>
                删除
              </Button>
            </Popconfirm>
          )}
        </Space>
      ),
    },
  ]

  return (
    <Card
      title="员工管理"
      extra={
        canManageEmployee && (
          <Button
            type="primary"
            icon={<PlusOutlined />}
            onClick={() => navigate('/employees/new')}
          >
            新增员工
          </Button>
        )
      }
    >
      {/* 搜索栏 */}
      <Space style={{ marginBottom: 16 }} wrap>
        <Search
          placeholder="按姓名搜索"
          allowClear
          style={{ width: 200 }}
          value={searchName}
          onChange={(e) => setSearchName(e.target.value)}
          onSearch={handleSearch}
          enterButton={<SearchOutlined />}
        />
        <Select
          placeholder="选择部门"
          allowClear
          style={{ width: 200 }}
          value={searchDepartment}
          onChange={(value) => {
            setSearchDepartment(value)
            // 选择部门后自动搜索
            setTimeout(() => {
              setSearchDepartment(value)
              loadEmployees()
            }, 0)
          }}
        >
          {departments.map((dept) => (
            <Option key={dept} value={dept}>
              {dept}
            </Option>
          ))}
        </Select>
        <Button onClick={handleSearch}>搜索</Button>
        <Button onClick={() => {
          setSearchName('')
          setSearchDepartment(undefined)
          loadEmployees()
        }}>
          重置
        </Button>
      </Space>

      {/* 员工表格 */}
      <Table
        columns={columns}
        dataSource={employees}
        rowKey="id"
        loading={loading}
        pagination={{
          current: page,
          pageSize: pageSize,
          total: total,
          showSizeChanger: true,
          showTotal: (total) => `共 ${total} 条记录`,
          onChange: (newPage, newPageSize) => {
            setPage(newPage)
            setPageSize(newPageSize)
            // 不需要手动调用 loadEmployees，依赖 useEffect 触发
          },
        }}
      />
    </Card>
  )
}

export default EmployeeList










