package com.example.empmgmt.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 创建用户请求DTO
 */
@Data
public class UserCreateRequest {

    @NotBlank(message = "用户名不能为空")
    @Pattern(regexp = "^[a-zA-Z0-9_]{3,20}$",
            message = "用户名只能包含字母、数字、下划线，长度3-20")
    private String username;

    @Email(message = "邮箱格式不正确")
    private String email;

    @NotBlank(message = "角色不能为空")
    @Pattern(regexp = "^(SUPER_ADMIN|MANAGER|EMPLOYEE)$",
            message = "角色只能是 SUPER_ADMIN/MANAGER/EMPLOYEE")
    private String role;

    private String department;    // MANAGER角色必填

    private Long employeeId;      // EMPLOYEE角色可选

}
