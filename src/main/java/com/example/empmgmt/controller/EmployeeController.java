package com.example.empmgmt.controller;


import com.example.empmgmt.common.annotation.OperationLog;
import com.example.empmgmt.common.annotation.RequiresPermission;
import com.example.empmgmt.common.annotation.RequiresRole;
import com.example.empmgmt.common.enums.OperationType;
import com.example.empmgmt.dto.request.EmployeeCreateRequest;
import com.example.empmgmt.dto.request.EmployeeUpdateRequest;
import com.example.empmgmt.dto.response.DeptStatsResponse;
import com.example.empmgmt.dto.response.EmployeeResponse;
import com.example.empmgmt.dto.response.PageResponse;
import com.example.empmgmt.dto.response.Result;
import com.example.empmgmt.service.EmployeeService;
import com.example.empmgmt.service.ExportService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/employ")
public class EmployeeController {

    private final ExportService exportService;
    private final EmployeeService employeeService;

    public EmployeeController(EmployeeService employeeService, ExportService exportService) {
        this.exportService = exportService;
        this.employeeService = employeeService;
    }

    /**
     * 创建员工 -- 创建权限
     */
    @PostMapping
    @RequiresPermission(value = "employee:create", checkDepartment = true)
    @OperationLog(
            module = "员工管理",
            type = OperationType.CREATE,
            description = "创建员工",
            saveResult = true
    )
    public Result<EmployeeResponse> create(
            @Valid @RequestBody EmployeeCreateRequest request
    ){
        EmployeeResponse response = employeeService.create(request);
        return Result.success(response);
    }

    /**
     * 根据 id 去查找员工 -- 读取权限+所有者检查
     */
    @RequiresPermission(value = "employee:read", checkOwner = true)
    @GetMapping("/{id}")
    @OperationLog(
            module = "员工管理",
            type = OperationType.QUERY,
            description = "根据ID查询员工"
    )
    public Result<EmployeeResponse> getById(@PathVariable Long id){
        EmployeeResponse employeeServiceById = employeeService.findById(id);
        return Result.success(employeeServiceById);
    }

    /**
     * 查询所有员工（支持按姓名或部门搜索） -- 读取权限
     */
    @GetMapping
    @RequiresPermission("employee:read")
    public Result<PageResponse<EmployeeResponse>> list(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String department,
            @RequestParam(defaultValue = "1") int page,      // 当前页，默认第 1 页
            @RequestParam(defaultValue = "10") int size)     // 每页条数，默认 10 条
    {
        PageResponse<EmployeeResponse> pageResult = employeeService.pageQuery(name, department, page, size);
        return Result.success(pageResult);
    }

    /**
     * 更新员工 -- 更新权限 + 所有检查
     */
    @PutMapping("/{id}")
    @RequiresPermission(value = "employee:update", checkOwner = true,checkDepartment = true)
    @OperationLog(
            module = "员工管理",
            type = OperationType.UPDATE,
            description = "更新员工信息",
            saveResult = true
    )
    public Result<EmployeeResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody EmployeeUpdateRequest employeeUpdateRequest){
        EmployeeResponse employeeResponse = employeeService.update(id, employeeUpdateRequest);
        return Result.success(employeeResponse);
    }

    /**
     * 删除员工 -- 删除权限 + 部门检查
     */
    @DeleteMapping("/{id}")
    @RequiresPermission(value = "employee:delete", checkDepartment = true)
    @OperationLog(
            module = "员工管理",
            type = OperationType.DELETE,
            description = "删除员工"
    )
    public Result<Void> delete(@PathVariable Long id){
        employeeService.delete(id);
        return Result.success(null);
    }

    /**
     * 统计各部门平均人数
     */
    @GetMapping("/stats/dept-count")
    public Result<List<DeptStatsResponse>> getDeptCount(){
        List<DeptStatsResponse> deptEmpCount = employeeService.getDeptEmpCount();
        return Result.success(deptEmpCount);
    }


    /**
     * 统计各部门平均薪资
     */
    @GetMapping("/stats/dept-avg-salary")
    public Result<List<DeptStatsResponse>> getDeptAvgSalary(){
        List<DeptStatsResponse> deptAvgSalary = employeeService.getDeptAvgSalary();
        return Result.success(deptAvgSalary);
    }


    /**
     * 统计员工在职时间
     */
    @GetMapping("/{id}/years")
    public Result<BigDecimal> getEmpYears(@PathVariable Long id){
        BigDecimal empYears = employeeService.getEmpYears(id);
        return Result.success(empYears);
    }

    /**
     * 导出员工信息为Excel
     * 权限：SUPER_ADMIN、MANAGER
     */
    //todo: 导出员工数据
    @GetMapping("/export")
    @RequiresRole({"SUPER_ADMIN", "MANAGER"})
    public void exportEmployees(
            @RequestParam(required = false) String department,
            @RequestParam(required = false) String position,
            HttpServletResponse response
    ) throws IOException {
        exportService.exportEmployeesToExcel(department, position, response);
    }
}
