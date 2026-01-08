1# RBAC 权限系统设计方案

## 📋 目录

1. [系统概述](#系统概述)
2. [权限等级设计](#权限等级设计)
3. [数据库设计](#数据库设计)
4. [后端实现](#后端实现)
5. [前端实现](#前端实现)
6. [实现步骤](#实现步骤)
7. [测试方案](#测试方案)

---

## 1. 系统概述

### 1.1 设计模型

本系统基于 **RBAC (Role-Based Access Control)** 模型实现，核心思想是：

```
用户 (User) ←→ 角色 (Role) ←→ 权限 (Permission)
```

### 1.2 权限等级

| 角色 | 代码 | 权限范围 |
|------|------|----------|
| 超级管理员 | `SUPER_ADMIN` | 所有权限 |
| 部门经理 | `MANAGER` | 本部门数据 |
| 普通员工 | `EMPLOYEE` | 个人数据 |

---

## 2. 权限等级设计

### 2.1 超级管理员（SUPER_ADMIN）

**权限列表：**
- ✅ 查看所有员工信息
- ✅ 创建/编辑/删除任何员工
- ✅ 管理用户账号（创建、禁用、分配角色）
- ✅ 查看所有操作日志
- ✅ 查看全公司统计数据
- ✅ 导出所有数据
- ✅ 修改系统配置

### 2.2 部门经理（MANAGER）

**权限列表：**
- ✅ 查看本部门员工信息
- ✅ 创建/编辑本部门员工（需验证部门）
- ✅ 删除本部门员工
- ⚠️ 不能修改其他部门员工
- ✅ 查看本部门统计数据
- ✅ 导出本部门员工数据
- ✅ 查看本部门操作日志
- ❌ 不能管理用户账号

### 2.3 普通员工（EMPLOYEE）

**权限列表：**
- ✅ 查看自己的员工信息
- ✅ 修改个人信息（姓名、性别、年龄、头像）
- ❌ 不能修改薪资、部门、职位
- ❌ 不能删除数据
- ❌ 不能查看其他员工信息
- ✅ 查看自己的操作日志
- ❌ 不能导出数据

---

## 3. 数据库设计

### 3.1 修改现有表结构

#### 3.1.1 用户表 (user_account) - 添加角色字段

```sql
-- 为 user_account 表添加角色和部门字段
ALTER TABLE user_account ADD COLUMN role VARCHAR(50) DEFAULT 'EMPLOYEE';
ALTER TABLE user_account ADD COLUMN department VARCHAR(100);
ALTER TABLE user_account ADD COLUMN employee_id BIGINT;

-- 添加外键约束（可选）
ALTER TABLE user_account ADD CONSTRAINT fk_employee 
    FOREIGN KEY (employee_id) REFERENCES employee(id);

-- 添加索引
CREATE INDEX idx_user_role ON user_account(role);
CREATE INDEX idx_user_department ON user_account(department);

-- 添加注释
COMMENT ON COLUMN user_account.role IS '角色：SUPER_ADMIN/MANAGER/EMPLOYEE';
COMMENT ON COLUMN user_account.department IS '所属部门（部门经理使用）';
COMMENT ON COLUMN user_account.employee_id IS '关联的员工ID（员工角色使用）';
```

#### 3.1.2 员工表 (employee) - 已有字段，无需修改

```sql
-- employee 表已有 department 字段，无需修改
-- 确保有以下字段：
-- - id: 员工ID
-- - department: 部门
-- - created_by: 创建人
-- - updated_by: 更新人
```

### 3.2 新建权限相关表

#### 3.2.1 角色表 (role)

```sql
CREATE TABLE role (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(50) UNIQUE NOT NULL,           -- 角色代码：SUPER_ADMIN/MANAGER/EMPLOYEE
    name VARCHAR(100) NOT NULL,                  -- 角色名称：超级管理员/部门经理/普通员工
    description VARCHAR(500),                    -- 角色描述
    level INT NOT NULL DEFAULT 0,                -- 角色等级：1-超管, 2-经理, 3-员工
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP
);

-- 初始化角色数据
INSERT INTO role (code, name, description, level) VALUES
('SUPER_ADMIN', '超级管理员', '拥有系统所有权限', 1),
('MANAGER', '部门经理', '管理本部门员工和数据', 2),
('EMPLOYEE', '普通员工', '只能查看和修改个人信息', 3);
```

#### 3.2.2 权限表 (permission)

```sql
CREATE TABLE permission (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(100) UNIQUE NOT NULL,           -- 权限代码：employee:create
    name VARCHAR(100) NOT NULL,                  -- 权限名称：创建员工
    resource VARCHAR(100) NOT NULL,              -- 资源：employee/user/log
    action VARCHAR(50) NOT NULL,                 -- 操作：create/read/update/delete
    description VARCHAR(500),                    -- 描述
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 初始化权限数据
INSERT INTO permission (code, name, resource, action, description) VALUES
-- 员工管理权限
('employee:create', '创建员工', 'employee', 'create', '创建新员工信息'),
('employee:read', '查看员工', 'employee', 'read', '查看员工信息'),
('employee:update', '编辑员工', 'employee', 'update', '编辑员工信息'),
('employee:delete', '删除员工', 'employee', 'delete', '删除员工信息'),
('employee:export', '导出员工', 'employee', 'export', '导出员工数据'),

-- 用户管理权限
('user:create', '创建用户', 'user', 'create', '创建系统用户'),
('user:read', '查看用户', 'user', 'read', '查看用户信息'),
('user:update', '编辑用户', 'user', 'update', '编辑用户信息'),
('user:delete', '删除用户', 'user', 'delete', '删除用户账号'),
('user:assign_role', '分配角色', 'user', 'assign_role', '为用户分配角色'),

-- 日志权限
('log:read', '查看日志', 'log', 'read', '查看操作日志'),
('log:export', '导出日志', 'log', 'export', '导出日志数据'),

-- 统计权限
('stats:read', '查看统计', 'stats', 'read', '查看统计数据');
```

#### 3.2.3 角色权限关联表 (role_permission)

```sql
CREATE TABLE role_permission (
    id BIGSERIAL PRIMARY KEY,
    role_id BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_role FOREIGN KEY (role_id) REFERENCES role(id) ON DELETE CASCADE,
    CONSTRAINT fk_permission FOREIGN KEY (permission_id) REFERENCES permission(id) ON DELETE CASCADE,
    UNIQUE(role_id, permission_id)
);

-- 创建索引
CREATE INDEX idx_role_permission_role ON role_permission(role_id);
CREATE INDEX idx_role_permission_permission ON role_permission(permission_id);

-- 为超级管理员分配所有权限
INSERT INTO role_permission (role_id, permission_id)
SELECT 1, id FROM permission;

-- 为部门经理分配权限
INSERT INTO role_permission (role_id, permission_id)
SELECT 2, id FROM permission 
WHERE code IN (
    'employee:create', 'employee:read', 'employee:update', 'employee:delete', 'employee:export',
    'log:read', 'stats:read'
);

-- 为普通员工分配权限
INSERT INTO role_permission (role_id, permission_id)
SELECT 3, id FROM permission 
WHERE code IN ('employee:read', 'employee:update', 'log:read');
```

---

## 4. 后端实现

### 4.1 领域模型（Domain）

#### 4.1.1 角色枚举 `RoleEnum.java`

```java
package com.example.empmgmt.enums;

import lombok.Getter;

/**
 * 角色枚举
 */
@Getter
public enum RoleEnum {
    SUPER_ADMIN("SUPER_ADMIN", "超级管理员", 1),
    MANAGER("MANAGER", "部门经理", 2),
    EMPLOYEE("EMPLOYEE", "普通员工", 3);

    private final String code;
    private final String name;
    private final int level;

    RoleEnum(String code, String name, int level) {
        this.code = code;
        this.name = name;
        this.level = level;
    }

    /**
     * 根据代码获取角色
     */
    public static RoleEnum fromCode(String code) {
        for (RoleEnum role : values()) {
            if (role.code.equals(code)) {
                return role;
            }
        }
        throw new IllegalArgumentException("未知角色: " + code);
    }

    /**
     * 判断是否有更高权限
     */
    public boolean hasHigherLevelThan(RoleEnum other) {
        return this.level < other.level;  // level 越小权限越高
    }
}
```

#### 4.1.2 修改 User 实体 `User.java`

```java
package com.example.empmgmt.domain;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "user_account")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String username;

    @Column(nullable = false)
    private String password;

    private String email;

    // 新增字段
    @Column(name = "role", length = 50)
    private String role = "EMPLOYEE";  // 默认为普通员工

    @Column(name = "department", length = 100)
    private String department;  // 部门（部门经理使用）

    @Column(name = "employee_id")
    private Long employeeId;  // 关联的员工ID（员工角色使用）

    @Column(name = "enabled")
    private Boolean enabled = true;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
```

#### 4.1.3 Permission 实体 `Permission.java`

```java
package com.example.empmgmt.domain;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "permission")
public class Permission {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 100)
    private String code;  // 权限代码：employee:create

    @Column(nullable = false, length = 100)
    private String name;  // 权限名称

    @Column(nullable = false, length = 100)
    private String resource;  // 资源：employee/user/log

    @Column(nullable = false, length = 50)
    private String action;  // 操作：create/read/update/delete

    @Column(length = 500)
    private String description;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();
}
```

### 4.2 权限注解

#### 4.2.1 权限检查注解 `RequiresPermission.java`

```java
package com.example.empmgmt.common.annotation;

import java.lang.annotation.*;

/**
 * 权限检查注解
 * 用于方法级别的权限控制
 *
 * 使用示例：
 * @RequiresPermission("employee:create")
 * @RequiresPermission(value = "employee:update", checkDepartment = true)
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequiresPermission {

   /**
    * 需要的权限代码
    */
   String value();

   /**
    * 是否检查部门权限（部门经理只能操作本部门数据）
    */
   boolean checkDepartment() default false;

   /**
    * 是否检查数据所有者（普通员工只能操作自己的数据）
    */
   boolean checkOwner() default false;
}
```

#### 4.2.2 角色检查注解 `RequiresRole.java`

```java
package com.example.empmgmt.common.annotation;

import java.lang.annotation.*;

/**
 * 角色检查注解
 * 用于方法级别的角色控制
 *
 * 使用示例：
 * @RequiresRole("SUPER_ADMIN")
 * @RequiresRole({"SUPER_ADMIN", "MANAGER"})
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequiresRole {

   /**
    * 需要的角色（满足其中一个即可）
    */
   String[] value();
}
```

### 4.3 权限服务

#### 4.3.1 权限服务接口 `PermissionService.java`

```java
package com.example.empmgmt.service;

import com.example.empmgmt.domain.User;
import java.util.Set;

public interface PermissionService {
    
    /**
     * 检查用户是否有指定权限
     */
    boolean hasPermission(Long userId, String permissionCode);
    
    /**
     * 检查用户是否有指定角色
     */
    boolean hasRole(Long userId, String roleCode);
    
    /**
     * 获取用户的所有权限代码
     */
    Set<String> getUserPermissions(Long userId);
    
    /**
     * 检查用户是否可以访问指定部门的数据
     */
    boolean canAccessDepartment(Long userId, String department);
    
    /**
     * 检查用户是否可以访问指定员工数据
     */
    boolean canAccessEmployee(Long userId, Long employeeId);
}
```

#### 4.3.2 权限服务实现 `PermissionServiceImpl.java`

```java
package com.example.empmgmt.service.impl;

import com.example.empmgmt.domain.User;
import com.example.empmgmt.domain.Employee;
import com.example.empmgmt.enums.RoleEnum;
import com.example.empmgmt.repository.UserRepository;
import com.example.empmgmt.repository.EmployeeRepository;
import com.example.empmgmt.service.PermissionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Set;

@Service
@Slf4j
public class PermissionServiceImpl implements PermissionService {

    private final UserRepository userRepository;
    private final EmployeeRepository employeeRepository;

    public PermissionServiceImpl(UserRepository userRepository,
                                 EmployeeRepository employeeRepository) {
        this.userRepository = userRepository;
        this.employeeRepository = employeeRepository;
    }

    @Override
    public boolean hasPermission(Long userId, String permissionCode) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null || !user.getEnabled()) {
            return false;
        }

        RoleEnum role = RoleEnum.fromCode(user.getRole());
        
        // 超级管理员拥有所有权限
        if (role == RoleEnum.SUPER_ADMIN) {
            return true;
        }

        // 根据角色判断权限
        return checkPermissionByRole(role, permissionCode);
    }

    @Override
    public boolean hasRole(Long userId, String roleCode) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null || !user.getEnabled()) {
            return false;
        }
        return user.getRole().equals(roleCode);
    }

    @Override
    public Set<String> getUserPermissions(Long userId) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null || !user.getEnabled()) {
            return new HashSet<>();
        }

        RoleEnum role = RoleEnum.fromCode(user.getRole());
        return getPermissionsByRole(role);
    }

    @Override
    public boolean canAccessDepartment(Long userId, String department) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null || !user.getEnabled()) {
            return false;
        }

        RoleEnum role = RoleEnum.fromCode(user.getRole());
        
        // 超级管理员可以访问所有部门
        if (role == RoleEnum.SUPER_ADMIN) {
            return true;
        }
        
        // 部门经理只能访问自己的部门
        if (role == RoleEnum.MANAGER) {
            return user.getDepartment() != null && 
                   user.getDepartment().equals(department);
        }
        
        // 普通员工不能访问其他部门
        return false;
    }

    @Override
    public boolean canAccessEmployee(Long userId, Long employeeId) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null || !user.getEnabled()) {
            return false;
        }

        RoleEnum role = RoleEnum.fromCode(user.getRole());
        
        // 超级管理员可以访问所有员工
        if (role == RoleEnum.SUPER_ADMIN) {
            return true;
        }

        Employee employee = employeeRepository.findById(employeeId).orElse(null);
        if (employee == null) {
            return false;
        }

        // 部门经理可以访问本部门员工
        if (role == RoleEnum.MANAGER) {
            return user.getDepartment() != null && 
                   user.getDepartment().equals(employee.getDepartment());
        }

        // 普通员工只能访问自己
        if (role == RoleEnum.EMPLOYEE) {
            return user.getEmployeeId() != null && 
                   user.getEmployeeId().equals(employeeId);
        }

        return false;
    }

    /**
     * 根据角色检查权限
     */
    private boolean checkPermissionByRole(RoleEnum role, String permissionCode) {
        Set<String> permissions = getPermissionsByRole(role);
        return permissions.contains(permissionCode);
    }

    /**
     * 获取角色的权限集合
     */
    private Set<String> getPermissionsByRole(RoleEnum role) {
        Set<String> permissions = new HashSet<>();
        
        switch (role) {
            case SUPER_ADMIN:
                // 超级管理员拥有所有权限
                permissions.add("employee:create");
                permissions.add("employee:read");
                permissions.add("employee:update");
                permissions.add("employee:delete");
                permissions.add("employee:export");
                permissions.add("user:create");
                permissions.add("user:read");
                permissions.add("user:update");
                permissions.add("user:delete");
                permissions.add("user:assign_role");
                permissions.add("log:read");
                permissions.add("log:export");
                permissions.add("stats:read");
                break;
                
            case MANAGER:
                // 部门经理权限
                permissions.add("employee:create");
                permissions.add("employee:read");
                permissions.add("employee:update");
                permissions.add("employee:delete");
                permissions.add("employee:export");
                permissions.add("log:read");
                permissions.add("stats:read");
                break;
                
            case EMPLOYEE:
                // 普通员工权限
                permissions.add("employee:read");
                permissions.add("employee:update");  // 仅限个人信息
                permissions.add("log:read");         // 仅限个人日志
                break;
        }
        
        return permissions;
    }
}
```

### 4.4 权限切面

#### 4.4.1 权限检查切面 `PermissionAspect.java`

```java
package com.example.empmgmt.common.aspect;

import com.example.empmgmt.common.annotation.RequiresPermission;
import com.example.empmgmt.common.annotation.RequiresRole;
import com.example.empmgmt.exception.PermissionDeniedException;
import com.example.empmgmt.service.PermissionService;
import com.example.empmgmt.common.util.SecurityUtil;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

/**
 * 权限检查切面
 * 拦截带有权限注解的方法，进行权限验证
 */
@Aspect
@Component
@Slf4j
public class PermissionAspect {

   private final PermissionService permissionService;

   public PermissionAspect(PermissionService permissionService) {
      this.permissionService = permissionService;
   }

   /**
    * 检查权限注解
    */
   @Before("@annotation(com.example.empmgmt.common.annotation.RequiresPermission)")
   public void checkPermission(JoinPoint joinPoint) {
      Long userId = SecurityUtil.getCurrentUserId();

      MethodSignature signature = (MethodSignature) joinPoint.getSignature();
      Method method = signature.getMethod();
      RequiresPermission annotation = method.getAnnotation(RequiresPermission.class);

      String permissionCode = annotation.value();

      // 检查基本权限
      if (!permissionService.hasPermission(userId, permissionCode)) {
         log.warn("用户 {} 没有权限 {}", userId, permissionCode);
         throw new PermissionDeniedException("没有权限执行此操作");
      }

      // 检查部门权限
      if (annotation.checkDepartment()) {
         checkDepartmentPermission(joinPoint, userId);
      }

      // 检查所有者权限
      if (annotation.checkOwner()) {
         checkOwnerPermission(joinPoint, userId);
      }
   }

   /**
    * 检查角色注解
    */
   @Before("@annotation(com.example.empmgmt.common.annotation.RequiresRole)")
   public void checkRole(JoinPoint joinPoint) {
      Long userId = SecurityUtil.getCurrentUserId();

      MethodSignature signature = (MethodSignature) joinPoint.getSignature();
      Method method = signature.getMethod();
      RequiresRole annotation = method.getAnnotation(RequiresRole.class);

      String[] roles = annotation.value();
      boolean hasRole = false;

      for (String role : roles) {
         if (permissionService.hasRole(userId, role)) {
            hasRole = true;
            break;
         }
      }

      if (!hasRole) {
         log.warn("用户 {} 没有所需角色", userId);
         throw new PermissionDeniedException("没有权限执行此操作");
      }
   }

   /**
    * 检查部门权限（部门经理只能操作本部门）
    */
   private void checkDepartmentPermission(JoinPoint joinPoint, Long userId) {
      Object[] args = joinPoint.getArgs();

      // 尝试从参数中提取部门信息
      for (Object arg : args) {
         if (arg instanceof String && isDepartmentField(arg.toString())) {
            String department = arg.toString();
            if (!permissionService.canAccessDepartment(userId, department)) {
               throw new PermissionDeniedException("只能操作本部门的数据");
            }
         }
      }
   }

   /**
    * 检查所有者权限（普通员工只能操作自己）
    */
   private void checkOwnerPermission(JoinPoint joinPoint, Long userId) {
      Object[] args = joinPoint.getArgs();

      // 尝试从参数中提取员工ID
      for (Object arg : args) {
         if (arg instanceof Long) {
            Long employeeId = (Long) arg;
            if (!permissionService.canAccessEmployee(userId, employeeId)) {
               throw new PermissionDeniedException("只能操作自己的数据");
            }
         }
      }
   }

   /**
    * 判断是否为部门字段
    */
   private boolean isDepartmentField(String value) {
      // 简单判断，实际应该更精确
      return value.matches("^[\\u4e00-\\u9fa5]{2,10}部$");
   }
}
```

### 4.5 自定义异常

#### 4.5.1 权限拒绝异常 `PermissionDeniedException.java`

```java
package com.example.empmgmt.exception;

/**
 * 权限拒绝异常
 */
public class PermissionDeniedException extends RuntimeException {
    
    public PermissionDeniedException(String message) {
        super(message);
    }
    
    public PermissionDeniedException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

#### 4.5.2 全局异常处理器（添加权限异常处理）

```java
package com.example.empmgmt.exception;

import com.example.empmgmt.dto.response.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * 处理权限拒绝异常
     */
    @ExceptionHandler(PermissionDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Result<Void> handlePermissionDeniedException(PermissionDeniedException e) {
        log.warn("权限拒绝: {}", e.getMessage());
        return Result.error(403, e.getMessage());
    }

    /**
     * 处理其他异常
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<Void> handleException(Exception e) {
        log.error("系统异常", e);
        return Result.error("系统异常: " + e.getMessage());
    }
}
```

### 4.6 Controller 改造示例

#### 4.6.1 员工 Controller 添加权限控制

```java
package com.example.empmgmt.controller;

import com.example.empmgmt.common.annotation.OperationLog;
import com.example.empmgmt.common.enums.OperationType;
import com.example.empmgmt.common.annotation.RequiresPermission;
import com.example.empmgmt.common.annotation.RequiresRole;
import com.example.empmgmt.dto.request.EmployeeCreateRequest;
import com.example.empmgmt.dto.request.EmployeeUpdateRequest;
import com.example.empmgmt.dto.response.EmployeeResponse;
import com.example.empmgmt.dto.response.PageResponse;
import com.example.empmgmt.dto.response.Result;
import com.example.empmgmt.service.EmployeeService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/employ")
public class EmployeeController {

   private final EmployeeService employeeService;

   public EmployeeController(EmployeeService employeeService) {
      this.employeeService = employeeService;
   }

   /**
    * 创建员工（需要创建权限）
    */
   @PostMapping
   @RequiresPermission(value = "employee:create", checkDepartment = true)
   @OperationLog(
           module = "EMPLOYEE",
           type = OperationType.CREATE,
           description = "创建员工",
           saveResult = true
   )
   public Result<EmployeeResponse> create(
           @Valid @RequestBody EmployeeCreateRequest request
   ) {
      EmployeeResponse response = employeeService.create(request);
      return Result.success(response);
   }

   /**
    * 查询员工列表（需要读取权限）
    */
   @GetMapping
   @RequiresPermission("employee:read")
   @OperationLog(
           module = "EMPLOYEE",
           type = OperationType.QUERY,
           description = "查询员工列表"
   )
   public Result<PageResponse<EmployeeResponse>> list(
           @RequestParam(required = false) String name,
           @RequestParam(required = false) String department,
           @RequestParam(defaultValue = "1") int page,
           @RequestParam(defaultValue = "10") int size
   ) {
      PageResponse<EmployeeResponse> pageResult =
              employeeService.pageQuery(name, department, page, size);
      return Result.success(pageResult);
   }

   /**
    * 根据ID查询员工（需要读取权限 + 所有者检查）
    */
   @GetMapping("/{id}")
   @RequiresPermission(value = "employee:read", checkOwner = true)
   @OperationLog(
           module = "EMPLOYEE",
           type = OperationType.QUERY,
           description = "根据ID查询员工"
   )
   public Result<EmployeeResponse> getById(@PathVariable Long id) {
      EmployeeResponse response = employeeService.findById(id);
      return Result.success(response);
   }

   /**
    * 更新员工（需要更新权限 + 部门检查）
    */
   @PutMapping("/{id}")
   @RequiresPermission(value = "employee:update", checkDepartment = true, checkOwner = true)
   @OperationLog(
           module = "EMPLOYEE",
           type = OperationType.UPDATE,
           description = "更新员工信息",
           saveResult = true
   )
   public Result<EmployeeResponse> update(
           @PathVariable Long id,
           @Valid @RequestBody EmployeeUpdateRequest request
   ) {
      EmployeeResponse response = employeeService.update(id, request);
      return Result.success(response);
   }

   /**
    * 删除员工（需要删除权限 + 部门检查）
    */
   @DeleteMapping("/{id}")
   @RequiresPermission(value = "employee:delete", checkDepartment = true)
   @OperationLog(
           module = "EMPLOYEE",
           type = OperationType.DELETE,
           description = "删除员工"
   )
   public Result<Void> delete(@PathVariable Long id) {
      employeeService.delete(id);
      return Result.success(null);
   }

   /**
    * 导出员工数据（需要导出权限）
    */
   @GetMapping("/export")
   @RequiresPermission(value = "employee:export", checkDepartment = true)
   @OperationLog(
           module = "EMPLOYEE",
           type = OperationType.QUERY,
           description = "导出员工数据"
   )
   public Result<String> export(
           @RequestParam(required = false) String department
   ) {
      // 导出逻辑
      return Result.success("导出成功");
   }
}
```

#### 4.6.2 用户管理 Controller

```java
package com.example.empmgmt.controller;

import com.example.empmgmt.common.annotation.RequiresRole;
import com.example.empmgmt.dto.request.UserCreateRequest;
import com.example.empmgmt.dto.response.Result;
import com.example.empmgmt.dto.response.UserResponse;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
public class UserController {

   /**
    * 获取用户列表（仅超级管理员）
    */
   @GetMapping
   @RequiresRole("SUPER_ADMIN")
   public Result<List<UserResponse>> list() {
      // 实现逻辑
      return Result.success(null);
   }

   /**
    * 创建用户（仅超级管理员）
    */
   @PostMapping
   @RequiresRole("SUPER_ADMIN")
   public Result<UserResponse> create(@RequestBody UserCreateRequest request) {
      // 实现逻辑
      return Result.success(null);
   }

   /**
    * 分配角色（仅超级管理员）
    */
   @PutMapping("/{id}/role")
   @RequiresRole("SUPER_ADMIN")
   public Result<Void> assignRole(
           @PathVariable Long id,
           @RequestParam String role
   ) {
      // 实现逻辑
      return Result.success(null);
   }
}
```

### 4.7 修改 JWT 工具类

#### 4.7.1 在 Token 中添加角色信息 `JwtUtil.java`

```java
package com.example.empmgmt.common.util;

import com.example.empmgmt.domain.User;
import io.jsonwebtoken.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Component
public class JwtUtil {

   @Value("${jwt.secret}")
   private String secret;

   @Value("${jwt.expiration}")
   private Long expiration;

   /**
    * 生成Token（包含角色信息）
    */
   public String generateToken(User user) {
      Map<String, Object> claims = new HashMap<>();
      claims.put("userId", user.getId());
      claims.put("username", user.getUsername());
      claims.put("role", user.getRole());  // 添加角色信息
      claims.put("department", user.getDepartment());  // 添加部门信息
      claims.put("employeeId", user.getEmployeeId());  // 添加员工ID

      return Jwts.builder()
              .setClaims(claims)
              .setSubject(user.getUsername())
              .setIssuedAt(new Date())
              .setExpiration(new Date(System.currentTimeMillis() + expiration))
              .signWith(getSignKey(), SignatureAlgorithm.HS256)
              .compact();
   }

   /**
    * 从Token中获取角色
    */
   public String getRoleFromToken(String token) {
      Claims claims = getClaimsFromToken(token);
      return claims.get("role", String.class);
   }

   /**
    * 从Token中获取部门
    */
   public String getDepartmentFromToken(String token) {
      Claims claims = getClaimsFromToken(token);
      return claims.get("department", String.class);
   }

   /**
    * 从Token中获取员工ID
    */
   public Long getEmployeeIdFromToken(String token) {
      Claims claims = getClaimsFromToken(token);
      Integer employeeId = claims.get("employeeId", Integer.class);
      return employeeId != null ? employeeId.longValue() : null;
   }

   // ... 其他方法保持不变
}
```

### 4.8 修改 SecurityUtil

#### 4.8.1 添加角色和部门获取方法

```java
package com.example.empmgmt.common.util;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Map;

public class SecurityUtil {

   /**
    * 获取当前用户ID
    */
   public static Long getCurrentUserId() {
      Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
      if (authentication != null && authentication.getPrincipal() instanceof Map) {
         @SuppressWarnings("unchecked")
         Map<String, Object> principal = (Map<String, Object>) authentication.getPrincipal();
         Number userId = (Number) principal.get("userId");
         return userId != null ? userId.longValue() : null;
      }
      return null;
   }

   /**
    * 获取当前用户名
    */
   public static String getCurrentUsername() {
      Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
      if (authentication != null && authentication.getPrincipal() instanceof Map) {
         @SuppressWarnings("unchecked")
         Map<String, Object> principal = (Map<String, Object>) authentication.getPrincipal();
         return (String) principal.get("username");
      }
      return null;
   }

   /**
    * 获取当前用户角色
    */
   public static String getCurrentUserRole() {
      Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
      if (authentication != null && authentication.getPrincipal() instanceof Map) {
         @SuppressWarnings("unchecked")
         Map<String, Object> principal = (Map<String, Object>) authentication.getPrincipal();
         return (String) principal.get("role");
      }
      return null;
   }

   /**
    * 获取当前用户部门
    */
   public static String getCurrentUserDepartment() {
      Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
      if (authentication != null && authentication.getPrincipal() instanceof Map) {
         @SuppressWarnings("unchecked")
         Map<String, Object> principal = (Map<String, Object>) authentication.getPrincipal();
         return (String) principal.get("department");
      }
      return null;
   }

   /**
    * 获取当前用户关联的员工ID
    */
   public static Long getCurrentEmployeeId() {
      Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
      if (authentication != null && authentication.getPrincipal() instanceof Map) {
         @SuppressWarnings("unchecked")
         Map<String, Object> principal = (Map<String, Object>) authentication.getPrincipal();
         Number employeeId = (Number) principal.get("employeeId");
         return employeeId != null ? employeeId.longValue() : null;
      }
      return null;
   }
}
```

---

## 5. 前端实现

### 5.1 类型定义

#### 5.1.1 用户类型 `types/index.ts`

```typescript
// 角色枚举
export enum Role {
  SUPER_ADMIN = 'SUPER_ADMIN',
  MANAGER = 'MANAGER',
  EMPLOYEE = 'EMPLOYEE'
}

// 用户信息（扩展）
export interface User {
  id: number
  username: string
  email?: string
  role: Role
  department?: string
  employeeId?: number
  enabled: boolean
  createdAt: string
}

// 权限信息
export interface Permission {
  id: number
  code: string
  name: string
  resource: string
  action: string
  description?: string
}
```

### 5.2 权限 Store

#### 5.2.1 权限状态管理 `stores/permissionStore.ts`

```typescript
import { create } from 'zustand'
import { persist } from 'zustand/middleware'

interface PermissionState {
  role: string | null
  department: string | null
  employeeId: number | null
  permissions: string[]
  
  setRole: (role: string) => void
  setDepartment: (department: string | null) => void
  setEmployeeId: (employeeId: number | null) => void
  setPermissions: (permissions: string[]) => void
  
  hasPermission: (permission: string) => boolean
  hasRole: (role: string | string[]) => boolean
  isAdmin: () => boolean
  isManager: () => boolean
  isEmployee: () => boolean
  
  clearPermission: () => void
}

export const usePermissionStore = create<PermissionState>()(
  persist(
    (set, get) => ({
      role: null,
      department: null,
      employeeId: null,
      permissions: [],

      setRole: (role) => set({ role }),
      setDepartment: (department) => set({ department }),
      setEmployeeId: (employeeId) => set({ employeeId }),
      setPermissions: (permissions) => set({ permissions }),

      hasPermission: (permission) => {
        const { permissions, role } = get()
        // 超级管理员拥有所有权限
        if (role === 'SUPER_ADMIN') return true
        return permissions.includes(permission)
      },

      hasRole: (roles) => {
        const { role } = get()
        if (!role) return false
        if (Array.isArray(roles)) {
          return roles.includes(role)
        }
        return role === roles
      },

      isAdmin: () => get().role === 'SUPER_ADMIN',
      isManager: () => get().role === 'MANAGER',
      isEmployee: () => get().role === 'EMPLOYEE',

      clearPermission: () => set({
        role: null,
        department: null,
        employeeId: null,
        permissions: []
      })
    }),
    {
      name: 'permission-storage'
    }
  )
)
```

### 5.3 权限组件

#### 5.3.1 权限控制组件 `components/PermissionGuard.tsx`

```typescript
import React from 'react'
import { usePermissionStore } from '../stores/permissionStore'

interface PermissionGuardProps {
  permission?: string
  role?: string | string[]
  fallback?: React.ReactNode
  children: React.ReactNode
}

/**
 * 权限控制组件
 * 根据权限或角色显示/隐藏内容
 */
const PermissionGuard: React.FC<PermissionGuardProps> = ({
  permission,
  role,
  fallback = null,
  children
}) => {
  const { hasPermission, hasRole } = usePermissionStore()

  // 检查权限
  if (permission && !hasPermission(permission)) {
    return <>{fallback}</>
  }

  // 检查角色
  if (role && !hasRole(role)) {
    return <>{fallback}</>
  }

  return <>{children}</>
}

export default PermissionGuard
```

#### 5.3.2 权限按钮组件 `components/PermissionButton.tsx`

```typescript
import React from 'react'
import { Button, ButtonProps } from 'antd'
import { usePermissionStore } from '../stores/permissionStore'

interface PermissionButtonProps extends ButtonProps {
  permission?: string
  role?: string | string[]
}

/**
 * 权限按钮组件
 * 根据权限显示/禁用按钮
 */
const PermissionButton: React.FC<PermissionButtonProps> = ({
  permission,
  role,
  children,
  ...props
}) => {
  const { hasPermission, hasRole } = usePermissionStore()

  // 检查权限
  if (permission && !hasPermission(permission)) {
    return null
  }

  // 检查角色
  if (role && !hasRole(role)) {
    return null
  }

  return <Button {...props}>{children}</Button>
}

export default PermissionButton
```

### 5.4 修改登录逻辑

#### 5.4.1 登录时保存权限信息 `pages/Login.tsx`

```typescript
import { useAuthStore } from '../stores/authStore'
import { usePermissionStore } from '../stores/permissionStore'

const Login: React.FC = () => {
  const { setAuth } = useAuthStore()
  const { setRole, setDepartment, setEmployeeId, setPermissions } = usePermissionStore()

  const handleLogin = async (values: LoginForm) => {
    try {
      const response = await login(values.username, values.password)
      
      // 保存认证信息
      setAuth(response.token, response.username)
      
      // 保存权限信息
      setRole(response.role)
      setDepartment(response.department)
      setEmployeeId(response.employeeId)
      setPermissions(response.permissions || [])

      message.success('登录成功')
      navigate('/')
    } catch (error) {
      message.error('登录失败')
    }
  }

  // ...
}
```

### 5.5 页面改造示例

#### 5.5.1 员工列表页面 `pages/EmployeeList.tsx`

```typescript
import React from 'react'
import { Button, Space } from 'antd'
import PermissionGuard from '../components/PermissionGuard'
import PermissionButton from '../components/PermissionButton'
import { usePermissionStore } from '../stores/permissionStore'

const EmployeeList: React.FC = () => {
  const { hasPermission, isAdmin, isManager } = usePermissionStore()

  return (
    <div>
      {/* 操作按钮区域 */}
      <Space style={{ marginBottom: 16 }}>
        {/* 只有有创建权限的用户才能看到此按钮 */}
        <PermissionButton
          type="primary"
          permission="employee:create"
          onClick={() => navigate('/employees/new')}
        >
          新增员工
        </PermissionButton>

        {/* 只有管理员和经理能导出 */}
        <PermissionButton
          permission="employee:export"
          onClick={handleExport}
        >
          导出数据
        </PermissionButton>
      </Space>

      {/* 员工列表表格 */}
      <Table
        columns={[
          // ... 其他列
          {
            title: '操作',
            key: 'action',
            render: (_, record) => (
              <Space>
                {/* 查看按钮 - 所有人可见 */}
                <Button type="link" onClick={() => handleView(record.id)}>
                  查看
                </Button>

                {/* 编辑按钮 - 需要更新权限 */}
                <PermissionGuard permission="employee:update">
                  <Button type="link" onClick={() => handleEdit(record.id)}>
                    编辑
                  </Button>
                </PermissionGuard>

                {/* 删除按钮 - 需要删除权限 */}
                <PermissionGuard permission="employee:delete">
                  <Button 
                    type="link" 
                    danger 
                    onClick={() => handleDelete(record.id)}
                  >
                    删除
                  </Button>
                </PermissionGuard>
              </Space>
            )
          }
        ]}
        dataSource={employees}
      />
    </div>
  )
}
```

#### 5.5.2 修改菜单显示 `components/Layout.tsx`

```typescript
import { usePermissionStore } from '../stores/permissionStore'

const Layout = () => {
  const { hasPermission, isAdmin } = usePermissionStore()

  // 根据权限动态生成菜单
  const menuItems: MenuProps['items'] = [
    {
      key: '/employees',
      icon: <TeamOutlined />,
      label: '员工管理',
    },
    {
      key: '/statistics',
      icon: <BarChartOutlined />,
      label: '数据统计',
    },
    {
      key: '/logs',
      icon: <FileTextOutlined />,
      label: '操作日志',
    },
    // 只有超级管理员能看到用户管理
    ...(isAdmin() ? [{
      key: '/users',
      icon: <UserOutlined />,
      label: '用户管理',
    }] : [])
  ]

  return (
    // ...
  )
}
```

---

## 6. 实现步骤

### 第一阶段：数据库准备（1-2小时）

1. **执行数据库脚本**
   ```bash
   # 连接到 PostgreSQL
   psql -U postgres -d your_database
   
   # 执行 SQL 脚本
   \i schema.sql
   ```

2. **验证数据**
   ```sql
   SELECT * FROM role;
   SELECT * FROM permission;
   SELECT * FROM role_permission;
   ```

3. **更新测试用户**
   ```sql
   -- 创建测试用户
   INSERT INTO user_account (username, password, role, department, email)
   VALUES 
   ('admin', '$2a$10$...', 'SUPER_ADMIN', NULL, 'admin@example.com'),
   ('manager1', '$2a$10$...', 'MANAGER', '技术部', 'manager1@example.com'),
   ('employee1', '$2a$10$...', 'EMPLOYEE', NULL, 'employee1@example.com');
   ```

### 第二阶段：后端实现（3-4小时）

1. **创建枚举和实体类**（30分钟）
   - `RoleEnum.java`
   - 修改 `User.java`
   - `Permission.java`

2. **创建注解**（30分钟）
   - `RequiresPermission.java`
   - `RequiresRole.java`

3. **实现权限服务**（1小时）
   - `PermissionService.java`
   - `PermissionServiceImpl.java`

4. **实现权限切面**（1小时）
   - `PermissionAspect.java`
   - `PermissionDeniedException.java`
   - 全局异常处理

5. **修改现有代码**（1小时）
   - 修改 `JwtUtil.java`
   - 修改 `SecurityUtil.java`
   - 在 Controller 方法上添加权限注解

6. **测试后端接口**（30分钟）
   - 使用 Postman 测试权限控制
   - 测试不同角色的访问权限

### 第三阶段：前端实现（2-3小时）

1. **创建类型定义**（20分钟）
   - 扩展 `types/index.ts`

2. **创建权限 Store**（30分钟）
   - `stores/permissionStore.ts`

3. **创建权限组件**（40分钟）
   - `components/PermissionGuard.tsx`
   - `components/PermissionButton.tsx`

4. **修改登录逻辑**（20分钟）
   - 在登录时保存权限信息

5. **改造页面**（1小时）
   - 修改 `EmployeeList.tsx`
   - 修改 `Layout.tsx`
   - 修改其他需要权限控制的页面

6. **测试前端功能**（30分钟）
   - 测试不同角色的页面显示
   - 测试按钮和菜单的权限控制

### 第四阶段：联调测试（1-2小时）

1. **创建测试账号**
   - 超级管理员账号
   - 部门经理账号（不同部门）
   - 普通员工账号

2. **测试场景**
   - 超级管理员：所有功能正常
   - 部门经理：只能操作本部门
   - 普通员工：只能查看和修改自己

3. **权限验证**
   - 跨部门操作被拒绝
   - 无权限操作被拒绝
   - 日志正常记录

---

## 7. 测试方案

### 7.1 单元测试场景

#### 测试用户数据

| 用户名 | 角色 | 部门 | 员工ID |
|--------|------|------|--------|
| admin | SUPER_ADMIN | - | - |
| manager_tech | MANAGER | 技术部 | - |
| manager_sales | MANAGER | 销售部 | - |
| employee1 | EMPLOYEE | - | 1 |
| employee2 | EMPLOYEE | - | 2 |

### 7.2 功能测试用例

#### 用例1：超级管理员权限

```
前置条件：使用 admin 账号登录
测试步骤：
1. 查看所有员工列表 ✅
2. 创建任意部门员工 ✅
3. 编辑任意部门员工 ✅
4. 删除任意部门员工 ✅
5. 查看所有操作日志 ✅
6. 管理用户账号 ✅
预期结果：所有操作成功
```

#### 用例2：部门经理权限

```
前置条件：使用 manager_tech 账号登录（技术部经理）
测试步骤：
1. 查看技术部员工列表 ✅
2. 创建技术部员工 ✅
3. 编辑技术部员工 ✅
4. 尝试编辑销售部员工 ❌ 403
5. 删除技术部员工 ✅
6. 尝试删除销售部员工 ❌ 403
7. 查看技术部操作日志 ✅
8. 尝试管理用户账号 ❌ 403
预期结果：只能操作本部门数据
```

#### 用例3：普通员工权限

```
前置条件：使用 employee1 账号登录（员工ID=1）
测试步骤：
1. 查看自己的信息 ✅
2. 修改自己的姓名 ✅
3. 尝试修改自己的薪资 ❌ 403
4. 尝试查看其他员工信息 ❌ 403
5. 尝试删除数据 ❌ 403
6. 查看自己的操作日志 ✅
预期结果：只能查看和修改自己的基本信息
```

### 7.3 API 测试示例

#### Postman 测试脚本

```javascript
// 1. 登录获取Token
POST http://localhost:8080/api/auth/login
{
  "username": "manager_tech",
  "password": "password"
}

// 保存返回的 token

// 2. 测试查看本部门员工（应该成功）
GET http://localhost:8080/api/employ?department=技术部
Authorization: Bearer {token}

// 3. 测试编辑其他部门员工（应该失败403）
PUT http://localhost:8080/api/employ/5
Authorization: Bearer {token}
{
  "name": "张三",
  "department": "销售部"  // 不是技术部
}

// 预期返回：403 Forbidden "只能操作本部门的数据"
```

---

## 8. 注意事项

### 8.1 安全建议

1. **密码加密**
   - 使用 BCrypt 加密密码
   - 密码强度验证

2. **Token 安全**
   - 设置合理的过期时间
   - 敏感操作需要二次验证

3. **SQL 注入防护**
   - 使用 JPA 参数化查询
   - 避免字符串拼接 SQL

4. **日志脱敏**
   - 操作日志不记录敏感信息（如密码）
   - 个人信息脱敏显示

### 8.2 性能优化

1. **权限缓存**
   - 使用 Redis 缓存用户权限
   - 减少数据库查询

2. **批量查询**
   - 使用 JOIN 减少查询次数
   - 分页查询大数据量

3. **索引优化**
   - 为常用查询字段添加索引
   - 定期分析慢查询

### 8.3 扩展性建议

1. **动态权限配置**
   - 支持从数据库加载权限规则
   - 支持运行时修改权限

2. **数据权限过滤**
   - 使用 JPA Specification 自动过滤数据
   - 支持更细粒度的数据权限

3. **权限审计**
   - 记录所有权限检查结果
   - 定期生成权限审计报告

---

## 9. 常见问题

### Q1: 如何添加新的权限？

```sql
-- 1. 在 permission 表中添加新权限
INSERT INTO permission (code, name, resource, action) 
VALUES ('employee:batch_import', '批量导入员工', 'employee', 'batch_import');

-- 2. 为角色分配权限
INSERT INTO role_permission (role_id, permission_id)
SELECT 1, id FROM permission WHERE code = 'employee:batch_import';
```

### Q2: 如何修改用户角色？

```sql
-- 方法1：直接更新数据库
UPDATE user_account SET role = 'MANAGER', department = '技术部' WHERE id = 5;

-- 方法2：通过超级管理员接口
PUT /api/users/5/role
{
  "role": "MANAGER",
  "department": "技术部"
}
```

### Q3: 普通员工如何修改部分字段？

在 `EmployeeService` 中添加字段检查：

```java
public void update(Long id, EmployeeUpdateRequest request) {
    String role = SecurityUtil.getCurrentUserRole();
    
    // 普通员工只能修改特定字段
    if ("EMPLOYEE".equals(role)) {
        if (request.getSalary() != null || 
            request.getDepartment() != null || 
            request.getPosition() != null) {
            throw new PermissionDeniedException("不能修改薪资、部门、职位信息");
        }
    }
    
    // 继续更新逻辑
}
```

---

## 10. 总结

本权限系统设计基于 RBAC 模型，实现了：

✅ **三级权限管理**（超管/经理/员工）  
✅ **细粒度权限控制**（方法级别）  
✅ **数据范围限制**（部门、个人）  
✅ **前后端权限同步**  
✅ **操作日志记录**  
✅ **安全性保障**  

核心优势：
- 🎯 **灵活扩展** - 可轻松添加新角色和权限
- 🔒 **安全可靠** - 多层防护，前后端双重验证
- 📊 **可追溯** - 所有权限操作都有日志
- 🚀 **易于维护** - 注解式权限控制，代码清晰

预计实现时间：**8-12 小时**

---

**文档版本**: v1.0  
**创建日期**: 2026-01-02  
**作者**: AI Assistant
