package com.example.empmgmt.mq.dto;

import lombok.Data;

/**
 *  导出任务参数对象
 */
@Data
public class EmployeeExportParams {

    private String department;
    private String position;

}
