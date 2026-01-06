package com.example.empmgmt.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 用户信息（包含员工详细信息）响应DTO
 * 用于场景：用户管理页面显示员工信息
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserWithEmployeeDTO {
    // 用户信息
    private Long userId;
    private String username;
    private String email;
    private String role;
    private String userDepartment;        // 用户的部门字段（MANAGER使用）
    private Boolean enabled;
    private LocalDateTime userCreatedAt;

    // 员工信息（如果有关联）
    private Long employeeId;
    private String employeeName;
    private String employeeDepartment;   // 员工的部门字段
    private String employeePosition;
    private Integer employeeAge;
    private String employeeGender;
    private String employeeAvatar;

    /**
     * 从User实体转换为DTO（包含员工信息）
     */
    public static UserWithEmployeeDTO fromEntity(com.example.empmgmt.domain.User user) {
        UserWithEmployeeDTO dto = new UserWithEmployeeDTO();

        // 填充用户信息
        dto.setUserId(user.getId());
        dto.setUsername(user.getUsername());
        dto.setEmail(user.getEmail());
        dto.setRole(user.getRole());
        dto.setUserDepartment(user.getDepartment());
        dto.setEnabled(user.getEnabled());
        dto.setUserCreatedAt(user.getCreatedAt());

        // 填充员工信息（如果有关联）
        if (user.getEmployee() != null) {
            com.example.empmgmt.domain.Employee emp = user.getEmployee();
            dto.setEmployeeId(emp.getId());
            dto.setEmployeeName(emp.getName());
            dto.setEmployeeDepartment(emp.getDepartment());
            dto.setEmployeePosition(emp.getPosition());
            dto.setEmployeeAge(emp.getAge());
            dto.setEmployeeGender(emp.getGender());
            dto.setEmployeeAvatar(emp.getAvatar());
        }

        return dto;
    }
}
