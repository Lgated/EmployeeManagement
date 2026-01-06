package com.example.empmgmt.dto.response;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户响应DTO
 */
@Data
public class UserResponse {

    private Long id;
    private String username;
    private String email;
    private String role;           // SUPER_ADMIN/MANAGER/EMPLOYEE
    private String department;     // 部门
    private Long employeeId;       // 关联的员工ID
    private Boolean enabled;       // 是否启用
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private String employeeName;        // 员工姓名
    private String employeeDepartment;  // 员工部门
    private String employeePosition;    // 员工职位
    /**
     * 从User实体转换为UserResponse
     */
    /**
     * 从User实体转换为UserResponse
     */
    public static UserResponse fromEntity(com.example.empmgmt.domain.User user) {
        UserResponse response = new UserResponse();
        response.setId(user.getId());
        response.setUsername(user.getUsername());
        response.setEmail(user.getEmail());
        response.setRole(user.getRole());
        response.setDepartment(user.getDepartment());
        response.setEmployeeId(user.getEmployeeId());
        response.setEnabled(user.getEnabled());
        response.setCreatedAt(user.getCreatedAt());
        response.setUpdatedAt(user.getUpdatedAt());

        // ========== 新增：填充员工信息 ==========
        if (user.getEmployee() != null) {
            com.example.empmgmt.domain.Employee emp = user.getEmployee();
            response.setEmployeeName(emp.getName());
            response.setEmployeeDepartment(emp.getDepartment());
            response.setEmployeePosition(emp.getPosition());
        }

        return response;
    }


}
