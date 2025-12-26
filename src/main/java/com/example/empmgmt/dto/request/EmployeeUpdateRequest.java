package com.example.empmgmt.dto.request;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;

//更新员工请求
public record EmployeeUpdateRequest(
        @NotBlank(message = "姓名不能为空")
        String name,

        String gender,

        @Positive(message = "年龄必须大于0")
        Integer age,

        @NotBlank(message = "部门不能为空")
        String department,

        String position,

        LocalDate hireDate,

        @Positive(message = "薪资必须大于0")
        BigDecimal salary
) {
}
