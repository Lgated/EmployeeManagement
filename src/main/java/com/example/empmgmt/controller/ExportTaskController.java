package com.example.empmgmt.controller;

import com.alibaba.excel.EasyExcel;
import com.example.empmgmt.common.Exception.BusinessException;
import com.example.empmgmt.domain.Employee;
import com.example.empmgmt.domain.ExportTask;
import com.example.empmgmt.dto.response.Result;
import com.example.empmgmt.dto.vo.EmployeeExportVO;
import com.example.empmgmt.service.ExportTaskService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.io.IOException;
import java.net.URLEncoder;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;


@RestController
@RequestMapping("/api/export-tasks")
public class ExportTaskController {
    private final ExportTaskService exportTaskService;

    public ExportTaskController(ExportTaskService exportTaskService) {
        this.exportTaskService = exportTaskService;
    }

    // 文件名时间格式化器
    private static final DateTimeFormatter FILE_NAME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    @GetMapping("/{taskId}")
    public Result<ExportTask> getTask(@PathVariable Long taskId) {
        ExportTask task = exportTaskService.getTask(taskId);
        return Result.success(task);
    }

    @GetMapping("/{taskId}/download")
    public void download(@PathVariable Long taskId, HttpServletResponse response) throws IOException {
        ExportTask task = exportTaskService.getTask(taskId);
        if (!"SUCCESS".equals(task.getStatus())) {
            throw new BusinessException("任务未完成，无法下载");
        }
        File file = new File(task.getFilePath());
        if (!file.exists()) {
            throw new BusinessException("文件不存在，请重新导出");
        }

        // 查询数据
        List<Employee> employee = exportTaskService.getEmployeesForExportTask(taskId);
        List<EmployeeExportVO> voList = employee.stream().map(this::convertToEmployeeVO).toList();

        // 设置响应头
        String fileName = "员工信息_" + LocalDateTime.now().format(FILE_NAME_FORMATTER) + ".xlsx";
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("utf-8");
        response.setHeader("Content-Disposition",
                "attachment; filename=\"" + fileName + "\"; filename*=UTF-8''" + URLEncoder.encode(fileName, "UTF-8"));
        // 4. 使用EasyExcel写入
        EasyExcel.write(response.getOutputStream(), EmployeeExportVO.class)
                .sheet("员工信息")
                .doWrite(voList);
    }

    /**
     * Employee转换为EmployeeExportVO
     */
    private EmployeeExportVO convertToEmployeeVO(Employee employee) {
        EmployeeExportVO vo = new EmployeeExportVO();
        vo.setId(employee.getId());
        vo.setName(employee.getName());
        vo.setGender(employee.getGender());
        vo.setAge(employee.getAge());
        vo.setDepartment(employee.getDepartment());
        vo.setPosition(employee.getPosition());
        vo.setHireDate(employee.getHireDate());
        vo.setSalary(employee.getSalary());
        vo.setAvatar(employee.getAvatar());
        vo.setCreatedAt(employee.getCreatedAt());
        vo.setUpdatedAt(employee.getUpdatedAt());
        return vo;
    }
}
