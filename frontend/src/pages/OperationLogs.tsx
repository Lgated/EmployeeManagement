import React, { useState, useEffect } from 'react'
import { Table, Card, Select, Space, Tag } from 'antd'
import { getOperationLogs } from '../api/log'
import type { OperationLog } from '../types/log'

const { Option } = Select

const OperationLogs: React.FC = () => {
  const [logs, setLogs] = useState<OperationLog[]>([])
  const [loading, setLoading] = useState(false)
  const [pagination, setPagination] = useState({
    current: 1,
    pageSize: 10,
    total: 0,
  })
  const [filters, setFilters] = useState({
    module: '',
    operationType: '',
  })

  useEffect(() => {
    loadLogs()
  }, [pagination.current, filters])

  const loadLogs = async () => {
    setLoading(true)
    try {
      const res = await getOperationLogs(
        filters.module,
        filters.operationType,
        pagination.current,
        pagination.pageSize
      )
      setLogs(res.records)  // 后端返回 records，不是 content
      setPagination({
        ...pagination,
        total: res.total,  // 后端返回 total，不是 totalElements
      })
    } catch (error) {
      console.error('加载日志失败', error)
    } finally {
      setLoading(false)
    }
  }

  const columns = [
    {
      title: 'ID',
      dataIndex: 'id',
      key: 'id',
      width: 80,
    },
    {
      title: '操作人',
      dataIndex: 'username',
      key: 'username',
    },
    {
      title: '模块',
      dataIndex: 'module',
      key: 'module',
    },
    {
      title: '操作类型',
      dataIndex: 'operationType',
      key: 'operationType',
      render: (type: string) => {
        const colorMap: any = {
          CREATE: 'green',
          UPDATE: 'blue',
          DELETE: 'red',
          QUERY: 'default',
        }
        return <Tag color={colorMap[type] || 'default'}>{type}</Tag>
      },
    },
    {
      title: '描述',
      dataIndex: 'description',
      key: 'description',
    },
    {
      title: 'IP地址',
      dataIndex: 'ipAddress',
      key: 'ipAddress',
    },
    {
      title: '耗时',
      dataIndex: 'executionTime',
      key: 'executionTime',
      render: (time: number) => `${time}ms`,
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      render: (status: string) => (
        <Tag color={status === 'SUCCESS' ? 'success' : 'error'}>
          {status}
        </Tag>
      ),
    },
    {
      title: '操作时间',
      dataIndex: 'createdAt',
      key: 'createdAt',
    },
  ]

  return (
    <Card
      title="操作日志"
      extra={
        <Space>
          <Select
            placeholder="选择模块"
            style={{ width: 120 }}
            allowClear
            onChange={(value) => setFilters({ ...filters, module: value || '' })}
          >
            <Option value="EMPLOYEE">员工管理</Option>
            <Option value="USER">用户管理</Option>
            <Option value="SYSTEM">系统管理</Option>
          </Select>
          <Select
            placeholder="操作类型"
            style={{ width: 120 }}
            allowClear
            onChange={(value) => setFilters({ ...filters, operationType: value || '' })}
          >
            <Option value="CREATE">新增</Option>
            <Option value="UPDATE">修改</Option>
            <Option value="DELETE">删除</Option>
            <Option value="QUERY">查询</Option>
          </Select>
        </Space>
      }
    >
      <Table
        columns={columns}
        dataSource={logs}
        rowKey="id"
        loading={loading}
        pagination={{
          ...pagination,
          onChange: (page) => setPagination({ ...pagination, current: page }),
        }}
      />
    </Card>
  )
}

export default OperationLogs
