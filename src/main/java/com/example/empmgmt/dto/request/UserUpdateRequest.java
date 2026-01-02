package com.example.empmgmt.dto.request;

import jakarta.validation.constraints.Email;
import lombok.Data;

/**
 * 更新用户请求DTO
 */
@Data
public class UserUpdateRequest {

    @Email(message = "邮箱格式不正确")
    private String email;

    private String department;    // 更新部门信息

    private Long employeeId;      // 更新关联员工ID

}
