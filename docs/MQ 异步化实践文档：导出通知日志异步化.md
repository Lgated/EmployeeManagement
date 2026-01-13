## MQ 异步化实践文档：导出/通知/日志异步化 + 可靠投递与幂等

> 目标：  
> 1. 把「员工/用户 Excel 导出」改成异步任务，用户发起导出后立即返回，后台慢慢生成文件。  
> 2. 使用消息队列（以 RabbitMQ 为例）做异步处理，练习 **可靠投递、消费重试、幂等**。  
> 3. 为后续「操作日志异步写库」「通知异步发送」打基础。  
>  
> 文档是“边学边写”风格：每节都有 **思路 → 步骤 → 代码示例**，可以按顺序在项目里实现。

---

## 一、整体设计与准备工作

### 1.1 整体架构

以「员工导出」为例，当前是同步流程：

1. 前端点击导出按钮 → 调用 `/api/employ/export`。  
2. Controller 直接调用 `exportService.exportEmployeesToExcel(...)`。  
3. EasyExcel 在请求线程里写完文件，浏览器开始下载。  
4. 如果数据量大，请求时间会很长，用户体验不好，也容易超时。

**改造为异步流程：**

1. 前端调用 `/api/employ/export/async` 提交导出请求。  
2. 后端：
   - 在 DB 中插入一条导出任务记录（`export_task` 表），状态 `PENDING`；  
   - 把任务信息（taskId、筛选条件、发起人等）作为消息发送到 MQ；  
   - 立即返回 `{ taskId: 123 }` 给前端。  
3. 后台有一个「导出消费者」监听队列：  
   - 消费消息 → 根据条件查 DB → 用 EasyExcel 写 Excel 到磁盘/OSS；  
   - 写完后更新任务状态为 `SUCCESS`，记录文件路径；  
   - 出错时标记 `FAILED`（可重试）。  
4. 前端轮询 `/api/export-tasks/{taskId}` 获取任务状态，状态变为 `SUCCESS` 后再触发下载接口。

同样模式可以用到：

- 操作日志异步写库（AOP 发送 MQ；消费者写入 log 表）；  
- 通知（邮件 / 站内信）异步发送等。

---

### 1.2 选择 MQ & 安装启动（RabbitMQ 示例）

#### 依赖

`pom.xml` 添加：

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-amqp</artifactId>
</dependency>
```

#### 本地启动 RabbitMQ（可选 docker）

```bash
docker run -d --name rabbitmq \
  -p 5672:5672 -p 15672:15672 \
  rabbitmq:3-management
```

管理后台默认地址：`http://localhost:15672`，默认账号密码 `guest/guest`。

#### application.yml 配置（示例）

```yaml
spring:
  rabbitmq:
    host: localhost
    port: 5672
    username: guest
    password: guest
    virtual-host: /
    # 开启 publisher confirm 和 return，支持可靠投递
    publisher-confirm-type: correlated
    publisher-returns: true
```

---

## 二、导出任务表设计

### 2.1 数据表 `export_task`

新增一张简单的任务表（PostgreSQL 示例）：

```sql
CREATE TABLE export_task (
  id           BIGSERIAL PRIMARY KEY,
  task_type    VARCHAR(50) NOT NULL,      -- EMPLOYEE_EXPORT / USER_EXPORT 等
  params       JSONB        NOT NULL,     -- 导出参数（筛选条件等）
  status       VARCHAR(20)  NOT NULL,     -- PENDING / PROCESSING / SUCCESS / FAILED
  file_path    VARCHAR(255),              -- 生成的文件路径
  error_msg    TEXT,
  created_by   BIGINT,
  created_at   TIMESTAMP NOT NULL DEFAULT now(),
  updated_at   TIMESTAMP NOT NULL DEFAULT now()
);
```

### 2.2 对应实体 & Repository

`ExportTask.java`：

```java
package com.example.empmgmt.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "export_task")
public class ExportTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "task_type", nullable = false, length = 50)
    private String taskType;       // EMPLOYEE_EXPORT / USER_EXPORT

    @Column(name = "params", nullable = false, columnDefinition = "jsonb")
    private String params;         // 直接存 JSON 字符串

    @Column(name = "status", nullable = false, length = 20)
    private String status;         // PENDING / PROCESSING / SUCCESS / FAILED

    @Column(name = "file_path")
    private String filePath;

    @Column(name = "error_msg")
    private String errorMsg;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
```

`ExportTaskRepository.java`：

```java
package com.example.empmgmt.repository;

import com.example.empmgmt.domain.ExportTask;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExportTaskRepository extends JpaRepository<ExportTask, Long> {
}
```

---

## 三、导出消息模型与 MQ 配置

### 3.1 导出消息 DTO

`ExportTaskMessage.java`：

```java
package com.example.empmgmt.mq.dto;

import lombok.Data;

@Data
public class ExportTaskMessage {

    private Long taskId;        // 任务ID（数据库主键）
    private String taskType;    // EMPLOYEE_EXPORT / USER_EXPORT
    private String paramsJson;  // 与表里的 params 一致
}
```

### 3.2 RabbitMQ 配置（队列 + 交换机）

`ExportMqConfig.java`：

```java
package com.example.empmgmt.config;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ExportMqConfig {

    public static final String EXPORT_EXCHANGE = "export.exchange";
    public static final String EXPORT_QUEUE = "export.queue";
    public static final String EXPORT_ROUTING_KEY = "export.routing";

    @Bean
    public DirectExchange exportExchange() {
        return new DirectExchange(EXPORT_EXCHANGE);
    }

    @Bean
    public Queue exportQueue() {
        return QueueBuilder.durable(EXPORT_QUEUE).build();
    }

    @Bean
    public Binding exportBinding() {
        return BindingBuilder
                .bind(exportQueue())
                .to(exportExchange())
                .with(EXPORT_ROUTING_KEY);
    }
}
```

---

## 四、生产者：提交导出任务并发送 MQ 消息

### 4.1 导出任务参数对象

例如员工导出的筛选条件：

```java
package com.example.empmgmt.dto.export;

import lombok.Data;

@Data
public class EmployeeExportParams {
    private String department;
    private String position;
}
```

### 4.2 ExportTaskService：创建任务 & 发消息

`ExportTaskService.java`：

```java
package com.example.empmgmt.service;

import com.example.empmgmt.dto.export.EmployeeExportParams;
import com.example.empmgmt.domain.ExportTask;

public interface ExportTaskService {

    Long createEmployeeExportTask(EmployeeExportParams params, Long userId);

    ExportTask getTask(Long taskId);
}
```

`ExportTaskServiceImpl.java`：

```java
package com.example.empmgmt.service.Impl;

import com.example.empmgmt.config.ExportMqConfig;
import com.example.empmgmt.domain.ExportTask;
import com.example.empmgmt.dto.export.EmployeeExportParams;
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
            task.setParams(objectMapper.writeValueAsString(params));
            task.setStatus("PENDING");
            task.setCreatedBy(userId);
            task.setCreatedAt(LocalDateTime.now());
            task.setUpdatedAt(LocalDateTime.now());
            ExportTask saved = exportTaskRepository.save(task);

            // 2. 发送 MQ 消息
            ExportTaskMessage msg = new ExportTaskMessage();
            msg.setTaskId(saved.getId());
            msg.setTaskType(saved.getTaskType());
            msg.setParamsJson(task.getParams());

            rabbitTemplate.convertAndSend(
                    ExportMqConfig.EXPORT_EXCHANGE,
                    ExportMqConfig.EXPORT_ROUTING_KEY,
                    msg
            );

            log.info("提交员工导出任务成功，taskId={}", saved.getId());
            return saved.getId();
        } catch (Exception e) {
            log.error("创建员工导出任务失败", e);
            throw new RuntimeException("创建导出任务失败");
        }
    }

    @Override
    public ExportTask getTask(Long taskId) {
        return exportTaskRepository.findById(taskId)
                .orElseThrow(() -> new RuntimeException("导出任务不存在"));
    }
}
```

---

## 五、异步导出消费者：消费消息并生成文件

### 5.1 消费者实现

`ExportConsumer.java`：

```java
package com.example.empmgmt.mq.consumer;

import com.alibaba.excel.EasyExcel;
import com.example.empmgmt.config.ExportMqConfig;
import com.example.empmgmt.domain.Employee;
import com.example.empmgmt.domain.ExportTask;
import com.example.empmgmt.dto.export.EmployeeExportParams;
import com.example.empmgmt.dto.vo.EmployeeExportVO;
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

@Slf4j
@Component
@RequiredArgsConstructor
public class ExportConsumer {

    private final ExportTaskRepository exportTaskRepository;
    private final EmployeeRepository employeeRepository;
    private final ObjectMapper objectMapper;

    private static final DateTimeFormatter FILE_NAME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    @RabbitListener(queues = ExportMqConfig.EXPORT_QUEUE)
    @Transactional
    public void handleExportTask(ExportTaskMessage message) {
        log.info("收到导出任务消息: {}", message);

        ExportTask task = exportTaskRepository.findById(message.getTaskId())
                .orElse(null);
        if (task == null) {
            log.warn("导出任务不存在，taskId={}", message.getTaskId());
            return;
        }

        // 简单幂等：如果不是 PENDING，就不再处理
        if (!"PENDING".equals(task.getStatus())) {
            log.info("导出任务状态不是PENDING，跳过。taskId={}, status={}", task.getId(), task.getStatus());
            return;
        }

        try {
            task.setStatus("PROCESSING");
            task.setUpdatedAt(LocalDateTime.now());
            exportTaskRepository.save(task);

            if ("EMPLOYEE_EXPORT".equals(task.getTaskType())) {
                EmployeeExportParams params = objectMapper.readValue(
                        task.getParams(),
                        EmployeeExportParams.class
                );
                doEmployeeExport(task, params);
            } else {
                // 预留：其他类型导出
                log.warn("暂不支持的导出类型: {}", task.getTaskType());
                task.setStatus("FAILED");
                task.setErrorMsg("不支持的导出类型: " + task.getTaskType());
                task.setUpdatedAt(LocalDateTime.now());
                exportTaskRepository.save(task);
            }

        } catch (Exception e) {
            log.error("处理导出任务失败，taskId={}", task.getId(), e);
            task.setStatus("FAILED");
            task.setErrorMsg(e.getMessage());
            task.setUpdatedAt(LocalDateTime.now());
            exportTaskRepository.save(task);
            // 不抛异常，避免 MQ 重复投递；更高级的重试/死信队列可以后续引入
        }
    }

    private void doEmployeeExport(ExportTask task, EmployeeExportParams params) throws Exception {
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
```

> 幂等说明：  
> - 每条任务在 DB 中有 `status` 字段；  
> - 消费者拿到任务后，**只处理状态为 `PENDING` 的记录**；  
> - 如果 MQ 因网络问题重复投递同一条消息，第二次消费时看到 status 已经是 `PROCESSING`/`SUCCESS`，就直接跳过，避免重复导出。

---

## 六、Controller：提交任务、查询任务、下载文件

### 6.1 提交异步导出请求

在 `EmployeeController` 中增加一个异步导出接口：

```java
import com.example.empmgmt.dto.export.EmployeeExportParams;
import com.example.empmgmt.service.ExportTaskService;
import com.example.empmgmt.dto.response.Result;

// ...

@RestController
@RequestMapping("/api/employ")
public class EmployeeController {

    private final ExportTaskService exportTaskService;
    // 其他依赖...

    public EmployeeController(ExportTaskService exportTaskService, /* 其他依赖 */) {
        this.exportTaskService = exportTaskService;
        // ...
    }

    @PostMapping("/export/async")
    @RequiresRole({"SUPER_ADMIN", "MANAGER"})
    public Result<Long> createExportTask(
            @RequestParam(required = false) String department,
            @RequestParam(required = false) String position
    ) {
        EmployeeExportParams params = new EmployeeExportParams();
        params.setDepartment(department);
        params.setPosition(position);

        Long userId = SecurityUtil.getCurrentUserId();
        Long taskId = exportTaskService.createEmployeeExportTask(params, userId);
        return Result.success("导出任务已提交", taskId);
    }
}
```

### 6.2 查询任务状态 & 下载

可以单独写一个 `ExportTaskController`：

```java
@RestController
@RequestMapping("/api/export-tasks")
public class ExportTaskController {

    private final ExportTaskService exportTaskService;

    public ExportTaskController(ExportTaskService exportTaskService) {
        this.exportTaskService = exportTaskService;
    }

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

        // 设置响应头并输出文件内容（与之前导出类似）
        // ...
    }
}
```

前端：

- 调用 `/employ/export/async` → 拿到 `taskId`；  
- 开启轮询 `/export-tasks/{taskId}`，status == `SUCCESS` 时再调用 `/export-tasks/{taskId}/download`。

---

## 七、可靠投递与幂等总结

### 7.1 可靠投递（生产端）

- 当前示例中：我们在本地事务中先 `save(task)` 再 `convertAndSend(msg)`。在大多数场景下已经够用。  
- 更严格的做法：
  - 开启 RabbitTemplate 的 `confirmCallback`，确认消息是否到达交换机；  
  - 使用“本地事务 + 消息表 + outbox 模式”，保证 DB 和 MQ 一致，这属于进阶内容，可以后续再扩展。

### 7.2 幂等消费（消费端）

- 通过 `ExportTask.status` 字段做 **幂等控制**：
  - 仅当 status = `PENDING` 时才处理任务；  
  - 处理过程中更新为 `PROCESSING` 和最终状态 `SUCCESS/FAILED`；  
  - 即使消息重复消费，也不会重复导出。

如果以后要做操作日志异步化，也可以完全复用这个模式：  
AOP 收集日志 → 放 MQ → 消费者写入 log 表 + Redis 幂等键。

---

## 八、实践顺序建议

1. 先只做 **员工导出异步化**：  
   - 创建 `export_task` 表和 Entity；  
   - 实现 `ExportTaskService`、`ExportMqConfig`、`ExportConsumer`；  
   - 新增 `/employ/export/async` 接口；  
   - 本地用 Postman 手动调用，观察 MQ 和任务表状态变化。  
2. 再根据这个模板：  
   - 扩展“用户导出”异步任务；  
   - 把现有的操作日志 AOP 改成发 MQ，由消费者异步落库。  







