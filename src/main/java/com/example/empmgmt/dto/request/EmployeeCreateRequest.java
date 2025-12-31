package com.example.empmgmt.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;


// 创建员工请求
public record EmployeeCreateRequest(
        @NotBlank(message = "姓名不能为空")
        String name,
        String gender,

        @Positive(message = "年龄必须大于0")
        Integer age,

        @NotBlank(message = "部门不能为空")
        String department,
        String position,
        @NotNull(message = "入职日期不能为空")
        LocalDate hireDate,


        @NotNull(message = "薪资不能为空")
        @Positive(message = "薪资必须大于0")
        BigDecimal salary,
        
        String avatar
) { }
