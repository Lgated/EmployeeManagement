/**
 * 员工表单页面
 * 用于新增和编辑员工信息
 */
import { useState, useEffect } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import {
  Form,
  Input,
  InputNumber,
  DatePicker,
  Button,
  Card,
  message,
  Select,
  Space
} from 'antd'
import { ArrowLeftOutlined } from '@ant-design/icons'
import dayjs, { Dayjs } from 'dayjs'
import {
  createEmployee,
  updateEmployee,
  getEmployeeById,
  getEmployeeYears,
} from '../api/employee'
import type { EmployeeCreateRequest, EmployeeUpdateRequest } from '../types'
import ImageUpload from '../components/ImageUpload'

const { Option } = Select

const EmployeeForm = () => {
  const navigate = useNavigate()
  const { id } = useParams<{ id: string }>()
  const [form] = Form.useForm()
  const [loading, setLoading] = useState(false)
  const [initialLoading, setInitialLoading] = useState(false)
  const isEdit = !!id
  const [years, setYears] = useState<number | null>(null)
  /**
   * 加载员工数据（编辑模式）
   */
  useEffect(() => {
    if (isEdit) {
      loadEmployee()
    }
  }, [id])

  const loadEmployee = async () => {
    try {
      setInitialLoading(true)
      const employee = await getEmployeeById(Number(id))
      
      // 设置表单初始值
      form.setFieldsValue({
        name: employee.name,
        gender: employee.gender,
        age: employee.age,
        department: employee.department,
        position: employee.position,
        hireDate: employee.hireDate ? dayjs(employee.hireDate) : null,
        salary: employee.salary,
        avatar: employee.avatar,
      })
       // 额外：加载在职时间
      const empYears = await getEmployeeYears(Number(id))
      setYears(empYears)
    } catch (error: any) {
      message.error('加载员工信息失败: ' + (error.message || '未知错误'))
      navigate('/employees')
    } finally {
      setInitialLoading(false)
    }
  }

  /**
   * 处理表单提交
   */
  const handleSubmit = async (values: any) => {
    try {
      setLoading(true)
      
      // 格式化数据
      const formData = {
        ...values,
        hireDate: values.hireDate ? values.hireDate.format('YYYY-MM-DD') : undefined,
      }

      if (isEdit) {
        // 更新员工
        await updateEmployee(Number(id), formData as EmployeeUpdateRequest)
        message.success('更新成功')
      } else {
        // 创建员工
        await createEmployee(formData as EmployeeCreateRequest)
        message.success('创建成功')
      }
      
      // 返回员工列表
      navigate('/employees')
    } catch (error: any) {
      message.error((isEdit ? '更新' : '创建') + '失败: ' + (error.message || '未知错误'))
    } finally {
      setLoading(false)
    }
  }

  return (
    <Card
      title={
        <div>
          <Button
            type="link"
            icon={<ArrowLeftOutlined />}
            onClick={() => navigate('/employees')}
            style={{ padding: 0, marginRight: 8 }}
          >
            返回
          </Button>
          {isEdit ? '编辑员工' : '新增员工'}
        </div>
      }
      loading={initialLoading}
    >
      {isEdit && years !== null && (
        <div style={{ marginBottom: 16, color: '#666' }}>
      在职时间：<strong>{years}</strong> 年
        </div>
      )}

      <Form
        form={form}
        layout="vertical"
        onFinish={handleSubmit}
        autoComplete="off"
      >
        <Form.Item
          label="头像"
          name="avatar"
        >
          <ImageUpload />
        </Form.Item>  
        
        <Form.Item
          label="姓名"
          name="name"
          rules={[{ required: true, message: '请输入姓名' }]}
        >
          <Input placeholder="请输入员工姓名" />
        </Form.Item>

        <Form.Item label="性别" name="gender">
          <Select placeholder="请选择性别" allowClear>
            <Option value="M">男</Option>
            <Option value="F">女</Option>
          </Select>
        </Form.Item>

        <Form.Item
          label="年龄"
          name="age"
          rules={[
            { type: 'number', min: 1, message: '年龄必须大于0' },
          ]}
        >
          <InputNumber
            placeholder="请输入年龄"
            style={{ width: '100%' }}
            min={1}
            max={150}
          />
        </Form.Item>

        <Form.Item
          label="部门"
          name="department"
          rules={[{ required: true, message: '请输入部门' }]}
        >
          <Input placeholder="请输入部门名称" />
        </Form.Item>

        <Form.Item label="职位" name="position">
          <Input placeholder="请输入职位" />
        </Form.Item>

        <Form.Item
          label="入职日期"
          name="hireDate"
          rules={[{ required: true, message: '请选择入职日期' }]}
        >
          <DatePicker
            style={{ width: '100%' }}
            format="YYYY-MM-DD"
            placeholder="请选择入职日期"
          />
        </Form.Item>

        <Form.Item
          label="薪资"
          name="salary"
          rules={[
            { required: true, message: '请输入薪资' },
            { type: 'number', min: 0, message: '薪资必须大于0' },
          ]}
        >
          <InputNumber
            placeholder="请输入薪资"
            style={{ width: '100%' }}
            min={0}
            step={100}
            formatter={(value) => `¥ ${value}`.replace(/\B(?=(\d{3})+(?!\d))/g, ',')}
            parser={(value) => value!.replace(/¥\s?|(,*)/g, '')}
          />
        </Form.Item>

        <Form.Item>
          <Space>
            <Button type="primary" htmlType="submit" loading={loading}>
              {isEdit ? '更新' : '创建'}
            </Button>
            <Button onClick={() => navigate('/employees')}>
              取消
            </Button>
          </Space>
        </Form.Item>
      </Form>
    </Card>
  )
}

export default EmployeeForm




























