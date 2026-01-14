# xxl-job + ES + 多线程详细实现指南

## 一、环境准备

### 1.1 安装xxl-job调度中心

#### **方式1：Docker安装（推荐）**

```bash
# 拉取镜像
docker pull xuxueli/xxl-job-admin:2.4.0

# 运行容器
docker run -d \
  --name xxl-job-admin \
  -p 8080:8080 \
  -e PARAMS="--spring.datasource.url=jdbc:mysql://localhost:3306/xxl_job?useUnicode=true&characterEncoding=UTF-8&autoReconnect=true&serverTimezone=Asia/Shanghai --spring.datasource.username=root --spring.datasource.password=123456" \
  xuxueli/xxl-job-admin:2.4.0
```

#### **方式2：源码编译**

1. 下载源码：https://github.com/xuxueli/xxl-job
2. 导入数据库脚本：`/xxl-job/doc/db/tables_xxl_job.sql`
3. 修改配置文件：`application.properties`
4. 启动项目：访问 `http://localhost:8080/xxl-job-admin`

**默认账号密码：** admin / 123456

### 1.2 安装Elasticsearch

#### **Docker安装**

```bash
# 拉取镜像
docker pull docker.elastic.co/elasticsearch/elasticsearch:8.11.0

# 运行容器
docker run -d \
  --name elasticsearch \
  -p 9200:9200 \
  -p 9300:9300 \
  -e "discovery.type=single-node" \
  -e "xpack.security.enabled=false" \
  docker.elastic.co/elasticsearch/elasticsearch:8.11.0

# 验证安装
curl http://localhost:9200
```

#### **安装Kibana（可选，用于可视化）**

```bash
docker run -d \
  --name kibana \
  -p 5601:5601 \
  -e "ELASTICSEARCH_HOSTS=http://elasticsearch:9200" \
  docker.elastic.co/kibana/kibana:8.11.0
```

---

## 二、项目配置

### 2.1 添加Maven依赖

```xml
<!-- pom.xml -->
<dependencies>
    <!-- xxl-job -->
    <dependency>
        <groupId>com.xuxueli</groupId>
        <artifactId>xxl-job-core</artifactId>
        <version>2.4.0</version>
    </dependency>

    <!-- Elasticsearch -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-elasticsearch</artifactId>
    </dependency>

    <!-- Guava (用于Lists工具类) -->
    <dependency>
        <groupId>com.google.guava</groupId>
        <artifactId>guava</artifactId>
        <version>32.1.3-jre</version>
    </dependency>
</dependencies>
```

### 2.2 配置文件

```yaml
# application.yml 添加以下配置

spring:
  elasticsearch:
    uris: http://localhost:9200
    # username: elastic  # 如果ES开启了安全认证
    # password: elastic

xxl:
  job:
    admin:
      addresses: http://localhost:8080/xxl-job-admin  # xxl-job调度中心地址
    executor:
      appname: empmgmt-executor  # 执行器名称（需要在调度中心创建）
      address:  # 执行器地址，留空自动注册
      ip:  # 执行器IP，留空自动获取
      port: 9999  # 执行器端口
      logpath: ./logs/xxl-job  # 日志路径
      logretentiondays: 30  # 日志保留天数
    accessToken:  # 访问令牌，可选
```

### 2.3 xxl-job配置类

```java
package com.example.empmgmt.config;

import com.xxl.job.core.executor.impl.XxlJobSpringExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class XxlJobConfig {

    @Value("${xxl.job.admin.addresses}")
    private String adminAddresses;

    @Value("${xxl.job.executor.appname}")
    private String appname;

    @Value("${xxl.job.executor.address:}")
    private String address;

    @Value("${xxl.job.executor.ip:}")
    private String ip;

    @Value("${xxl.job.executor.port:9999}")
    private int port;

    @Value("${xxl.job.accessToken:}")
    private String accessToken;

    @Value("${xxl.job.executor.logpath:./logs/xxl-job}")
    private String logPath;

    @Value("${xxl.job.executor.logretentiondays:30}")
    private int logRetentionDays;

    @Bean
    public XxlJobSpringExecutor xxlJobExecutor() {
        log.info(">>>>>>>>>>> xxl-job config init.");
        XxlJobSpringExecutor xxlJobSpringExecutor = new XxlJobSpringExecutor();
        xxlJobSpringExecutor.setAdminAddresses(adminAddresses);
        xxlJobSpringExecutor.setAppname(appname);
        xxlJobSpringExecutor.setAddress(address);
        xxlJobSpringExecutor.setIp(ip);
        xxlJobSpringExecutor.setPort(port);
        xxlJobSpringExecutor.setAccessToken(accessToken);
        xxlJobSpringExecutor.setLogPath(logPath);
        xxlJobSpringExecutor.setLogRetentionDays(logRetentionDays);

        return xxlJobSpringExecutor;
    }
}
```

### 2.4 Elasticsearch配置类

```java
package com.example.empmgmt.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.elasticsearch.config.AbstractElasticsearchConfiguration;
import org.springframework.data.elasticsearch.repository.config.EnableElasticsearchRepositories;

@Configuration
@EnableElasticsearchRepositories(basePackages = "com.example.empmgmt.repository")
public class ElasticsearchConfig extends AbstractElasticsearchConfiguration {

    // Spring Boot会自动配置ElasticsearchClient
    // 如果需要自定义配置，可以重写elasticsearchClient()方法
}
```

### 2.5 线程池配置类

```java
package com.example.empmgmt.config;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

import java.util.concurrent.*;

@Slf4j
@Configuration
@EnableAsync
public class ThreadPoolConfig {

    /**
     * 数据导出线程池
     * 用于Excel导出、文件生成等IO密集型任务
     */
    @Bean("exportExecutor")
    public ThreadPoolExecutor exportExecutor() {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
            3,                      // 核心线程数
            6,                      // 最大线程数
            60L,                    // 空闲线程存活时间
            TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(50),  // 队列容量
            new ThreadFactoryBuilder()
                .setNameFormat("export-thread-%d")
                .setUncaughtExceptionHandler((t, e) -> 
                    log.error("Export thread {} error", t.getName(), e))
                .build(),
            new ThreadPoolExecutor.CallerRunsPolicy()  // 拒绝策略：调用者运行
        );
        
        log.info("Export thread pool initialized: core={}, max={}", 
            executor.getCorePoolSize(), executor.getMaximumPoolSize());
        return executor;
    }

    /**
     * ES写入线程池
     * 用于批量写入Elasticsearch
     */
    @Bean("esExecutor")
    public ThreadPoolExecutor esExecutor() {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
            2,                      // 核心线程数
            4,                      // 最大线程数
            60L,
            TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(100),  // 队列容量
            new ThreadFactoryBuilder()
                .setNameFormat("es-thread-%d")
                .setUncaughtExceptionHandler((t, e) -> 
                    log.error("ES thread {} error", t.getName(), e))
                .build(),
            new ThreadPoolExecutor.CallerRunsPolicy()
        );
        
        log.info("ES thread pool initialized: core={}, max={}", 
            executor.getCorePoolSize(), executor.getMaximumPoolSize());
        return executor;
    }

    /**
     * 通用异步线程池
     * 用于@Async注解的异步方法
     */
    @Bean("taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("async-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
```

---

## 三、ES实体和Repository

### 3.1 报表文档实体

```java
package com.example.empmgmt.domain;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 报表文档 - 存储在Elasticsearch
 */
@Data
@Document(indexName = "report_index")
public class ReportDocument {

    @Id
    private String reportId;

    /**
     * 报表类型：EMPLOYEE_STAT, USER_STAT, OPERATION_LOG_STAT
     */
    @Field(type = FieldType.Keyword)
    private String reportType;

    /**
     * 报表日期
     */
    @Field(type = FieldType.Date)
    private LocalDateTime reportDate;

    /**
     * 报表周期：DAILY, WEEKLY, MONTHLY
     */
    @Field(type = FieldType.Keyword)
    private String reportPeriod;

    /**
     * 创建人ID
     */
    @Field(type = FieldType.Long)
    private Long createdBy;

    /**
     * 创建时间
     */
    @Field(type = FieldType.Date)
    private LocalDateTime createdAt;

    /**
     * 统计数据（嵌套对象）
     */
    @Field(type = FieldType.Object)
    private ReportStatistics statistics;

    /**
     * 报表文件路径
     */
    @Field(type = FieldType.Keyword)
    private String filePath;

    /**
     * 状态：SUCCESS, FAILED, PROCESSING
     */
    @Field(type = FieldType.Keyword)
    private String status;

    /**
     * 统计数据内部类
     */
    @Data
    public static class ReportStatistics {
        /**
         * 总数量
         */
        @Field(type = FieldType.Long)
        private Long totalCount;

        /**
         * 部门统计（嵌套类型）
         */
        @Field(type = FieldType.Nested)
        private Map<String, Long> departmentStats;

        /**
         * 角色统计
         */
        @Field(type = FieldType.Nested)
        private Map<String, Long> roleStats;

        /**
         * 趋势数据（用于图表展示）
         */
        @Field(type = FieldType.Object)
        private Map<String, Object> trendData;
    }
}
```

### 3.2 Repository接口

```java
package com.example.empmgmt.repository;

import com.example.empmgmt.domain.ReportDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface ReportRepository extends ElasticsearchRepository<ReportDocument, String> {

    /**
     * 根据报表类型和日期范围查询
     */
    List<ReportDocument> findByReportTypeAndReportDateBetween(
        String reportType,
        LocalDateTime start,
        LocalDateTime end
    );

    /**
     * 根据报表类型分页查询
     */
    Page<ReportDocument> findByReportType(String reportType, Pageable pageable);

    /**
     * 根据状态查询
     */
    List<ReportDocument> findByStatus(String status);
}
```

---

## 四、报表服务实现

### 4.1 报表服务接口

```java
package com.example.empmgmt.service;

import com.example.empmgmt.domain.ReportDocument;
import java.time.LocalDateTime;
import java.util.List;

public interface ReportService {

    /**
     * 生成员工统计报表
     */
    String generateEmployeeReport(LocalDateTime reportDate, String period);

    /**
     * 生成用户统计报表
     */
    String generateUserReport(LocalDateTime reportDate, String period);

    /**
     * 生成操作日志统计报表
     */
    String generateOperationLogReport(LocalDateTime reportDate, String period);

    /**
     * 查询报表列表
     */
    List<ReportDocument> queryReports(String reportType, LocalDateTime start, LocalDateTime end);

    /**
     * 根据ID查询报表
     */
    ReportDocument getReportById(String reportId);
}
```

### 4.2 报表服务实现

```java
package com.example.empmgmt.service.Impl;

import com.example.empmgmt.domain.ReportDocument;
import com.example.empmgmt.repository.EmployeeRepository;
import com.example.empmgmt.repository.OperationLogRepository;
import com.example.empmgmt.repository.ReportRepository;
import com.example.empmgmt.repository.UserRepository;
import com.example.empmgmt.service.ReportService;
import com.google.common.collect.Lists;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReportServiceImpl implements ReportService {

    private final ReportRepository reportRepository;
    private final EmployeeRepository employeeRepository;
    private final UserRepository userRepository;
    private final OperationLogRepository operationLogRepository;

    @Qualifier("esExecutor")
    private final ThreadPoolExecutor esExecutor;

    @Override
    public String generateEmployeeReport(LocalDateTime reportDate, String period) {
        log.info("开始生成员工统计报表，日期：{}，周期：{}", reportDate, period);

        // 1. 查询统计数据（并行查询）
        CompletableFuture<Long> totalCountFuture = CompletableFuture.supplyAsync(
            () -> employeeRepository.count(), esExecutor
        );

        CompletableFuture<Map<String, Long>> deptStatsFuture = CompletableFuture.supplyAsync(
            () -> {
                // 按部门统计
                List<Object[]> results = employeeRepository.countByDepartment();
                return results.stream()
                    .collect(Collectors.toMap(
                        arr -> (String) arr[0],
                        arr -> ((Number) arr[1]).longValue()
                    ));
            }, esExecutor
        );

        // 等待所有查询完成
        Long totalCount = totalCountFuture.join();
        Map<String, Long> deptStats = deptStatsFuture.join();

        // 2. 构建统计数据
        ReportDocument.ReportStatistics statistics = new ReportDocument.ReportStatistics();
        statistics.setTotalCount(totalCount);
        statistics.setDepartmentStats(deptStats);

        // 3. 创建报表文档
        ReportDocument document = new ReportDocument();
        document.setReportId(UUID.randomUUID().toString());
        document.setReportType("EMPLOYEE_STAT");
        document.setReportDate(reportDate);
        document.setReportPeriod(period);
        document.setCreatedAt(LocalDateTime.now());
        document.setStatistics(statistics);
        document.setStatus("SUCCESS");

        // 4. 保存到ES
        reportRepository.save(document);

        log.info("员工统计报表生成完成，reportId：{}", document.getReportId());
        return document.getReportId();
    }

    @Override
    public String generateUserReport(LocalDateTime reportDate, String period) {
        log.info("开始生成用户统计报表，日期：{}，周期：{}", reportDate, period);

        // 并行查询
        CompletableFuture<Long> totalCountFuture = CompletableFuture.supplyAsync(
            () -> userRepository.count(), esExecutor
        );

        CompletableFuture<Map<String, Long>> roleStatsFuture = CompletableFuture.supplyAsync(
            () -> {
                List<Object[]> results = userRepository.countByRole();
                return results.stream()
                    .collect(Collectors.toMap(
                        arr -> (String) arr[0],
                        arr -> ((Number) arr[1]).longValue()
                    ));
            }, esExecutor
        );

        CompletableFuture<Map<String, Long>> deptStatsFuture = CompletableFuture.supplyAsync(
            () -> {
                List<Object[]> results = userRepository.countByDepartment();
                return results.stream()
                    .collect(Collectors.toMap(
                        arr -> (String) arr[0],
                        arr -> ((Number) arr[1]).longValue()
                    ));
            }, esExecutor
        );

        // 等待完成
        Long totalCount = totalCountFuture.join();
        Map<String, Long> roleStats = roleStatsFuture.join();
        Map<String, Long> deptStats = deptStatsFuture.join();

        // 构建文档
        ReportDocument.ReportStatistics statistics = new ReportDocument.ReportStatistics();
        statistics.setTotalCount(totalCount);
        statistics.setRoleStats(roleStats);
        statistics.setDepartmentStats(deptStats);

        ReportDocument document = new ReportDocument();
        document.setReportId(UUID.randomUUID().toString());
        document.setReportType("USER_STAT");
        document.setReportDate(reportDate);
        document.setReportPeriod(period);
        document.setCreatedAt(LocalDateTime.now());
        document.setStatistics(statistics);
        document.setStatus("SUCCESS");

        reportRepository.save(document);

        log.info("用户统计报表生成完成，reportId：{}", document.getReportId());
        return document.getReportId();
    }

    @Override
    public String generateOperationLogReport(LocalDateTime reportDate, String period) {
        log.info("开始生成操作日志统计报表，日期：{}，周期：{}", reportDate, period);

        // 查询指定日期范围的日志
        LocalDateTime start = reportDate.toLocalDate().atStartOfDay();
        LocalDateTime end = start.plusDays(1);

        CompletableFuture<Long> totalCountFuture = CompletableFuture.supplyAsync(
            () -> operationLogRepository.countByCreatedAtBetween(start, end), esExecutor
        );

        CompletableFuture<Map<String, Long>> moduleStatsFuture = CompletableFuture.supplyAsync(
            () -> {
                List<Object[]> results = operationLogRepository.countByModuleAndCreatedAtBetween(start, end);
                return results.stream()
                    .collect(Collectors.toMap(
                        arr -> (String) arr[0],
                        arr -> ((Number) arr[1]).longValue()
                    ));
            }, esExecutor
        );

        Long totalCount = totalCountFuture.join();
        Map<String, Long> moduleStats = moduleStatsFuture.join();

        ReportDocument.ReportStatistics statistics = new ReportDocument.ReportStatistics();
        statistics.setTotalCount(totalCount);
        statistics.setDepartmentStats(moduleStats);  // 复用字段存储模块统计

        ReportDocument document = new ReportDocument();
        document.setReportId(UUID.randomUUID().toString());
        document.setReportType("OPERATION_LOG_STAT");
        document.setReportDate(reportDate);
        document.setReportPeriod(period);
        document.setCreatedAt(LocalDateTime.now());
        document.setStatistics(statistics);
        document.setStatus("SUCCESS");

        reportRepository.save(document);

        log.info("操作日志统计报表生成完成，reportId：{}", document.getReportId());
        return document.getReportId();
    }

    @Override
    public List<ReportDocument> queryReports(String reportType, LocalDateTime start, LocalDateTime end) {
        return reportRepository.findByReportTypeAndReportDateBetween(reportType, start, end);
    }

    @Override
    public ReportDocument getReportById(String reportId) {
        return reportRepository.findById(reportId).orElse(null);
    }
}
```

---

## 五、xxl-job任务实现

### 5.1 员工统计报表Job

```java
package com.example.empmgmt.job;

import com.example.empmgmt.service.ReportService;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 员工统计报表定时任务
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmployeeReportJob {

    private final ReportService reportService;

    /**
     * 每日员工统计报表
     * 在xxl-job调度中心配置：每天凌晨2点执行
     */
    @XxlJob("employeeDailyReport")
    public void employeeDailyReport(String param) {
        log.info("开始执行每日员工统计报表任务，参数：{}", param);
        
        try {
            LocalDateTime reportDate = LocalDateTime.now().minusDays(1);  // 统计昨天的数据
            String reportId = reportService.generateEmployeeReport(reportDate, "DAILY");
            
            log.info("每日员工统计报表生成成功，reportId：{}", reportId);
        } catch (Exception e) {
            log.error("每日员工统计报表生成失败", e);
            throw e;  // 抛出异常，xxl-job会记录失败并触发重试
        }
    }

    /**
     * 每周员工统计报表
     * 配置：每周一凌晨3点执行
     */
    @XxlJob("employeeWeeklyReport")
    public void employeeWeeklyReport(String param) {
        log.info("开始执行每周员工统计报表任务");
        
        try {
            LocalDateTime reportDate = LocalDateTime.now().minusWeeks(1);
            String reportId = reportService.generateEmployeeReport(reportDate, "WEEKLY");
            
            log.info("每周员工统计报表生成成功，reportId：{}", reportId);
        } catch (Exception e) {
            log.error("每周员工统计报表生成失败", e);
            throw e;
        }
    }

    /**
     * 每月员工统计报表
     * 配置：每月1号凌晨4点执行
     */
    @XxlJob("employeeMonthlyReport")
    public void employeeMonthlyReport(String param) {
        log.info("开始执行每月员工统计报表任务");
        
        try {
            LocalDateTime reportDate = LocalDateTime.now().minusMonths(1);
            String reportId = reportService.generateEmployeeReport(reportDate, "MONTHLY");
            
            log.info("每月员工统计报表生成成功，reportId：{}", reportId);
        } catch (Exception e) {
            log.error("每月员工统计报表生成失败", e);
            throw e;
        }
    }
}
```

### 5.2 用户统计报表Job

```java
package com.example.empmgmt.job;

import com.example.empmgmt.service.ReportService;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserReportJob {

    private final ReportService reportService;

    @XxlJob("userDailyReport")
    public void userDailyReport(String param) {
        log.info("开始执行每日用户统计报表任务");
        
        try {
            LocalDateTime reportDate = LocalDateTime.now().minusDays(1);
            String reportId = reportService.generateUserReport(reportDate, "DAILY");
            log.info("每日用户统计报表生成成功，reportId：{}", reportId);
        } catch (Exception e) {
            log.error("每日用户统计报表生成失败", e);
            throw e;
        }
    }
}
```

### 5.3 操作日志统计报表Job

```java
package com.example.empmgmt.job;

import com.example.empmgmt.service.ReportService;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class OperationLogReportJob {

    private final ReportService reportService;

    @XxlJob("operationLogDailyReport")
    public void operationLogDailyReport(String param) {
        log.info("开始执行每日操作日志统计报表任务");
        
        try {
            LocalDateTime reportDate = LocalDateTime.now().minusDays(1);
            String reportId = reportService.generateOperationLogReport(reportDate, "DAILY");
            log.info("每日操作日志统计报表生成成功，reportId：{}", reportId);
        } catch (Exception e) {
            log.error("每日操作日志统计报表生成失败", e);
            throw e;
        }
    }
}
```

---

## 六、Repository扩展方法

需要在Repository中添加统计查询方法：

### 6.1 EmployeeRepository扩展

```java
// 在EmployeeRepository中添加
@Query("SELECT e.department, COUNT(e) FROM Employee e WHERE e.deleted = false GROUP BY e.department")
List<Object[]> countByDepartment();
```

### 6.2 UserRepository扩展

```java
// 在UserRepository中添加
@Query("SELECT u.role, COUNT(u) FROM User u GROUP BY u.role")
List<Object[]> countByRole();

@Query("SELECT u.department, COUNT(u) FROM User u WHERE u.department IS NOT NULL GROUP BY u.department")
List<Object[]> countByDepartment();
```

### 6.3 OperationLogRepository扩展

```java
// 在OperationLogRepository中添加
long countByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

@Query("SELECT ol.module, COUNT(ol) FROM OperationLog ol WHERE ol.createdAt BETWEEN ?1 AND ?2 GROUP BY ol.module")
List<Object[]> countByModuleAndCreatedAtBetween(LocalDateTime start, LocalDateTime end);
```

---

## 七、测试与验证

### 7.1 测试xxl-job任务

1. **启动项目**
2. **访问xxl-job调度中心**：http://localhost:8080/xxl-job-admin
3. **创建执行器**：
   - 执行器名称：`empmgmt-executor`
   - 注册方式：自动注册
4. **创建任务**：
   - JobHandler：`employeeDailyReport`
   - 调度类型：CRON
   - Cron表达式：`0 0 2 * * ?`（每天凌晨2点）
   - 运行模式：BEAN
5. **执行一次**：点击"执行一次"按钮测试

### 7.2 测试ES查询

```bash
# 查询所有报表
curl -X GET "http://localhost:9200/report_index/_search?pretty"

# 查询特定类型的报表
curl -X GET "http://localhost:9200/report_index/_search?pretty" -H 'Content-Type: application/json' -d'
{
  "query": {
    "term": {
      "reportType": "EMPLOYEE_STAT"
    }
  }
}'
```

### 7.3 测试多线程性能

```java
// 创建测试Controller
@RestController
@RequestMapping("/api/test")
public class PerformanceTestController {

    @Autowired
    private ReportService reportService;

    @GetMapping("/parallel-report")
    public Result<String> testParallelReport() {
        long start = System.currentTimeMillis();
        
        // 并行生成多个报表
        CompletableFuture<String> future1 = CompletableFuture.supplyAsync(
            () -> reportService.generateEmployeeReport(LocalDateTime.now(), "DAILY")
        );
        CompletableFuture<String> future2 = CompletableFuture.supplyAsync(
            () -> reportService.generateUserReport(LocalDateTime.now(), "DAILY")
        );
        
        CompletableFuture.allOf(future1, future2).join();
        
        long end = System.currentTimeMillis();
        return Result.success("并行执行完成，耗时：" + (end - start) + "ms");
    }
}
```

---

## 八、常见问题

### 8.1 xxl-job执行器注册失败

**问题**：执行器无法注册到调度中心

**解决方案**：
1. 检查网络连接
2. 确认调度中心地址正确
3. 检查执行器端口是否被占用
4. 查看执行器日志：`./logs/xxl-job/xxl-job.log`

### 8.2 ES连接失败

**问题**：无法连接到Elasticsearch

**解决方案**：
1. 确认ES服务已启动：`curl http://localhost:9200`
2. 检查配置文件中的ES地址
3. 如果ES开启了安全认证，需要配置用户名密码

### 8.3 线程池拒绝任务

**问题**：线程池队列满了，任务被拒绝

**解决方案**：
1. 增加队列容量
2. 增加最大线程数
3. 调整拒绝策略
4. 监控线程池状态，及时发现问题

---

## 九、监控与优化

### 9.1 线程池监控

```java
@Component
@Slf4j
public class ThreadPoolMonitor {

    @Autowired
    @Qualifier("exportExecutor")
    private ThreadPoolExecutor exportExecutor;

    @Scheduled(fixedRate = 60000)  // 每分钟监控一次
    public void monitorThreadPool() {
        log.info("Export线程池状态 - 核心线程数：{}，当前线程数：{}，活跃线程数：{}，队列大小：{}，已完成任务数：{}",
            exportExecutor.getCorePoolSize(),
            exportExecutor.getPoolSize(),
            exportExecutor.getActiveCount(),
            exportExecutor.getQueue().size(),
            exportExecutor.getCompletedTaskCount());
    }
}
```

### 9.2 ES性能优化

1. **批量写入**：使用`bulk` API批量写入数据
2. **索引优化**：合理设置分片数和副本数
3. **查询优化**：使用合适的查询类型，避免深度分页

---

## 十、总结

通过以上实现，你的项目将具备：

✅ **定时任务调度** - 使用xxl-job实现报表自动生成
✅ **搜索引擎** - 使用ES存储和查询报表数据
✅ **多线程优化** - 提升数据处理性能
✅ **可扩展性** - 易于添加新的报表类型和任务

**下一步建议**：
1. 实现报表Excel导出功能
2. 添加报表可视化接口（前端图表展示）
3. 实现报表邮件通知功能
4. 添加报表数据对比分析功能

