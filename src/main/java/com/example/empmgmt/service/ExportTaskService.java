package com.example.empmgmt.service;

import com.example.empmgmt.domain.Employee;
import com.example.empmgmt.domain.ExportTask;
import com.example.empmgmt.mq.dto.EmployeeExportParams;
import com.example.empmgmt.mq.dto.UserExportParams;

import java.util.List;

/**
 * 导出任务服务接口
 */
public interface ExportTaskService {

    // 创建员工导出任务
    Long createEmployeeExportTask(EmployeeExportParams params, Long userId);

    Long createUserExportTask(UserExportParams params, Long userId);

    // 发消息
    ExportTask getTask(Long taskId);


}
