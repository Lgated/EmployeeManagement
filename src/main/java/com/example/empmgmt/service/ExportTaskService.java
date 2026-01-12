package com.example.empmgmt.service;

import com.example.empmgmt.domain.Employee;
import com.example.empmgmt.domain.ExportTask;
import com.example.empmgmt.mq.dto.EmployeeExportParams;

import java.util.List;

/**
 * 导出任务服务接口
 */
public interface ExportTaskService {

    // 创建员工导出任务
    Long createEmployeeExportTask(EmployeeExportParams params, Long userId);

    // 发消息
    ExportTask getTask(Long taskId);

    // 根据任务ID获取需要导出的员工列表
    List<Employee> getEmployeesForExportTask(Long taskId);
}
