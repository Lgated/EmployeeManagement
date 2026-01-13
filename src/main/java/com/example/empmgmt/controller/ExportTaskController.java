package com.example.empmgmt.controller;

import com.alibaba.excel.EasyExcel;
import com.example.empmgmt.common.Exception.BusinessException;
import com.example.empmgmt.domain.Employee;
import com.example.empmgmt.domain.ExportTask;
import com.example.empmgmt.dto.response.Result;
import com.example.empmgmt.dto.vo.EmployeeExportVO;
import com.example.empmgmt.service.ExportTaskService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;


@RestController
@RequestMapping("/api/export-tasks")
@Slf4j
public class ExportTaskController {
    private final ExportTaskService exportTaskService;

    public ExportTaskController(ExportTaskService exportTaskService) {
        this.exportTaskService = exportTaskService;
    }

    // 文件名时间格式化器
    private static final DateTimeFormatter FILE_NAME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    /**
     * 获取导出任务详情
     */
    @GetMapping("/{taskId}")
    public Result<ExportTask> getTask(@PathVariable Long taskId) {
        ExportTask task = exportTaskService.getTask(taskId);
        return Result.success(task);
    }

    /**
     * 下载导出文件
     */
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
        // 设置响应头
        String fileName = file.getName();
        String encodedFileName = URLEncoder.encode(fileName, "UTF-8")
                .replaceAll("\\+", "%20");

        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Content-Disposition",
                String.format("attachment; filename=\"%s\"; filename*=UTF-8''%s",
                        fileName, encodedFileName));
        response.setContentLengthLong(file.length());

        // 使用Files.copy直接复制文件到响应流（Java 7+）
        Files.copy(file.toPath(), response.getOutputStream());

        log.info("文件下载成功，taskId={}, file={}", taskId, file.getAbsolutePath());

    }

}
