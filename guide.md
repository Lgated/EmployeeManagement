## 员工信息管理系统（Spring Boot + JPA + PostgreSQL + 存储过程/函数 + JWT）实践指南

以下内容从零开始，帮助你在本地快速落地一个包含员工 CRUD 与基于 PostgreSQL 函数统计分析的示例项目。示例代码偏向教学，可根据实际需要精简或增强。

---

### 1. 环境与前置条件
- JDK 17（或 11 以上）
- Maven 3.8+
- PostgreSQL 12+（推荐 14 或更高版本）
- PostgreSQL JDBC 驱动（postgresql），Maven 会自动下载
- Postman/Insomnia 便于调试 REST 接口

---

### 2. 项目结构建议（标准 MVC 架构）

```
EmployManagement/
├─ src/main/java/com/example/empmgmt/
│  ├─ EmpMgmtApplication.java        # Spring Boot 启动类（主类）
│  │
│  ├─ config/                        # 配置层
│  │   ├─ SecurityConfig.java        # Spring Security 配置
│  │   └─ JwtAuthFilter.java         # JWT 认证过滤器
│  │
│  ├─ domain/                        # 领域层（实体层）
│  │   └─ Employee.java              # JPA 实体类
│  │
│  ├─ dto/                           # 数据传输对象层
│  │   ├─ request/                   # 请求 DTO
│  │   │   ├─ EmployeeCreateRequest.java
│  │   │   └─ EmployeeUpdateRequest.java
│  │   └─ response/                  # 响应 DTO
│  │       ├─ EmployeeResponse.java
│  │       └─ DeptStatsResponse.java
│  │
│  ├─ repository/                    # 数据访问层（DAO 层）
│  │   └─ EmployeeRepository.java    # JPA Repository 接口
│  │
│  ├─ service/                       # 服务层（业务逻辑层）
│  │   ├─ EmployeeService.java       # 服务接口
│  │   └─ impl/                      # 服务实现类
│  │       └─ EmployeeServiceImpl.java
│  │
│  ├─ controller/                    # 控制器层（表现层）
│  │   ├─ EmployeeController.java
│  │   └─ AuthController.java
│  │
│  └─ util/                          # 工具类层
│      └─ JwtUtil.java               # JWT 工具类
│
└─ src/main/resources/
   ├─ application.yml                # 应用配置文件
   └─ db/migration/                  # 可选：数据库迁移脚本（Flyway/Liquibase）
```

**架构说明：**
- **Controller 层**：接收 HTTP 请求，参数校验，调用 Service，返回响应
- **Service 层**：业务逻辑处理，接口与实现分离，便于测试和扩展
- **Repository 层**：数据访问，封装数据库操作
- **Domain 层**：实体类，对应数据库表
- **DTO 层**：数据传输对象，request 用于接收请求，response 用于返回响应
- **Config 层**：配置类，如安全配置、过滤器等
- **Util 层**：工具类，通用功能

---

### 3. Maven 依赖（`pom.xml` 关键段）
```xml
<dependencies>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-jpa</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-security</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-validation</artifactId>
    </dependency>
    <dependency>
        <groupId>io.jsonwebtoken</groupId>
        <artifactId>jjwt-api</artifactId>
        <version>0.11.5</version>
    </dependency>
    <dependency>
        <groupId>io.jsonwebtoken</groupId>
        <artifactId>jjwt-impl</artifactId>
        <version>0.11.5</version>
        <scope>runtime</scope>
    </dependency>
    <dependency>
        <groupId>io.jsonwebtoken</groupId>
        <artifactId>jjwt-jackson</artifactId>
        <version>0.11.5</version>
        <scope>runtime</scope>
    </dependency>
    <dependency>
        <groupId>org.postgresql</groupId>
        <artifactId>postgresql</artifactId>
        <scope>runtime</scope>
    </dependency>
</dependencies>
```

---

### 4. 数据库建表与示例数据

**4.1 员工表（employee）**
```sql
-- 创建数据库（如果不存在）
-- CREATE DATABASE empmgmt;

-- 切换到数据库后执行以下 SQL
CREATE TABLE employee (
    id           BIGSERIAL PRIMARY KEY,
    name         VARCHAR(100) NOT NULL,
    gender       VARCHAR(10),
    age          INTEGER,
    department   VARCHAR(100),
    position     VARCHAR(100),
    hire_date    DATE,
    salary       NUMERIC(12,2),
    created_at   TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP
);

CREATE INDEX idx_emp_dept ON employee(department);

-- 插入示例数据
INSERT INTO employee(name, gender, age, department, position, hire_date, salary)
VALUES ('Alice', 'F', 28, '研发部', '工程师', '2020-03-01', 18000);
INSERT INTO employee(name, gender, age, department, position, hire_date, salary)
VALUES ('Bob', 'M', 32, '研发部', '高级工程师', '2018-07-15', 25000);
INSERT INTO employee(name, gender, age, department, position, hire_date, salary)
VALUES ('Carol', 'F', 30, '人事部', 'HR', '2019-10-10', 15000);
```

**4.2 用户表（user_account）**
```sql
-- 用户表（用于登录认证）
CREATE TABLE user_account (
    id           BIGSERIAL PRIMARY KEY,
    username     VARCHAR(50) UNIQUE NOT NULL,
    password     VARCHAR(255) NOT NULL,  -- 存储加密后的密码
    email        VARCHAR(100),
    enabled      BOOLEAN DEFAULT TRUE,   -- 账户是否启用
    created_at   TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP
);

CREATE INDEX idx_user_username ON user_account(username);

-- 插入默认管理员账户（密码：admin123，实际应使用 BCrypt 加密）
-- 注意：这里只是示例，实际密码应该使用 BCrypt 加密存储
-- BCrypt 加密后的 "admin123" 示例：$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iwK8pJ5C
INSERT INTO user_account(username, password, email, enabled)
VALUES ('admin', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iwK8pJ5C', 'admin@example.com', TRUE);
```

---

### 5. PostgreSQL 函数示例（替代存储过程）

PostgreSQL 使用函数（FUNCTION）来实现类似 Oracle 存储过程的功能。函数可以返回表、标量值等。

**1) 部门员工数量统计（返回表）**
```sql
CREATE OR REPLACE FUNCTION fn_dept_emp_count()
RETURNS TABLE(department VARCHAR, emp_count BIGINT) AS $$
BEGIN
  RETURN QUERY
    SELECT e.department, COUNT(*)::BIGINT
    FROM employee e
    GROUP BY e.department;
END;
$$ LANGUAGE plpgsql;
```

**2) 部门平均薪资计算（返回表）**
```sql
CREATE OR REPLACE FUNCTION fn_dept_avg_salary()
RETURNS TABLE(department VARCHAR, avg_salary NUMERIC) AS $$
BEGIN
  RETURN QUERY
    SELECT e.department, ROUND(AVG(e.salary), 2)
    FROM employee e
    GROUP BY e.department;
END;
$$ LANGUAGE plpgsql;
```

**3) 员工工龄计算（返回标量值，按年）**
```sql
CREATE OR REPLACE FUNCTION fn_emp_years(p_emp_id BIGINT)
RETURNS NUMERIC AS $$
DECLARE
  v_years NUMERIC;
BEGIN
  SELECT ROUND(
    EXTRACT(EPOCH FROM (CURRENT_DATE - e.hire_date)) / (365.25 * 24 * 3600),
    2
  )
  INTO v_years
  FROM employee e
  WHERE e.id = p_emp_id;
  
  RETURN COALESCE(v_years, 0);
END;
$$ LANGUAGE plpgsql;
```

**说明：**
- PostgreSQL 函数使用 `RETURNS TABLE` 返回多行数据，使用 `RETURNS 类型` 返回单个值
- `$$` 是 PostgreSQL 的美元引号，用于定义函数体
- `LANGUAGE plpgsql` 指定使用 PL/pgSQL 语言
- `EXTRACT(EPOCH FROM ...)` 计算时间差（秒），再转换为年
- `COALESCE` 处理 NULL 值

---

### 6. Spring Data JPA 实体与仓储

**6.1 员工实体**
`Employee.java`
```java
package com.example.empmgmt.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "employee")
public class Employee {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private String gender;
    private Integer age;
    private String department;
    private String position;

    @Column(name = "hire_date")
    private LocalDate hireDate;

    private BigDecimal salary;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // Getter 和 Setter 方法
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getGender() { return gender; }
    public void setGender(String gender) { this.gender = gender; }

    public Integer getAge() { return age; }
    public void setAge(Integer age) { this.age = age; }

    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }

    public String getPosition() { return position; }
    public void setPosition(String position) { this.position = position; }

    public LocalDate getHireDate() { return hireDate; }
    public void setHireDate(LocalDate hireDate) { this.hireDate = hireDate; }

    public BigDecimal getSalary() { return salary; }
    public void setSalary(BigDecimal salary) { this.salary = salary; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
```

`EmployeeRepository.java`
```java
package com.example.empmgmt.repository;

import com.example.empmgmt.domain.Employee;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface EmployeeRepository extends JpaRepository<Employee, Long> {
    List<Employee> findByNameContainingIgnoreCase(String name);
    List<Employee> findByDepartment(String department);
}
```

---

### 7. DTO 层（数据传输对象）

DTO 用于在不同层之间传输数据，分为请求 DTO 和响应 DTO。

**7.1 员工相关 DTO**

**7.1.1 请求 DTO**

`EmployeeCreateRequest.java`（创建员工请求）
```java
package com.example.empmgmt.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;

public record EmployeeCreateRequest(
        @NotBlank(message = "姓名不能为空")
        String name,
        
        String gender,
        
        @Positive(message = "年龄必须大于0")
        Integer age,
        
        @NotBlank(message = "部门不能为空")
        String department,
        
        String position,
        
        @NotNull(message = "入职日期不能为空")
        LocalDate hireDate,
        
        @NotNull(message = "薪资不能为空")
        @Positive(message = "薪资必须大于0")
        BigDecimal salary
) {}
```

`EmployeeUpdateRequest.java`（更新员工请求）
```java
package com.example.empmgmt.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;

public record EmployeeUpdateRequest(
        @NotBlank(message = "姓名不能为空")
        String name,
        
        String gender,
        
        @Positive(message = "年龄必须大于0")
        Integer age,
        
        @NotBlank(message = "部门不能为空")
        String department,
        
        String position,
        
        LocalDate hireDate,
        
        @Positive(message = "薪资必须大于0")
        BigDecimal salary
) {}
```

**7.1.2 响应 DTO**

`EmployeeResponse.java`（员工响应）
```java
package com.example.empmgmt.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record EmployeeResponse(
        Long id,
        String name,
        String gender,
        Integer age,
        String department,
        String position,
        LocalDate hireDate,
        BigDecimal salary,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    // 静态工厂方法：从实体转换为响应 DTO
    public static EmployeeResponse from(com.example.empmgmt.domain.Employee employee) {
        return new EmployeeResponse(
                employee.getId(),
                employee.getName(),
                employee.getGender(),
                employee.getAge(),
                employee.getDepartment(),
                employee.getPosition(),
                employee.getHireDate(),
                employee.getSalary(),
                employee.getCreatedAt(),
                employee.getUpdatedAt()
        );
    }
}
```

`DeptStatsResponse.java`（部门统计响应）
```java
package com.example.empmgmt.dto.response;

import java.math.BigDecimal;

public record DeptStatsResponse(
        String department,
        Long empCount,
        BigDecimal avgSalary
) {}
```

**7.2 认证相关 DTO**

**7.2.1 请求 DTO**

`LoginRequest.java`（登录请求）
```java
package com.example.empmgmt.dto.request;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank(message = "用户名不能为空")
        String username,
        
        @NotBlank(message = "密码不能为空")
        String password
) {}
```

`RegisterRequest.java`（注册请求）
```java
package com.example.empmgmt.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "用户名不能为空")
        @Size(min = 3, max = 50, message = "用户名长度必须在3-50之间")
        String username,
        
        @NotBlank(message = "密码不能为空")
        @Size(min = 6, max = 100, message = "密码长度必须在6-100之间")
        String password,
        
        @Email(message = "邮箱格式不正确")
        String email
) {}
```

**7.2.2 响应 DTO**

`AuthResponse.java`（认证响应）
```java
package com.example.empmgmt.dto.response;

public record AuthResponse(
        String token,
        String tokenType,
        Long expiresIn
) {
    public static AuthResponse of(String token, Long expiresIn) {
        return new AuthResponse(token, "Bearer", expiresIn);
    }
}
```

---

### 8. Service 层（服务接口与实现）

**8.1 员工服务**

**8.1.1 Service 接口**

`EmployeeService.java`（服务接口）

```java
package com.example.empmgmt.service;

import com.example.empmgmt.dto.response.DeptStatsResponse;
import com.example.empmgmt.dto.response.EmployeeResponse;

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
     * 根据 ID 查询员工
     */
    EmployeeResponse findById(Long id);

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
```

**8.1.2 Service 实现类**

`EmployeeServiceImpl.java`（服务实现）

```java
package com.example.empmgmt.service.impl;

import com.example.empmgmt.domain.Employee;
import com.example.empmgmt.dto.response.DeptStatsResponse;
import com.example.empmgmt.dto.response.EmployeeResponse;
import com.example.empmgmt.repository.EmployeeRepository;
import com.example.empmgmt.service.EmployeeService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@Transactional
public class EmployeeServiceImpl implements EmployeeService {

    private final EmployeeRepository repository;

    @PersistenceContext
    private EntityManager entityManager;

    public EmployeeServiceImpl(EmployeeRepository repository) {
        this.repository = repository;
    }

    @Override
    public EmployeeResponse create(EmployeeCreateRequest request) {
        Employee employee = new Employee();
        copyFromRequest(request, employee);
        Employee saved = repository.save(employee);
        return EmployeeResponse.from(saved);
    }

    @Override
    public EmployeeResponse update(Long id, EmployeeUpdateRequest request) {
        Employee employee = repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("员工不存在，ID: " + id));
        copyFromRequest(request, employee);
        Employee updated = repository.save(employee);
        return EmployeeResponse.from(updated);
    }

    @Override
    public void delete(Long id) {
        if (!repository.existsById(id)) {
            throw new EntityNotFoundException("员工不存在，ID: " + id);
        }
        repository.deleteById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public EmployeeResponse findById(Long id) {
        Employee employee = repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("员工不存在，ID: " + id));
        return EmployeeResponse.from(employee);
    }

    @Override
    @Transactional(readOnly = true)
    public List<EmployeeResponse> listAll() {
        return repository.findAll().stream()
                .map(EmployeeResponse::from)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<EmployeeResponse> search(String name, String department) {
        List<Employee> employees;
        if (name != null && !name.isBlank()) {
            employees = repository.findByNameContainingIgnoreCase(name);
        } else if (department != null && !department.isBlank()) {
            employees = repository.findByDepartment(department);
        } else {
            employees = repository.findAll();
        }
        return employees.stream()
                .map(EmployeeResponse::from)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")  // 抑制类型安全警告：createNativeQuery 返回原始类型
    public List<DeptStatsResponse> getDeptEmpCount() {
        // 调用 PostgreSQL 函数
        // 注意：createNativeQuery().getResultList() 返回原始类型 List，需要类型转换
        List<Object[]> rows = (List<Object[]>) entityManager
                .createNativeQuery("SELECT * FROM fn_dept_emp_count()")
                .getResultList();

        return rows.stream()
                .map(row -> new DeptStatsResponse(
                        (String) row[0],      // department
                        ((Number) row[1]).longValue(),  // empCount
                        null                   // avgSalary 为 null
                ))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")  // 抑制类型安全警告：createNativeQuery 返回原始类型
    public List<DeptStatsResponse> getDeptAvgSalary() {
        // 调用 PostgreSQL 函数
        // 注意：createNativeQuery().getResultList() 返回原始类型 List，需要类型转换
        List<Object[]> rows = (List<Object[]>) entityManager
                .createNativeQuery("SELECT * FROM fn_dept_avg_salary()")
                .getResultList();

        return rows.stream()
                .map(row -> new DeptStatsResponse(
                        (String) row[0],              // department
                        null,                         // empCount 为 null
                        (BigDecimal) row[1]           // avgSalary
                ))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal getEmpYears(Long id) {
        // 调用 PostgreSQL 函数
        Object result = entityManager
                .createNativeQuery("SELECT fn_emp_years(:id)")
                .setParameter("id", id)
                .getSingleResult();

        return result == null
                ? BigDecimal.ZERO
                : new BigDecimal(result.toString());
    }

    /**
     * 将请求 DTO 的数据复制到实体对象
     */
    private void copyFromRequest(EmployeeCreateRequest request, Employee employee) {
        employee.setName(request.name());
        employee.setGender(request.gender());
        employee.setAge(request.age());
        employee.setDepartment(request.department());
        employee.setPosition(request.position());
        employee.setHireDate(request.hireDate());
        employee.setSalary(request.salary());
    }

    /**
     * 将更新请求 DTO 的数据复制到实体对象
     */
    private void copyFromRequest(EmployeeUpdateRequest request, Employee employee) {
        employee.setName(request.name());
        employee.setGender(request.gender());
        employee.setAge(request.age());
        employee.setDepartment(request.department());
        employee.setPosition(request.position());
        if (request.hireDate() != null) {
            employee.setHireDate(request.hireDate());
        }
        employee.setSalary(request.salary());
    }
}
```

---

### 9. Controller 层（控制器）

`EmployeeController.java`（员工控制器）

```java
package com.example.empmgmt.controller;

import com.example.empmgmt.dto.response.DeptStatsResponse;
import com.example.empmgmt.dto.response.EmployeeResponse;
import com.example.empmgmt.service.EmployeeService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/employees")
public class EmployeeController {

    private final EmployeeService employeeService;

    public EmployeeController(EmployeeService employeeService) {
        this.employeeService = employeeService;
    }

    /**
     * 创建员工
     */
    @PostMapping
    public ResponseEntity<EmployeeResponse> create(
            @Valid @RequestBody EmployeeCreateRequest request) {
        EmployeeResponse response = employeeService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * 根据 ID 查询员工
     */
    @GetMapping("/{id}")
    public ResponseEntity<EmployeeResponse> getById(@PathVariable Long id) {
        EmployeeResponse response = employeeService.findById(id);
        return ResponseEntity.ok(response);
    }

    /**
     * 查询所有员工（支持按姓名或部门搜索）
     */
    @GetMapping
    public ResponseEntity<List<EmployeeResponse>> list(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String department) {
        List<EmployeeResponse> responses = employeeService.search(name, department);
        return ResponseEntity.ok(responses);
    }

    /**
     * 更新员工
     */
    @PutMapping("/{id}")
    public ResponseEntity<EmployeeResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody EmployeeUpdateRequest request) {
        EmployeeResponse response = employeeService.update(id, request);
        return ResponseEntity.ok(response);
    }

    /**
     * 删除员工
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        employeeService.delete(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * 统计各部门员工数量
     */
    @GetMapping("/stats/dept-count")
    public ResponseEntity<List<DeptStatsResponse>> getDeptCount() {
        List<DeptStatsResponse> responses = employeeService.getDeptEmpCount();
        return ResponseEntity.ok(responses);
    }

    /**
     * 统计各部门平均薪资
     */
    @GetMapping("/stats/dept-avg-salary")
    public ResponseEntity<List<DeptStatsResponse>> getDeptAvgSalary() {
        List<DeptStatsResponse> responses = employeeService.getDeptAvgSalary();
        return ResponseEntity.ok(responses);
    }

    /**
     * 计算员工工龄
     */
    @GetMapping("/{id}/years")
    public ResponseEntity<BigDecimal> getEmpYears(@PathVariable Long id) {
        BigDecimal years = employeeService.getEmpYears(id);
        return ResponseEntity.ok(years);
    }
}
```

**Controller 层职责说明：**
- 接收 HTTP 请求，进行参数校验（使用 `@Valid`）
- 调用 Service 层处理业务逻辑
- 将 Service 返回的结果封装为 HTTP 响应
- 处理 HTTP 状态码（201 Created、200 OK、204 No Content 等）
- 不包含业务逻辑，只负责请求/响应的转换

---

### 10. JWT 工具类与安全配置

**10.1 JWT 工具类**

`JwtUtil.java`
```java
package com.example.empmgmt.util;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtUtil {
    
    private final SecretKey key;
    private final long ttlMs;

    // 从配置文件读取密钥和过期时间（推荐方式）
    // 如果没有配置，使用默认值
    public JwtUtil(
            @Value("${jwt.secret:replace-with-256-bit-secret-key-xxxx}") String secret,
            @Value("${jwt.expiration:3600000}") long expiration) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.ttlMs = expiration;
    }

    /**
     * 生成 JWT Token
     */
    public String generateToken(String username) {
        Date now = new Date();
        return Jwts.builder()
                .subject(username)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + ttlMs))
                .signWith(key)
                .compact();
    }

    /**
     * 从 Token 中解析用户名
     */
    public String parseUsername(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload()
                    .getSubject();
        } catch (Exception e) {
            throw new IllegalArgumentException("无效的 Token", e);
        }
    }

    /**
     * 验证 Token 是否有效
     */
    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 获取 Token 过期时间（秒）
     */
    public Long getExpirationTime() {
        return ttlMs / 1000;
    }
}
```

**10.2 JWT 认证过滤器**

`JwtAuthFilter.java`（JWT 认证过滤器）
```java
package com.example.empmgmt.config;

import com.example.empmgmt.util.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {
    
    private final JwtUtil jwtUtil;

    public JwtAuthFilter(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        
        // 1. 从请求头中获取 Token
        String authHeader = request.getHeader("Authorization");
        
        // 2. 检查 Token 格式（Bearer <token>）
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7); // 提取 Token（去掉 "Bearer " 前缀）
            
            try {
                // 3. 解析 Token，获取用户名
                String username = jwtUtil.parseUsername(token);
                
                // 4. 验证 Token 是否有效
                if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                    // 5. 创建认证对象
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(
                                    username,
                                    null,  // 密码设为 null（JWT 不需要密码）
                                    List.of()  // 权限列表（这里为空，可根据需要添加）
                            );
                    
                    // 6. 设置认证详情
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    
                    // 7. 将认证信息存入 SecurityContext（Spring Security 的上下文）
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            } catch (Exception e) {
                // Token 无效或过期，继续执行（不设置认证信息）
                logger.error("JWT Token 验证失败: {}", e.getMessage());
            }
        }
        
        // 8. 继续执行过滤器链
        chain.doFilter(request, response);
    }
}
```

**10.3 Spring Security 配置**

`SecurityConfig.java`（安全配置类）
```java
package com.example.empmgmt.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {
    
    private final JwtAuthFilter jwtAuthFilter;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter) {
        this.jwtAuthFilter = jwtAuthFilter;
    }

    /**
     * 配置安全过滤器链
     * 这是 Spring Security 的核心配置方法
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // 1. 禁用 CSRF（跨站请求伪造）保护
            // 因为使用 JWT，不需要 CSRF 保护
            .csrf(AbstractHttpConfigurer::disable)
            
            // 2. 配置会话管理策略
            // STATELESS：无状态，不使用 Session（JWT 是无状态的）
            .sessionManagement(session -> 
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            
            // 3. 配置请求授权规则
            .authorizeHttpRequests(auth -> auth
                // 允许未认证访问的路径（登录、注册接口）
                .requestMatchers("/api/auth/**").permitAll()
                // 其他所有请求都需要认证
                .anyRequest().authenticated()
            )
            
            // 4. 添加 JWT 认证过滤器
            // 在 UsernamePasswordAuthenticationFilter 之前执行
            // 这样可以在 Spring Security 默认认证之前先验证 JWT
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        
        return http.build();
    }

    /**
     * 配置密码编码器
     * 使用 BCrypt 算法加密密码
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
```

**10.4 SecurityConfig 与 JwtAuthFilter 的关系详解**

**关系图（完整流程）：**
```
┌─────────────────────────────────────────────────────────────┐
│                    HTTP 请求到达服务器                        │
└──────────────────────────┬──────────────────────────────────┘
                           ↓
┌─────────────────────────────────────────────────────────────┐
│  SecurityFilterChain (由 SecurityConfig 配置)                │
│  ┌──────────────────────────────────────────────────────┐  │
│  │ 1. 检查请求路径权限规则                                │  │
│  │    - /api/auth/** → permitAll (允许匿名访问)          │  │
│  │    - 其他路径 → authenticated (需要认证)              │  │
│  └──────────────────────────────────────────────────────┘  │
└──────────────────────────┬──────────────────────────────────┘
                           ↓
┌─────────────────────────────────────────────────────────────┐
│  JwtAuthFilter (自定义 JWT 认证过滤器)                        │
│  ┌──────────────────────────────────────────────────────┐  │
│  │ 1. 从请求头提取 Token: Authorization: Bearer <token>│  │
│  │ 2. 验证 Token 有效性（签名、过期时间等）             │  │
│  │ 3. 解析 Token 获取用户名                              │  │
│  │ 4. 创建 Authentication 对象                          │  │
│  │ 5. 存入 SecurityContext                              │  │
│  └──────────────────────────────────────────────────────┘  │
│  ← SecurityConfig 通过 addFilterBefore() 插入此过滤器       │
└──────────────────────────┬──────────────────────────────────┘
                           ↓
┌─────────────────────────────────────────────────────────────┐
│  UsernamePasswordAuthenticationFilter (Spring Security 默认)  │
│  ┌──────────────────────────────────────────────────────┐  │
│  │ 检查 SecurityContext 中是否有认证信息                  │  │
│  │ 如果有，说明已通过 JwtAuthFilter 认证，继续执行       │  │
│  │ 如果没有，且路径需要认证，则拒绝访问（401）            │  │
│  └──────────────────────────────────────────────────────┘  │
└──────────────────────────┬──────────────────────────────────┘
                           ↓
┌─────────────────────────────────────────────────────────────┐
│                    Controller 处理请求                        │
│  ┌──────────────────────────────────────────────────────┐  │
│  │ 可以通过 SecurityContext 获取当前认证用户信息          │  │
│  │ 执行业务逻辑并返回响应                                  │  │
│  └──────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
```

**关键关系说明：**
- **SecurityConfig** 是"配置者"和"管理者"
  - 配置安全规则（哪些路径需要认证）
  - 配置过滤器链的顺序
  - 通过 `addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)` 
    将 JwtAuthFilter 插入到过滤器链中
  
- **JwtAuthFilter** 是"执行者"
  - 被 SecurityConfig 插入到过滤器链
  - 在 UsernamePasswordAuthenticationFilter 之前执行
  - 负责实际的 JWT 认证逻辑

**详细说明：**

1. **SecurityConfig 的作用**：
   - 配置 Spring Security 的安全策略
   - 定义哪些路径需要认证，哪些路径允许匿名访问
   - 配置会话管理策略（无状态）
   - 将 JwtAuthFilter 插入到过滤器链中
   - 提供密码编码器（BCrypt）

2. **JwtAuthFilter 的作用**：
   - 拦截每个 HTTP 请求
   - 从请求头中提取 JWT Token
   - 验证 Token 的有效性
   - 如果 Token 有效，将用户信息存入 SecurityContext
   - 让后续的过滤器知道用户已认证

3. **它们的关系（核心要点）**：
   
   **依赖关系：**
   ```
   SecurityConfig (配置类)
        ↓ 依赖注入
   JwtAuthFilter (过滤器)
        ↓ 通过 addFilterBefore() 插入
   SecurityFilterChain (过滤器链)
   ```
   
   **执行顺序：**
   ```
   1. SecurityConfig.filterChain() 被 Spring 调用
      → 配置安全规则
      → 将 JwtAuthFilter 插入过滤器链
   
   2. 每个 HTTP 请求到达时：
      → 先经过 SecurityFilterChain（检查路径权限）
      → 再经过 JwtAuthFilter（验证 Token）
      → 最后经过 UsernamePasswordAuthenticationFilter（检查认证状态）
      → 到达 Controller
   ```
   
   **代码层面的关系：**
   ```java
   // SecurityConfig.java
   @Configuration
   public class SecurityConfig {
       private final JwtAuthFilter jwtAuthFilter;  // ← 依赖注入
       
       @Bean
       public SecurityFilterChain filterChain(HttpSecurity http) {
           http.addFilterBefore(
               jwtAuthFilter,  // ← 将 JwtAuthFilter 插入过滤器链
               UsernamePasswordAuthenticationFilter.class
           );
           return http.build();
       }
   }
   ```

4. **完整的认证流程**：

   **登录流程：**
   ```
   1. 用户发送 POST /api/auth/login {username, password}
   2. AuthController 接收请求
   3. UserService 验证用户名和密码
   4. 如果验证成功，生成 JWT Token
   5. 返回 Token 给客户端
   ```

   **访问受保护资源流程（详细步骤）：**
   ```
   步骤 1: 客户端发送请求
   ┌─────────────────────────────────────┐
   │ GET /api/employees                  │
   │ Authorization: Bearer eyJhbGc...   │
   └──────────────┬──────────────────────┘
                  ↓
   步骤 2: 进入 SecurityFilterChain
   ┌─────────────────────────────────────┐
   │ SecurityConfig.filterChain() 执行   │
   │ - 检查路径: /api/employees          │
   │ - 匹配规则: anyRequest().authenticated() │
   │ - 结论: 需要认证                    │
   └──────────────┬──────────────────────┘
                  ↓
   步骤 3: 进入 JwtAuthFilter
   ┌─────────────────────────────────────┐
   │ doFilterInternal() 执行              │
   │ 1. 提取 Header: Authorization       │
   │ 2. 提取 Token: "Bearer eyJhbGc..."  │
   │ 3. 调用 jwtUtil.parseUsername()     │
   │ 4. 验证 Token 签名和过期时间         │
   │ 5. 创建 Authentication 对象          │
   │ 6. 存入 SecurityContext             │
   └──────────────┬──────────────────────┘
                  ↓
   步骤 4: 进入 UsernamePasswordAuthenticationFilter
   ┌─────────────────────────────────────┐
   │ 检查 SecurityContext                 │
   │ - 发现已有认证信息（来自 JwtAuthFilter）│
   │ - 认证通过，继续执行                 │
   └──────────────┬──────────────────────┘
                  ↓
   步骤 5: 到达 Controller
   ┌─────────────────────────────────────┐
   │ EmployeeController.list() 执行      │
   │ - 从 SecurityContext 获取用户信息   │
   │ - 调用 EmployeeService             │
   │ - 返回员工列表                      │
   └─────────────────────────────────────┘
   
   如果 Token 无效或缺失：
   ┌─────────────────────────────────────┐
   │ JwtAuthFilter 无法设置认证信息        │
   │ → SecurityContext 为空               │
   │ → UsernamePasswordAuthenticationFilter│
   │   检测到未认证                        │
   │ → 返回 401 Unauthorized              │
   └─────────────────────────────────────┘
   ```

5. **为什么需要两个类？**
   - **职责分离**：SecurityConfig 负责配置，JwtAuthFilter 负责执行
   - **可维护性**：修改安全策略只需改 SecurityConfig，修改认证逻辑只需改 JwtAuthFilter
   - **可测试性**：可以单独测试 JwtAuthFilter 的认证逻辑
   - **符合 Spring Security 的设计模式**：配置与实现分离

`AuthController.java`（认证控制器）
```java
package com.example.empmgmt.controller;

import com.example.empmgmt.dto.request.LoginRequest;
import com.example.empmgmt.dto.request.RegisterRequest;
import com.example.empmgmt.dto.response.AuthResponse;
import com.example.empmgmt.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    /**
     * 用户注册
     */
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        try {
            AuthResponse response = userService.register(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }

    /**
     * 用户登录
     */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        try {
            AuthResponse response = userService.login(request);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }
}
```

---

### 11. 应用配置 `application.yml`
```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/empmgmt
    username: postgres
    password: 123456
    driver-class-name: org.postgresql.Driver
  jpa:
    hibernate:
      ddl-auto: none
    show-sql: true
    properties:
      hibernate:
        format_sql: true
        dialect: org.hibernate.dialect.PostgreSQLDialect

server:
  port: 8080

# JWT 配置
jwt:
  secret: replace-with-256-bit-secret-key-xxxx  # 生产环境应使用更安全的密钥
  expiration: 3600000  # Token 过期时间（毫秒），默认 1 小时

logging:
  level:
    org.hibernate.SQL: debug
    org.hibernate.type.descriptor.sql.BasicBinder: trace
```

---

### 12. 关键实现要点与架构规范

**12.1 架构分层职责**

| 层级 | 职责 | 注意事项 |
|------|------|----------|
| **Controller** | 接收请求、参数校验、调用 Service、返回响应 | 不包含业务逻辑，只做请求/响应转换 |
| **Service（接口）** | 定义业务方法签名 | 面向接口编程，便于测试和扩展 |
| **Service（实现）** | 实现业务逻辑、事务管理 | 使用 `@Transactional` 管理事务 |
| **Repository** | 数据访问、封装数据库操作 | 继承 JpaRepository，提供基础 CRUD |
| **DTO** | 数据传输对象，分离请求和响应 | Request 用于接收，Response 用于返回 |
| **Domain** | 实体类，对应数据库表 | 只包含属性和 JPA 注解，不包含业务逻辑 |

**12.2 代码规范**

1. **包命名规范**：
   - `controller` - 控制器层
   - `service` - 服务接口层
   - `service.impl` - 服务实现层
   - `repository` - 数据访问层
   - `domain` - 实体层
   - `dto.request` - 请求 DTO
   - `dto.response` - 响应 DTO
   - `config` - 配置层
   - `util` - 工具类层

2. **类命名规范**：
   - Controller: `XxxController`
   - Service 接口: `XxxService`
   - Service 实现: `XxxServiceImpl`
   - Repository: `XxxRepository`
   - Entity: `Xxx`（无后缀）
   - Request DTO: `XxxRequest` 或 `XxxCreateRequest`、`XxxUpdateRequest`
   - Response DTO: `XxxResponse`

3. **方法命名规范**：
   - Service 接口方法使用动词开头：`create`、`update`、`delete`、`findById`、`listAll`
   - Controller 方法对应 HTTP 方法：`create`（POST）、`update`（PUT）、`delete`（DELETE）、`getById`（GET）

**12.3 关键实现要点**
- PostgreSQL 使用函数（FUNCTION）而非存储过程，返回表时使用 `RETURNS TABLE`，返回标量时使用 `RETURNS 类型`。
- JPA 调用 PostgreSQL 函数使用 `createNativeQuery("SELECT * FROM function_name()")`，比 Oracle 的 `StoredProcedureQuery` 更简单。
- **类型安全警告处理**：`createNativeQuery().getResultList()` 返回原始类型 `List`，需要类型转换：
  ```java
  @SuppressWarnings("unchecked")  // 抑制类型安全警告
  List<Object[]> rows = (List<Object[]>) entityManager
      .createNativeQuery("SELECT * FROM fn_dept_emp_count()")
      .getResultList();
  ```
  这是 JPA 的已知限制，使用 `@SuppressWarnings("unchecked")` 是标准做法。
- PostgreSQL 自增列使用 `BIGSERIAL` 或 `GENERATED BY DEFAULT AS IDENTITY`，JPA 使用 `GenerationType.IDENTITY`。
- 金额使用 `NUMERIC` 类型，对应 Java 的 `BigDecimal`；日期使用 `DATE`/`TIMESTAMP`，对应 `LocalDate`/`LocalDateTime`。
- PostgreSQL 函数参数使用命名参数（`:param`）或位置参数（`$1, $2`），JPA 推荐使用命名参数。
- Service 层使用接口与实现分离，便于单元测试和扩展。
- DTO 分为 Request 和 Response，实现请求与响应的分离，提高 API 的灵活性。
- Controller 层使用 `@Valid` 进行参数校验，需要添加 `spring-boot-starter-validation` 依赖。
- JWT 示例只做简单校验，生产需增加用户体系、密码加密、刷新令牌、异常处理。

---

### 13. Spring Boot 启动类
`EmpMgmtApplication.java`
```java
package com.example.empmgmt;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class EmpMgmtApplication {
    public static void main(String[] args) {
        SpringApplication.run(EmpMgmtApplication.class, args);
    }
}
```

---

### 14. 本地运行步骤

#### 13.1 连接 PostgreSQL 并执行 SQL 的几种方式

**方式一：使用 psql 命令行工具（推荐学习）**

1. **打开命令行**：
   - Windows: 按 `Win + R`，输入 `cmd` 或 `powershell`
   - 或者找到 PostgreSQL 安装目录下的 `psql.exe`

2. **连接数据库**：
   ```bash
   # 方式1：直接连接指定数据库
   psql -U postgres -d empmgmt
   
   # 方式2：先连接默认数据库，再切换
   psql -U postgres
   # 进入后执行：
   \c empmgmt
   ```

3. **执行 SQL**：
   - 直接粘贴第 4 节的建表 SQL 和第 5 节的函数 SQL
   - 每行以分号 `;` 结尾
   - 按 `Enter` 执行

4. **常用 psql 命令**：
   ```sql
   \l              -- 列出所有数据库
   \c database_name -- 切换到指定数据库
   \dt             -- 列出当前数据库的所有表
   \df             -- 列出所有函数
   \d table_name   -- 查看表结构
   \q              -- 退出 psql
   ```

**方式二：使用 IntelliJ IDEA 的 Database 工具**

1. **打开 Database 工具窗口**：
   - 右侧边栏点击 `Database` 图标
   - 或菜单：`View` → `Tool Windows` → `Database`

2. **添加数据源**：
   - 点击 `+` → `Data Source` → `PostgreSQL`
   - 填写连接信息：
     - Host: `localhost`
     - Port: `5432`
     - Database: `empmgmt`
     - User: `postgres`
     - Password: `123456`（你的密码）
   - 点击 `Test Connection` 测试连接
   - 点击 `OK` 保存

3. **执行 SQL**：
   - 右键数据库 → `New` → `Query Console`
   - 或直接打开 `Query.sql` 文件
   - 粘贴 SQL 语句，点击执行按钮（绿色三角形）

**方式三：使用 pgAdmin（图形化工具）**

1. 打开 pgAdmin（PostgreSQL 安装时通常自带）
2. 连接到服务器
3. 展开 `Databases` → `empmgmt` → `Schemas` → `public`
4. 右键 `public` → `Query Tool`
5. 粘贴 SQL 执行

#### 13.2 执行 SQL 脚本

在 `empmgmt` 数据库中依次执行：

1. **建表 SQL**（第 4 节）：
   ```sql
   CREATE TABLE employee (
       id           BIGSERIAL PRIMARY KEY,
       name         VARCHAR(100) NOT NULL,
       gender       VARCHAR(10),
       age          INTEGER,
       department   VARCHAR(100),
       position     VARCHAR(100),
       hire_date    DATE,
       salary       NUMERIC(12,2),
       created_at   TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
       updated_at   TIMESTAMP
   );
   
   CREATE INDEX idx_emp_dept ON employee(department);
   ```

2. **插入示例数据**（第 4 节）：
   ```sql
   INSERT INTO employee(name, gender, age, department, position, hire_date, salary)
   VALUES ('Alice', 'F', 28, '研发部', '工程师', '2020-03-01', 18000);
   INSERT INTO employee(name, gender, age, department, position, hire_date, salary)
   VALUES ('Bob', 'M', 32, '研发部', '高级工程师', '2018-07-15', 25000);
   INSERT INTO employee(name, gender, age, department, position, hire_date, salary)
   VALUES ('Carol', 'F', 30, '人事部', 'HR', '2019-10-10', 15000);
   ```

3. **创建函数**（第 5 节）：
   - 依次执行三个函数的 SQL 语句
3) **配置连接**：修改 `application.yml` 中的数据库连接信息（用户名、密码、端口等）。

4) **关于 `ddl-auto` 配置说明**：
   
   `ddl-auto` 控制 Hibernate 是否自动创建/更新数据库表结构，有以下选项：
   
   | 选项 | 说明 | 使用场景 |
   |------|------|----------|
   | `none` | 不自动创建或更新表 | **生产环境推荐**，手动管理表结构 |
   | `validate` | 只验证表结构，不修改 | 生产环境，确保实体与表一致 |
   | `update` | 自动更新表结构（添加列、索引等） | 开发环境，**不会删除列** |
   | `create` | 每次启动删除并重建表 | 开发/测试，**会丢失数据** |
   | `create-drop` | 启动时创建，关闭时删除 | 测试环境 |
   
   **为什么使用 `none` 而不是 `update`？**
   
   - ✅ **`none` 的优势**：
     - 完全控制表结构，避免意外修改
     - 适合使用存储过程/函数的场景（需要手动创建）
     - 生产环境更安全，不会自动变更数据库
     - 可以配合 Flyway/Liquibase 做版本管理
   
   - ⚠️ **`update` 的局限性**：
     - 只能添加列，不能删除列或修改列类型
     - 可能产生不一致的表结构
     - 对复杂约束（外键、索引）支持有限
     - 不适合有存储过程/函数的场景
   
   **建议**：
   - 开发阶段：可以用 `update` 快速迭代
   - 生产环境：必须用 `none` 或 `validate`，手动管理表结构
   - 本项目：使用 `none`，因为需要手动创建函数

5) **编译项目**：`mvn clean package -DskipTests`（首次会下载依赖）。

6) **启动应用**：运行 `EmpMgmtApplication` 主类，或执行 `mvn spring-boot:run`。
7) **测试接口**：
   - 登录获取 token：`POST /api/auth/login?username=admin&password=admin123`
   - 访问员工接口，携带 `Authorization: Bearer <token>`：
     - `POST /api/employees` 新增员工
     - `GET /api/employees?department=研发部` 按部门查询
     - `GET /api/employees` 查询所有员工
     - `PUT /api/employees/{id}` 更新员工
     - `DELETE /api/employees/{id}` 删除员工
     - `GET /api/employees/stats/dept-count` 部门人数统计
     - `GET /api/employees/stats/dept-avg-salary` 部门平均薪资
     - `GET /api/employees/{id}/years` 员工工龄

---

### 15. PostgreSQL 入门要点（速查）
- **连接串**：`jdbc:postgresql://host:5432/database`，默认端口 5432。
- **日期字面量**：`'2020-01-01'` 或 `DATE '2020-01-01'`。
- **当前日期时间**：`CURRENT_DATE`（日期），`CURRENT_TIMESTAMP`（时间戳），`NOW()`（时间戳）。
- **字符串拼接**：`||` 或 `CONCAT(str1, str2)`。
- **分页**：`LIMIT n OFFSET m` 或 `FETCH FIRST n ROWS ONLY`。
- **常用函数**：
  - `COALESCE(expr, default)` - 返回第一个非 NULL 值
  - `ROUND(numeric, digits)` - 四舍五入
  - `EXTRACT(field FROM timestamp)` - 提取日期时间部分
  - `AGE(timestamp1, timestamp2)` - 计算年龄/时间差
  - `TO_CHAR(value, format)` - 格式化输出
- **函数调试**：
  ```sql
  -- 测试函数
  SELECT * FROM fn_dept_emp_count();
  SELECT fn_emp_years(1);
  
  -- 查看函数定义
  \df fn_dept_emp_count
  
  -- 使用 DO 块测试
  DO $$
  DECLARE
    result NUMERIC;
  BEGIN
    result := fn_emp_years(1);
    RAISE NOTICE '工龄: %', result;
  END $$;
  ```
- **数据类型**：
  - `BIGSERIAL` / `SERIAL` - 自增整数
  - `VARCHAR(n)` / `TEXT` - 字符串
  - `NUMERIC(p, s)` - 精确数值
  - `DATE` / `TIMESTAMP` - 日期时间
  - `BOOLEAN` - 布尔值

---

### 16. 测试建议
- 单元测试：使用 `@DataJpaTest` 连接测试库验证存储过程调用与实体映射。  
- 集成测试：`@SpringBootTest` + `TestRestTemplate` 调接口，覆盖 JWT 校验、CRUD、统计接口。  
- 边界用例：空部门、工资为 null、极端日期（未来/历史）、无效 ID。

---

### 17. 架构优势说明

**为什么采用接口与实现分离？**

1. **便于测试**：可以轻松创建 Mock 实现进行单元测试
2. **易于扩展**：可以创建多个实现类（如缓存实现、数据库实现）
3. **解耦合**：Controller 只依赖接口，不依赖具体实现
4. **符合 SOLID 原则**：依赖倒置原则（DIP）

**为什么分离 Request 和 Response DTO？**

1. **灵活性**：请求和响应可以有不同的字段
2. **安全性**：响应可以隐藏敏感信息（如密码）
3. **版本控制**：API 升级时可以独立修改请求和响应
4. **清晰性**：明确区分输入和输出

**标准 MVC 架构的优势：**

- ✅ **职责清晰**：每层只负责自己的职责
- ✅ **易于维护**：修改某层不影响其他层
- ✅ **便于测试**：可以单独测试每一层
- ✅ **代码复用**：Service 可以被多个 Controller 调用
- ✅ **团队协作**：不同开发者可以并行开发不同层

---

### 18. 可选改进
- 使用 Flyway/Liquibase 管理表与存储过程版本。  
- 引入 MapStruct 做 DTO 映射（替代手动 copy 方法）。  
- 增加岗位/部门表并建立外键，做更规范的数据模型。  
- 对统计接口增加缓存（Redis）或异步任务。  
- 完善异常处理与统一响应格式（使用 `@ControllerAdvice`）。  
- 添加 API 文档（Swagger/OpenAPI）。  
- 使用 Lombok 简化实体类的 getter/setter。

---

将以上文件与代码片段整合到实际项目目录中即可快速得到一个可运行的示例。需要更完整的代码骨架时，可按上述结构创建文件并拷贝示例内容，再根据业务补充。

---

## Hibernate 懒加载问题深度解析

### 爷的开场白

CSDN的朋友们大家好！我是新来的Java练习生 CodeCodeBond！

这段时间呢，博主在学习Spring Boot + JPA，想做一个自己感兴趣的项目，结果遇到了一个经典的Hibernate问题——懒加载异常！（已经解决但想深入理解原理）

后面会介绍到我个人的一些思路和踩坑经历（能把懒加载问题彻底搞懂，以后遇到类似问题就不慌了嘿嘿嘿

老实求放过说明：博主对Hibernate底层知识仅仅是了解一下皮毛，哈哈哈精巧的ORM框架让像我这种黄毛小子短时间熟悉精通确实不是那么容易的，需要时间的沉淀（#沉淀 orz）

什么！连Hibernate的底层知识都不懂！居然还会用JPA做开发数据库操作？？？我想这也是框架的意义所在吧！将复杂繁琐的JDBC操作封装成简单好用且优雅的API，让开发者快速上手。（大多数Java混子curdBoy不也不知道Spring框架, MyBatis的底层么…os:真是自己骂自己）

### 简单聊聊我眼中懒加载是什么

懒加载（Lazy Loading）是Hibernate中一个优雅的性能优化机制，用于延迟加载关联数据，只有在真正需要时才查询数据库。懒加载被广泛应用于各类ORM框架，尤其是需要避免N+1查询问题的场景，如一对多、多对一关联查询等等。

懒加载在我的眼里，是Java领域内ORM性能优化的基石。在数据库查询优化的地位非常之高，有多高呢，有三四百层楼那么高吧。

可能身边的同学们还在用各种Eager Loading、N+1查询…的时候，还不知道懒加载在里面吧！（一种精通…咳咳，了解懒加载的快感油然而生~）

没错，我们熟知的Spring Data JPA，Hibernate等等底层都有懒加载的相关实现。Spring的JPA相关组件也是大量用到懒加载，比如@OneToMany、@ManyToOne等。足以说明懒加载的地位。

我认为懒加载有两大特点，延迟和代理。这使得Hibernate在查询大量数据时仍能保持良好的性能以及避免不必要的查询。

问题来了，不熟悉ORM编程的我在一开始上手十分困难，代理对象的机制和Session生命周期的管理让我感到十分难受，后来越来越被这种特点着迷，实际上Hibernate懒加载的代码实现的非常优雅。

它大量用到前人总结的设计模式，例如代理模式（Proxy Pattern）实现延迟加载，Session管理时的生命周期模式… 真正的让开发者自由扩展，优雅编程。

下面我来简单说说懒加载开发时的核心概念

### 核心概念

#### 1. Hibernate Session（会话）

Session 是 Hibernate 操作数据库的核心

- Session 是 Hibernate 操作数据库的核心
- 每个 Session 维护一个数据库连接
- Session 关闭后，数据库连接释放，无法再执行查询

#### 2. 懒加载（Lazy Loading）

- **定义**：关联数据在需要时才加载
- **目的**：减少不必要的查询，提升性能
- **实现**：使用代理对象（Proxy）

### 问题出现的完整流程

下面是我遇到懒加载问题的完整流程，有更好的理解欢迎讨论一起交流进步！

概括起来其实主要分为六步：

1. Controller 接收请求
2. Service 层查询数据库
3. Hibernate 查询过程（创建代理对象）
4. 事务结束，Session 关闭
5. Controller 返回，Jackson 序列化
6. 触发懒加载，导致异常

#### 步骤 1：Controller 接收请求

```java
@GetMapping("/conversations")
public Result<List<Conversation>> listConversations() {
    List<Conversation> conversations = chatService.listConversations();
    return Result.success(conversations);  // 返回给前端
}
```

#### 步骤 2：Service 层查询数据库

```java
@Transactional  // 开启事务，创建 Hibernate Session
public List<Conversation> listConversations() {
    return conversationRepository.findAll();  // 执行查询
}
// 方法结束，@Transactional 提交事务，Session 关闭！
```

#### 步骤 3：Hibernate 查询过程

Hibernate 执行的 SQL（简化）：
```sql
SELECT id, title, created_at FROM conversation;
```

此时：
- Conversation 对象的 id、title、createdAt 被加载（直接字段）
- messages 字段未加载（懒加载）
- Hibernate 创建一个代理对象（Proxy）填充到 messages 字段

内存中的对象状态：
```java
Conversation conversation = {
    id: 1,
    title: "测试会话",
    createdAt: 2025-12-17...,
    messages: <Proxy对象>  // ⚠️ 这是一个代理，不是真正的 List<Message>
}
```

#### 步骤 4：事务结束，Session 关闭

```java
// Service 方法执行完毕
public List<Conversation> listConversations() {
    return conversationRepository.findAll();
    // ← 这里 @Transactional 结束，事务提交
    // ← Hibernate Session 关闭！
    // ← 数据库连接释放！
}
```

**重要：**
- 此时 Conversation 对象已经脱离了 Hibernate Session
- messages 字段仍然是代理对象，还没有真正查询过数据库
- 但 Session 已经关闭，无法再执行数据库查询

#### 步骤 5：Controller 返回，Jackson 序列化

```java
@GetMapping("/conversations")
public Result<List<Conversation>> listConversations() {
    List<Conversation> conversations = chatService.listConversations();
    // ↑ 这里 conversations 中的每个对象的 messages 还是代理对象

    return Result.success(conversations);
    // ↑ Jackson 开始序列化这个对象为 JSON
}
```

Jackson 序列化过程：
```java
// Jackson 内部大致逻辑（简化）
for (Conversation conv : conversations) {
    json.writeField("id", conv.getId());           // ✅ 正常，直接字段
    json.writeField("title", conv.getTitle());     // ✅ 正常，直接字段
    json.writeField("messages", conv.getMessages()); // ❌ 出错了！
    //                ↑
    //         调用 getMessages()
    //         代理对象尝试初始化
    //         需要查询数据库
    //         但 Session 已经关闭！
}
```

#### 步骤 6：触发懒加载，导致异常

当 Jackson 调用 `conv.getMessages()` 时：

```java
// Hibernate 代理对象的内部逻辑（简化）
public List<Message> getMessages() {
    if (!initialized) {
        // 尝试初始化，需要查询数据库
        Session session = getCurrentSession();  // ❌ 返回 null！Session 已经关闭了
        if (session == null) {
            throw new LazyInitializationException("no Session");  // 抛出异常！
        }
        // 如果能获取到 Session，执行：
        // SELECT * FROM message WHERE conversation_id = ?
    }
    return realMessagesList;
}
```

异常信息：
```
org.hibernate.LazyInitializationException: 
failed to lazily initialize a collection of role: com.example.demo.domain.Conversation.messages: 
could not initialize proxy - no Session
```

### 为什么使用懒加载还会出问题？

#### 懒加载的设计初衷

```java
// 设计场景：在同一个 Session 内访问
@Transactional
public void processConversation(Long id) {
    Conversation conv = repository.findById(id);
    // Session 还在，可以访问
    List<Message> messages = conv.getMessages();  // ✅ 正常，会触发查询
    // 处理 messages...
}
// Session 关闭
```

#### 你的场景：跨 Session 访问

```java
// Service 层：Session 内
@Transactional
public List<Conversation> listConversations() {
    return repository.findAll();  // Session 内查询
}  // ← Session 关闭

// Controller 层：Session 外
public Result<List<Conversation>> listConversations() {
    List<Conversation> conversations = chatService.listConversations();
    // ↑ 此时 Session 已经关闭
    // ↑ conversations 对象的 messages 还是代理，无法初始化
    return Result.success(conversations);
}
```

**关键点：**
- Service 返回对象时，Session 已关闭
- Controller 和序列化发生在 Session 外
- 访问懒加载字段时，无法再查询数据库

### @JsonIgnore 如何解决这个问题？

#### 方案原理

```java
@Entity
public class Conversation {
    @OneToMany(fetch = FetchType.LAZY)
    @JsonIgnore  // ← 关键！
    private List<Message> messages;
}
```

Jackson 序列化时的行为：
```java
// Jackson 内部逻辑（简化）
for (Field field : entity.getFields()) {
    if (field.isAnnotationPresent(JsonIgnore.class)) {
        continue;  // 跳过这个字段，不序列化
    }
    // 序列化其他字段...
}
```

**效果：**
- @JsonIgnore 标记的字段不会被序列化
- Jackson 不会调用 getMessages()
- 代理对象不会被触发初始化
- 不会抛出异常

序列化结果：
```json
{
  "id": 1,
  "title": "测试会话",
  "createdAt": "2025-12-17T10:00:00"
  // messages 字段被忽略，不会出现在 JSON 中
}
```

### 其他解决方案的对比

#### 方案 A：使用 @JsonIgnore（推荐）

- **优点**：简单，性能好，避免额外查询
- **缺点**：前端拿不到 messages 字段
- **适用**：列表接口通常不需要关联数据

#### 方案 B：使用 DTO（灵活但复杂）

```java
// 优点：精确控制返回的数据结构
// 缺点：需要写转换代码，增加工作量
MessageDTO {
    Long id;
    String content;
    // 不包含 conversation 对象，只包含 conversationId
}
```

#### 方案 C：使用 @EntityGraph 主动加载（性能较差）

```java
@EntityGraph(attributePaths = {"messages"})
List<Conversation> findAll();
// 优点：一次查询加载所有数据
// 缺点：如果 messages 很多，会加载大量数据，影响性能
```

#### 方案 D：开启 open-in-view（不推荐）

```yaml
spring:
  jpa:
    open-in-view: true  # Session 会一直保持到视图渲染完成
```

- **缺点**：Session 生命周期过长，可能导致连接泄露和性能问题

### 总结

1. **懒加载的本质**：
   - 延迟加载关联数据，使用代理对象占位
   - 首次访问时才查询数据库

2. **问题的根源**：
   - Session 关闭后，代理对象无法初始化
   - 序列化时访问代理对象，触发懒加载失败

3. **解决思路**：
   - 避免在 Session 外访问懒加载字段（如 @JsonIgnore）
   - 或在 Session 内主动加载需要的数据（如 DTO 映射、@EntityGraph）

4. **最佳实践**：
   - 列表接口：使用 @JsonIgnore 忽略关联对象
   - 详情接口：如需关联数据，使用 DTO 或在查询时主动加载

这就是 Hibernate 懒加载问题的原理和流程。核心是：**Session 生命周期与对象序列化时机不匹配，导致代理对象无法初始化**。

（等我完善好哈，我一定要把更多ORM相关的坑都记录下来，做到简单理解，简单使用，简单避坑，覆盖大多数人的需求。/加油，xdm双击点波关注上车了喂）

