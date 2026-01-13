package com.example.empmgmt.mq.consumer;

import com.alibaba.excel.EasyExcel;
import com.example.empmgmt.config.ExportMqConfig;
import com.example.empmgmt.domain.Employee;
import com.example.empmgmt.domain.ExportTask;
import com.example.empmgmt.domain.User;
import com.example.empmgmt.dto.vo.EmployeeExportVO;
import com.example.empmgmt.dto.vo.UserExportVO;
import com.example.empmgmt.mq.dto.EmployeeExportParams;
import com.example.empmgmt.mq.dto.ExportTaskMessage;
import com.example.empmgmt.mq.dto.UserExportParams;
import com.example.empmgmt.repository.EmployeeRepository;
import com.example.empmgmt.repository.ExportTaskRepository;
import com.example.empmgmt.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 导出任务的消费者
 */

@Slf4j
@Component
@RequiredArgsConstructor
public class ExportConsumer {

    private final ExportTaskRepository exportTaskRepository;
    private final UserRepository userRepository;
    private final EmployeeRepository employeeRepository;
    private final ObjectMapper objectMapper;
    private final StringRedisTemplate stringRedisTemplate;


    private static final DateTimeFormatter FILE_NAME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");


    /**
     *  处理导出任务消息
     */
    @RabbitListener(queues = ExportMqConfig.EXPORT_QUEUE)
    public void handleExportTask(ExportTaskMessage message){
        log.info("收到导出任务消息: {}", message);

        // 分布式锁的 key
        String lockKey = "export:task:lock:" + message.getTaskId();

        // 使用Redis 幂等/防重锁
        // 尝试占坑，如果 key 不存在则设置成功返回 true，过期时间 30分钟（防止代码挂了死锁）
        Boolean isLockAcquired = stringRedisTemplate.opsForValue().setIfAbsent(lockKey, "1", 30, TimeUnit.MINUTES);

        if (Boolean.FALSE.equals(isLockAcquired)) {
            log.warn("任务正在处理中或已处理，触发防重逻辑，丢弃消息: taskId={}", message.getTaskId());
            // 此时直接返回，MQ 认为消费成功，不会再重试，完美防止重复消费
            return;
        }


        try{

            // 1. 查询任务
            ExportTask exportTask = exportTaskRepository.findById(message.getTaskId()).orElseThrow(() ->
                    new RuntimeException("任务不存在") // 这种错误重试也没用，但在简单设计中也抛出去进死信吧
            );

            // 2. 数据库层面的幂等兜底 (乐观锁思想)
            if (!"PENDING".equals(exportTask.getStatus())) {
                log.info("任务状态非PENDING，跳过: {}", exportTask.getStatus());
                return;
            }

            // 3.更新任务状态为 PROCESSING
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
            } else if ("USER_EXPORT".equals(exportTask.getTaskType())) {
                UserExportParams params = objectMapper.readValue(
                        exportTask.getParams(),
                        UserExportParams.class
                );
                // 执行用户导出
                doUserExport(exportTask, params);
            } else {
                // 不支持的类型，这种不需要重试，直接在数据库标记失败即可，不需要抛异常
                log.warn("暂不支持的导出类型: {}", exportTask.getTaskType());
                exportTask.setStatus("FAILED");
                exportTask.setErrorMsg("不支持的导出类型: " + exportTask.getTaskType());
                exportTask.setUpdatedAt(LocalDateTime.now());
                exportTaskRepository.save(exportTask);
            }
        }catch (Exception e) {
            // 异常处理
            log.error("处理任务异常，准备抛出以触发重试: taskId={}", message.getTaskId(), e);

            // 重要：Redis 锁不仅是防重，如果任务失败了要重试，得把锁删掉，
            // 否则重试的时候（第二次进来）会因为上面有锁而直接返回，导致重试失效！
            stringRedisTemplate.delete(lockKey);

            // 抛出异常 -> 触发 Spring RabbitMQ 的重试机制 (application.yml配置的3次)
            // 重试3次还挂 -> 扔进死信队列
            throw new RuntimeException("导出失败，触发重试", e);
        }finally {
            // 如果成功了，锁可以留着让它自然过期（作为一段时间内的防重墙），
            // 也可以删掉。对于“只做一次”的任务，通常留着自然过期更安全。
            // 但如果想要任务完成后立刻允许下一次（虽然id不一样），可以 delete。
            // 这里我们选择让它过期，作为该 TaskId 的“已消费凭证”。
            stringRedisTemplate.expire(lockKey, 24, TimeUnit.HOURS); // 延长过期时间作为完成标记
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
     * 执行用户导出
     */
    private void doUserExport(ExportTask task, UserExportParams params) {
        // 1、查询数据
        List<User> users;
        if (params.getRole() != null && !params.getRole().isEmpty()) {
            if (params.getDepartment() != null && !params.getDepartment().isEmpty()) {
                users = userRepository.findByRoleAndDepartment(params.getRole(), params.getDepartment());
            } else {
                users = userRepository.findByRole(params.getRole());
            }
        } else {
            // 无过滤条件，查询所有用户
            users = userRepository.findAll();
        }

        // 2、转换为VO
        List<UserExportVO> voList = users.stream()
                .map(this::convertToUserVO)
                .toList();

        // 3. 生成文件（简单起见，写到本地磁盘）
        String fileName = "用户信息_" + LocalDateTime.now().format(FILE_NAME_FORMATTER) + ".xlsx";
        String dir = "D:/exports"; // 可以放到配置
        File dirFile = new File(dir);
        if (!dirFile.exists()) {
            dirFile.mkdirs();
        }
        File file = new File(dirFile, fileName);
        // 使用 EasyExcel 写入文件
        EasyExcel.write(file, UserExportVO.class)
                .sheet("用户信息")
                .doWrite(voList);
        // 4. 更新任务状态为 SUCCESS
        task.setStatus("SUCCESS");
        task.setFilePath(file.getAbsolutePath());
        task.setUpdatedAt(LocalDateTime.now());
        exportTaskRepository.save(task);
        log.info("用户导出完成，taskId={}, file={}", task.getId(), file.getAbsolutePath());
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

    /**
     * User转换为UserExportVO
     */
    private UserExportVO convertToUserVO(User user) {
        UserExportVO vo = new UserExportVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setEmail(user.getEmail());
        vo.setRole(user.getRole());
        vo.setDepartment(user.getDepartment());
        vo.setEmployeeId(user.getEmployeeId());
        vo.setEnabledStatus(user.getEnabled() != null && user.getEnabled() ? "启用" : "禁用");
        vo.setCreatedAt(user.getCreatedAt());
        vo.setUpdatedAt(user.getUpdatedAt());
        return vo;
    }
}
