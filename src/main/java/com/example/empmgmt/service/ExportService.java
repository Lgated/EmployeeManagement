package com.example.empmgmt.service;

import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

public interface ExportService {
    /**
     * 导出员工信息为Excel
     * @param department 部门筛选
     * @param position 职位筛选
     * @param response HTTP响应对象
     * @throws IOException IO异常
     */
    void exportEmployeesToExcel(String department, String position, HttpServletResponse response) throws IOException;

    /**
     * 导出用户信息为Excel
     * @param role 角色筛选
     * @param department 部门筛选
     * @param response HTTP响应对象
     * @throws IOException IO异常
     */
    void exportUsersToExcel(String role, String department, HttpServletResponse response) throws IOException;
}
