package com.example.empmgmt.service.Impl;

import com.example.empmgmt.config.ExportMqConfig;
import com.example.empmgmt.domain.Employee;
import com.example.empmgmt.domain.ExportTask;
import com.example.empmgmt.mq.dto.EmployeeExportParams;
import com.example.empmgmt.mq.dto.ExportTaskMessage;
import com.example.empmgmt.repository.ExportTaskRepository;
import com.example.empmgmt.service.ExportTaskService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;


@Slf4j
@Service
@RequiredArgsConstructor
public class ExportTaskServiceImpl implements ExportTaskService {


    private final ExportTaskRepository exportTaskRepository;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;



    @Override
    @Transactional
    public Long createEmployeeExportTask(EmployeeExportParams params, Long userId) {
        try {
            // 1. 保存任务到数据库
            ExportTask task = new ExportTask();
            task.setTaskType("EMPLOYEE_EXPORT");
            // 将参数对象转换为 JSON 字符串存储
            task.setParams(objectMapper.writeValueAsString(params));
            task.setCreatedBy(userId);
            task.setStatus("PENDING");
            task.setCreatedAt(LocalDateTime.now());
            task.setUpdatedAt(LocalDateTime.now());
            ExportTask save = exportTaskRepository.save(task);

            // 2. 发送 MQ 消息
            ExportTaskMessage message = new ExportTaskMessage();
            message.setTaskId(save.getId());
            message.setTaskType(task.getTaskType());
            message.setParamsJson(save.getParams());

            // 发送到指定的交换机和路由键
            //convertAndSend() : 异步发送消息，不阻塞
            rabbitTemplate.convertAndSend(ExportMqConfig.EXPORT_EXCHANGE,
                    ExportMqConfig.EXPORT_ROUTING_KEY,
                    message);
            log.info("提交员工导出任务成功，taskId={}", save.getId());
            return save.getId();
        } catch (Exception e) {
            log.error("创建员工导出任务失败", e);
            throw new RuntimeException("创建员工导出任务失败", e);
        }
    }

    @Override
    public ExportTask getTask(Long taskId) {
        return exportTaskRepository.findById(taskId)
                .orElseThrow(() -> new RuntimeException("导出任务不存在，taskId=" + taskId));
    }

    //TODO: 根据任务ID获取需要导出的员工列表
    @Override
    public List<Employee> getEmployeesForExportTask(Long taskId) {
        ExportTask task = getTask(taskId);
        try {
            EmployeeExportParams params = objectMapper.readValue(
                    task.getParams(),
                    EmployeeExportParams.class
            );
            // 根据参数查询员工数据
            // 这里假设有一个 employeeRepository，可以根据实际情况调整
            // return employeeRepository.findByCriteria(params);
            // 暂时返回空列表，实际实现中应根据 params 查询数据
            return List.of();
        } catch (Exception e) {
            log.error("解析导出任务参数失败，taskId={}", taskId, e);
            throw new RuntimeException("解析导出任务参数失败，taskId=" + taskId, e);
        }
    }
}
