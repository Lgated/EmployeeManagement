package com.example.empmgmt.controller;


import com.example.empmgmt.dto.request.EmployeeCreateRequest;
import com.example.empmgmt.dto.request.EmployeeUpdateRequest;
import com.example.empmgmt.dto.response.DeptStatsResponse;
import com.example.empmgmt.dto.response.EmployeeResponse;
import com.example.empmgmt.dto.response.PageResponse;
import com.example.empmgmt.dto.response.Result;
import com.example.empmgmt.service.EmployeeService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/employ")
public class EmployeeController {

    private final EmployeeService employeeService;

    public EmployeeController(EmployeeService employeeService){
        this.employeeService = employeeService;
    }

    /**
     * 创建员工
     */
    @PostMapping
    public Result<EmployeeResponse> create(
            @Valid @RequestBody EmployeeCreateRequest request
    ){
        EmployeeResponse response = employeeService.create(request);
        return Result.success(response);
    }

    /**
     * 根据 id 去查找员工
     */
    @GetMapping("/{id}")
    public Result<EmployeeResponse> getById(@PathVariable Long id){
        EmployeeResponse employeeServiceById = employeeService.findById(id);
        return Result.success(employeeServiceById);
    }

    /**
     * 查询所有员工（支持按姓名或部门搜索）
     */
    @GetMapping
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
     * 更新员工
     */
    @PutMapping("/{id}")
    public Result<EmployeeResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody EmployeeUpdateRequest employeeUpdateRequest){
        EmployeeResponse employeeResponse = employeeService.update(id, employeeUpdateRequest);
        return Result.success(employeeResponse);
    }

    /**
     * 删除员工
     */
    @DeleteMapping("/{id}")
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
}
