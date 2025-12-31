package com.example.empmgmt.dto.response;

import com.example.empmgmt.domain.Employee;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

//员工响应
public record EmployeeResponse(
        Long id,
        String name,
        String gender,
        Integer age,
        String department,
        String position,
        LocalDate hireDate,
        BigDecimal salary,
        String avatar,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    // 从实体转换为响应 DTO
    public static EmployeeResponse from(Employee employee) {
        return new EmployeeResponse(
                employee.getId(),
                employee.getName(),
                employee.getGender(),
                employee.getAge(),
                employee.getDepartment(),
                employee.getPosition(),
                employee.getHireDate(),
                employee.getSalary(),
                employee.getAvatar(),
                employee.getCreatedAt(),
                employee.getUpdatedAt()
        );
    }
}
