package com.example.empmgmt.mq.consumer;

import com.alibaba.excel.EasyExcel;
import com.example.empmgmt.config.ExportMqConfig;
import com.example.empmgmt.domain.Employee;
import com.example.empmgmt.domain.ExportTask;
import com.example.empmgmt.dto.vo.EmployeeExportVO;
import com.example.empmgmt.mq.dto.EmployeeExportParams;
import com.example.empmgmt.mq.dto.ExportTaskMessage;
import com.example.empmgmt.repository.EmployeeRepository;
import com.example.empmgmt.repository.ExportTaskRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 导出任务的消费者
 */

@Slf4j
@Component
@RequiredArgsConstructor
public class ExportConsumer {

    private final ExportTaskRepository exportTaskRepository;
    private final EmployeeRepository employeeRepository;
    private final ObjectMapper objectMapper;


    private static final DateTimeFormatter FILE_NAME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");


    /**
     *  处理导出任务消息
     */
    @RabbitListener(queues = ExportMqConfig.EXPORT_QUEUE)
    @Transactional
    public void handleExportTask(ExportTaskMessage message){
        log.info("收到导出任务消息: {}", message);

        // 查询导出任务
        ExportTask exportTask = exportTaskRepository.findById(message.getTaskId()).orElseThrow(() ->
                new RuntimeException("导出任务不存在，taskId=" + message.getTaskId())
        );

        // 简单幂等：如果不是 PENDING，就不再处理
        if (!"PENDING".equals(exportTask.getStatus())) {
            log.info("导出任务状态不是PENDING，跳过。taskId={}, status={}", exportTask.getId(), exportTask.getStatus());
            return;
        }

        try{
            // 更新任务状态为 PROCESSING
            exportTask.setStatus("PROCESSING");
            exportTask.setUpdatedAt(LocalDateTime.now());
            exportTaskRepository.save(exportTask);

            if ("EMPLOYEE_EXPORT".equals(exportTask.getTaskType())) {
                EmployeeExportParams params = objectMapper.readValue(
                        exportTask.getParams(),
                        EmployeeExportParams.class
                );
                // 执行员工导出
                doEmployeeExport(exportTask, params);
            } else {
                // 预留：其他类型导出
                log.warn("暂不支持的导出类型: {}", exportTask.getTaskType());
                exportTask.setStatus("FAILED");
                exportTask.setErrorMsg("不支持的导出类型: " + exportTask.getTaskType());
                exportTask.setUpdatedAt(LocalDateTime.now());
                exportTaskRepository.save(exportTask);
            }
        }catch (Exception e) {
            log.error("处理导出任务失败，taskId={}", exportTask.getId(), e);
            exportTask.setStatus("FAILED");
            exportTask.setErrorMsg(e.getMessage());
            exportTask.setUpdatedAt(LocalDateTime.now());
            exportTaskRepository.save(exportTask);
            // 不抛异常，避免 MQ 重复投递；更高级的重试/死信队列可以后续引入
        }
    }

    /**
     * 执行员工导出
     */
    private void doEmployeeExport(ExportTask task, EmployeeExportParams params) {
        // 1. 查询数据
        List<Employee> employees;
        if (params.getDepartment() != null && !params.getDepartment().isBlank()) {
            employees = employeeRepository.findByDepartmentAndDeletedFalse(params.getDepartment());
        } else {
            employees = employeeRepository.findAllByDeletedFalse();
        }

        List<EmployeeExportVO> voList = employees.stream()
                .map(this::toEmployeeExportVO)
                .toList();

        // 2. 生成文件（简单起见，写到本地磁盘）
        String fileName = "员工信息_" + LocalDateTime.now().format(FILE_NAME_FORMATTER) + ".xlsx";
        String dir = "D:/exports"; // 可以放到配置里
        File dirFile = new File(dir);
        if (!dirFile.exists()) {
            dirFile.mkdirs();
        }

        File file = new File(dirFile, fileName);

        // 使用 EasyExcel 写入文件
        EasyExcel.write(file, EmployeeExportVO.class)
                .sheet("员工信息")
                .doWrite(voList);

        // 3. 更新任务状态为 SUCCESS
        task.setStatus("SUCCESS");
        task.setFilePath(file.getAbsolutePath());
        task.setUpdatedAt(LocalDateTime.now());
        exportTaskRepository.save(task);

        log.info("员工导出完成，taskId={}, file={}", task.getId(), file.getAbsolutePath());
    }

    /**
     * 转换 Employee 到 EmployeeExportVO
     */
    private EmployeeExportVO toEmployeeExportVO(Employee employee) {
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
