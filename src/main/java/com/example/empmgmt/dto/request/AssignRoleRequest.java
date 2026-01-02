package com.example.empmgmt.dto.request;


import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 分配角色请求DTO
 */
@Data
public class AssignRoleRequest {

    @NotBlank(message = "角色不能为空")
    @Pattern(regexp = "^(SUPER_ADMIN|MANAGER|EMPLOYEE)$",
            message = "角色只能是 SUPER_ADMIN/MANAGER/EMPLOYEE")
    private String role;

    private String department;    // MANAGER角色必填

    private Long employeeId;      // EMPLOYEE角色可选
}
