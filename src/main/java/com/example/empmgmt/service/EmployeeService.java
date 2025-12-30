package com.example.empmgmt.service;

import com.example.empmgmt.domain.Employee;
import com.example.empmgmt.dto.request.EmployeeCreateRequest;
import com.example.empmgmt.dto.request.EmployeeUpdateRequest;
import com.example.empmgmt.dto.response.DeptStatsResponse;
import com.example.empmgmt.dto.response.EmployeeResponse;
import com.example.empmgmt.dto.response.PageResponse;
import com.example.empmgmt.repository.EmployeeRepository;

import java.math.BigDecimal;
import java.util.List;

public interface EmployeeService {


    /**
     * 创建员工
     */
    EmployeeResponse create(EmployeeCreateRequest request);

    /**
     * 更新员工
     */
    EmployeeResponse update(Long id, EmployeeUpdateRequest request);

    /**
     * 删除员工
     */
    void delete(Long id);

    /**
     * 根据id查找员工
     */
    EmployeeResponse findById(Long id);

    /**
     * 分页查询
     */
    PageResponse<EmployeeResponse> pageQuery(String name, String department, int page, int size);

    /**
     * 查询所有员工
     */
    List<EmployeeResponse> listAll();

    /**
     * 搜索员工（按姓名或部门）
     */
    List<EmployeeResponse> search(String name, String department);

    /**
     * 统计各部门员工数量
     */
    List<DeptStatsResponse> getDeptEmpCount();

    /**
     * 统计各部门平均薪资
     */
    List<DeptStatsResponse> getDeptAvgSalary();

    /**
     * 计算员工工龄（年）
     */
    BigDecimal getEmpYears(Long id);
}
