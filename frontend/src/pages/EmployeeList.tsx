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

const { Search } = Input
const { Option } = Select

const EmployeeList = () => {
  const navigate = useNavigate()
  const [employees, setEmployees] = useState<Employee[]>([])
  const [loading, setLoading] = useState(false)
  const [searchName, setSearchName] = useState('')
  const [searchDepartment, setSearchDepartment] = useState<string>()

  /**
   * 加载员工列表
   */
  const loadEmployees = async () => {
    try {
      setLoading(true)
      const data = await getEmployeeList(searchName, searchDepartment)
      setEmployees(data)
    } catch (error: any) {
      message.error('加载员工列表失败: ' + (error.message || '未知错误'))
    } finally {
      setLoading(false)
    }
  }

  // 组件挂载时加载数据
  useEffect(() => {
    loadEmployees()
  }, [])

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
   * 处理搜索
   */
  const handleSearch = () => {
    loadEmployees()
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
        </Space>
      ),
    },
  ]

  return (
    <Card
      title="员工管理"
      extra={
        <Button
          type="primary"
          icon={<PlusOutlined />}
          onClick={() => navigate('/employees/new')}
        >
          新增员工
        </Button>
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
        scroll={{ x: 1200 }}
        pagination={{
          pageSize: 10,
          showSizeChanger: true,
          showTotal: (total) => `共 ${total} 条记录`,
        }}
      />
    </Card>
  )
}

export default EmployeeList







