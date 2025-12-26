package com.example.empmgmt.dto.response;

import java.math.BigDecimal;

//部门统计响应
public record DeptStatsResponse (
        String department,
        Long empCount,
        BigDecimal avgSalary
){
}
