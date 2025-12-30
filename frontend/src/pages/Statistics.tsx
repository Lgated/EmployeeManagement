/**
 * 统计页面
 * 显示各部门员工数量统计和平均薪资统计
 */
import { useState, useEffect } from 'react'
import { Card, Table, Statistic, Row, Col, message, Spin } from 'antd'
import { TeamOutlined, DollarOutlined } from '@ant-design/icons'
import {
  getDeptCountStats,
  getDeptAvgSalaryStats,
} from '../api/employee'
import type { DeptStats } from '../types'
import type { ColumnsType } from 'antd/es/table'

const Statistics = () => {
  const [deptCountData, setDeptCountData] = useState<DeptStats[]>([])
  const [deptSalaryData, setDeptSalaryData] = useState<DeptStats[]>([])
  const [loading, setLoading] = useState(false)

  /**
   * 加载统计数据
   */
  const loadStatistics = async () => {
    try {
      setLoading(true)
      
      // 并行加载两个统计数据
      const [countData, salaryData] = await Promise.all([
        getDeptCountStats(),
        getDeptAvgSalaryStats(),
      ])
      
      setDeptCountData(countData)
      setDeptSalaryData(salaryData)
    } catch (error: any) {
      message.error('加载统计数据失败: ' + (error.message || '未知错误'))
    } finally {
      setLoading(false)
    }
  }

  // 组件挂载时加载数据
  useEffect(() => {
    loadStatistics()
  }, [])

  // 计算总员工数和平均薪资
  const totalEmployees = deptCountData.reduce((sum, item) => sum + (item.empCount || 0), 0)
  const avgSalary = deptSalaryData.length > 0
    ? deptSalaryData.reduce((sum, item) => sum + (item.avgSalary || 0), 0) / deptSalaryData.length
    : 0

  // 部门人数统计表格列
  const countColumns: ColumnsType<DeptStats> = [
    {
      title: '部门',
      dataIndex: 'department',
      key: 'department',
    },
    {
      title: '员工数量',
      dataIndex: 'empCount',
      key: 'empCount',
      render: (count: number) => count?.toLocaleString() || 0,
    },
  ]

  // 部门平均薪资统计表格列
  const salaryColumns: ColumnsType<DeptStats> = [
    {
      title: '部门',
      dataIndex: 'department',
      key: 'department',
    },
    {
      title: '平均薪资',
      dataIndex: 'avgSalary',
      key: 'avgSalary',
      render: (salary: number) => salary ? `¥${salary.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}` : '-',
    },
  ]

  return (
    <Spin spinning={loading}>
      <div>
        {/* 统计卡片 */}
        <Row gutter={16} style={{ marginBottom: 24 }}>
          <Col span={12}>
            <Card>
              <Statistic
                title="总员工数"
                value={totalEmployees}
                prefix={<TeamOutlined />}
                valueStyle={{ color: '#3f8600' }}
              />
            </Card>
          </Col>
          <Col span={12}>
            <Card>
              <Statistic
                title="平均薪资"
                value={avgSalary}
                prefix={<DollarOutlined />}
                precision={2}
                suffix="元"
                valueStyle={{ color: '#cf1322' }}
              />
            </Card>
          </Col>
        </Row>

        {/* 部门人数统计 */}
        <Card
          title="各部门员工数量统计"
          style={{ marginBottom: 24 }}
        >
          <Table
            columns={countColumns}
            dataSource={deptCountData}
            rowKey="department"
            pagination={false}
          />
        </Card>

        {/* 部门平均薪资统计 */}
        <Card title="各部门平均薪资统计">
          <Table
            columns={salaryColumns}
            dataSource={deptSalaryData}
            rowKey="department"
            pagination={false}
          />
        </Card>
      </div>
    </Spin>
  )
}

export default Statistics







