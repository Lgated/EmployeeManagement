package com.example.empmgmt.mq.dto;

import lombok.Data;

/**
 * 导出任务消息对象
 */
@Data
public class ExportTaskMessage {
    private Long taskId;        // 任务ID（数据库主键）
    private String taskType;    // EMPLOYEE_EXPORT / USER_EXPORT
    private String paramsJson;  // 与表里的 params 一致
}
