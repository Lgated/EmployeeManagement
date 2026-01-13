package com.example.empmgmt.mq.dto;

import lombok.Data;

/**
 *  用户导出任务参数对象
 */
@Data
public class UserExportParams {

    private String role;
    private String Department;

}
