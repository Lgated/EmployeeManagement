# RBAC权限模型实战：从设计到落地

> 最近在做员工管理系统，遇到了一个经典问题：**如何优雅地控制不同角色的访问权限？**  
> 比如超级管理员能管理所有数据，部门经理只能管自己部门，普通员工只能看自己的信息...  
> 
> 经过一番折腾，最终选择了 **RBAC（基于角色的访问控制）** 模型，今天就来分享一下我的设计思路和实现过程！

---

## 【先贴个效果图~】

```
用户登录 → JWT认证 → 权限检查 → 业务执行
    ↓         ↓          ↓          ↓
  admin   解析角色    超管：全部    ✅ 成功
 manager  解析部门    经理：本部门  ✅ 成功
employee  解析员工ID  员工：仅自己  ✅ 成功
```

---

## 一、RBAC是什么？为什么选择它？

### 1.1 什么是RBAC？

**RBAC（Role-Based Access Control）**，翻译过来就是"基于角色的访问控制"。

简单来说，它的核心思想是：

```
用户 (User) → 角色 (Role) → 权限 (Permission)
```

**举个生活中的例子**：
- 👨‍💼 **老板**（角色）→ 可以看所有报表、发工资、开除员工（权限）
- 👔 **部门经理**（角色）→ 可以看本部门报表、管理本部门员工（权限）
- 👷 **普通员工**（角色）→ 只能看自己的工资条、修改个人信息（权限）

### 1.2 为什么选择RBAC？

在实现权限系统之前，我考虑过几种方案：

**方案1：直接给用户分配权限**
```
用户A → 权限1, 权限2, 权限3
用户B → 权限2, 权限4
用户C → 权限1, 权限3, 权限5
```
❌ **问题**：用户多了之后，权限管理会变得非常混乱！

**方案2：RBAC模型**
```
用户A → 角色：部门经理 → 权限：部门管理权限
用户B → 角色：部门经理 → 权限：部门管理权限
用户C → 角色：普通员工 → 权限：个人权限
```
✅ **优势**：
- 权限管理更清晰（角色统一管理）
- 新增用户时，只需要分配角色，不用一个个配权限
- 修改权限时，只需要修改角色，所有用户自动生效

**所以，我选择了RBAC！** 🎯

---

## 二、我的RBAC设计思路

### 2.1 核心关系图

```
┌─────────────────────────────────────────────────────────────┐
│                     RBAC 核心模型                            │
└─────────────────────────────────────────────────────────────┘

    ┌─────────┐         ┌─────────┐         ┌─────────────┐
    │  用户   │ 1     N │  角色   │ N     M │   权限      │
    │  User   ├────────>│  Role   ├────────>│ Permission  │
    └─────────┘  拥有   └─────────┘  包含   └─────────────┘
         │                   │                      │
    username              SUPER_ADMIN         employee:create
    password               MANAGER            employee:read
    email                  EMPLOYEE           employee:update
    role                      │               employee:delete
    department                │                      │
    employeeId                │                      │
                              │                      │
                    ┌─────────┴──────────┐          │
                    │                    │          │
            ┌───────┴────────┐  ┌────────┴──────┐  │
            │ 权限等级：1    │  │ 权限等级：2   │  │
            │ 所有权限       │  │ 本部门权限    │  │
            └────────────────┘  └───────────────┘  │
                                                    │
                                        ┌───────────┴──────────┐
                                        │ 权限等级：3          │
                                        │ 个人数据权限         │
                                        └──────────────────────┘
```

### 2.2 三级权限设计

在我的系统中，设计了**三个角色等级**：

| 角色 | 代码 | 权限范围 | 权限等级 |
|------|------|----------|----------|
| 超级管理员 | `SUPER_ADMIN` | 所有权限 | Level 1 |
| 部门经理 | `MANAGER` | 本部门数据 | Level 2 |
| 普通员工 | `EMPLOYEE` | 个人数据 | Level 3 |

**权限等级关系**：
```
Level 1 (超管) > Level 2 (经理) > Level 3 (员工)
```

### 2.3 权限矩阵

```
权限代码              │ SUPER_ADMIN │ MANAGER │ EMPLOYEE │ 说明
─────────────────────┼─────────────┼─────────┼──────────┼─────────
【员工管理】
employee:create      │      ✅     │   ✅    │    ❌    │ 创建员工
employee:read        │      ✅     │   ✅    │    ✅    │ 查看员工
employee:update      │      ✅     │   ✅    │    ⚠️    │ 编辑员工
employee:delete      │      ✅     │   ✅    │    ❌    │ 删除员工
employee:export      │      ✅     │   ✅    │    ❌    │ 导出数据

【用户管理】
user:create          │      ✅     │   ❌    │    ❌    │ 创建用户
user:read            │      ✅     │   ❌    │    ❌    │ 查看用户
user:update          │      ✅     │   ❌    │    ❌    │ 编辑用户
user:delete          │      ✅     │   ❌    │    ❌    │ 删除用户
user:assign_role     │      ✅     │   ❌    │    ❌    │ 分配角色

【日志管理】
log:read             │      ✅     │   ✅    │    ⚠️    │ 查看日志
log:export           │      ✅     │   ⚠️    │    ❌    │ 导出日志

【统计功能】
stats:read           │      ✅     │   ⚠️    │    ❌    │ 查看统计

图例:
  ✅ = 完全允许
  ⚠️  = 受限允许 (有数据范围限制)
  ❌ = 禁止
```

**特殊说明**：
- `employee:update` (EMPLOYEE)：⚠️ 只能修改自己的信息，且不能改薪资、部门、职位
- `employee:*` (MANAGER)：⚠️ 只能操作本部门的员工数据
- `log:read` (EMPLOYEE)：⚠️ 只能查看自己的操作日志

---

## 三、数据库设计

### 3.1 表结构关系

```
┌───────────────────┐         ┌───────────────────┐
│   user_account    │         │     employee      │
├───────────────────┤         ├───────────────────┤
│ • id (PK)        │         │ • id (PK)        │
│ • username       │         │ • name           │
│ • password       │         │ • department     │
│ • email          │         │ • position       │
│ • role           │───┐     │ • salary         │
│ • department     │   │     │ • hire_date      │
│ • employee_id    │───┼────>│ • avatar         │
│ • enabled        │   │     │ • created_by     │
│ • created_at     │   │     │ • updated_by     │
└───────────────────┘   │     │ • deleted        │
         │              │     └───────────────────┘
         │ FK           │              ▲
         ▼              │              │ FK
┌───────────────────┐   │              │
│  operation_log   │   │     ┌────────┴─────────┐
├───────────────────┤   │     │   数据范围检查    │
│ • id (PK)        │   │     │                  │
│ • user_id (FK)   │───┘     │ 部门经理：       │
│ • username       │         │ user.department  │
│ • operation_type │         │ = employee.dept   │
│ • module         │         │                  │
│ • description    │         │ 普通员工：       │
│ • method         │         │ user.employee_id │
│ • params         │         │ = employee.id    │
│ • result         │         └──────────────────┘
│ • ip_address     │
│ • execution_time │
│ • status         │
│ • created_at     │
└───────────────────┘
```

### 3.2 关键字段说明

**user_account 表**：
- `role`：角色（SUPER_ADMIN/MANAGER/EMPLOYEE）
- `department`：部门（部门经理使用）
- `employee_id`：关联的员工ID（普通员工使用）

**为什么这样设计？**
- 普通员工通过 `employee_id` 关联到员工表，可以快速定位"自己"
- 部门经理通过 `department` 字段，可以快速判断"是否本部门"
- 超级管理员不需要这些字段，因为可以访问所有数据

---

## 四、后端实现步骤

### 4.1 第一步：定义角色枚举

```java
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
     * 通过代码获取角色枚举
     */
    public static RoleEnum fromCode(String code) {
        for (RoleEnum role : values()) {
            if (role.getCode().equals(code)) {
                return role;
            }
        }
        throw new IllegalArgumentException("未知的角色代码: " + code);
    }

    /**
     * 判断是否有更高权限
     */
    public boolean hasHigherLevelThan(RoleEnum other) {
        return this.level < other.level;  // level 越小权限越高
    }
}
```

**设计思路**：
- 用枚举而不是数据库表，因为角色数量固定，不需要动态配置
- `level` 字段用于权限比较（超管=1，经理=2，员工=3）

### 4.2 第二步：创建权限注解

#### 4.2.1 权限检查注解

```java
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

**使用示例**：
```java
@PostMapping
@RequiresPermission(value = "employee:create", checkDepartment = true)
public Result<EmployeeResponse> create(@RequestBody EmployeeCreateRequest request) {
    // 创建员工逻辑
}
```

#### 4.2.2 角色检查注解

```java
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

**使用示例**：
```java
@GetMapping
@RequiresRole({"SUPER_ADMIN", "MANAGER"})
public Result<List<UserResponse>> list() {
    // 只有超管和经理能看到用户列表
}
```

### 4.3 第三步：实现权限服务

```java
@Service
@Slf4j
public class PermissionServiceImpl implements PermissionService {

    private final UserRepository userRepository;
    private final EmployeeRepository employeeRepository;

    @Override
    public boolean hasPermission(Long userId, String permissionCode) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("用户不存在: " + userId));

        // 超级管理员拥有所有权限
        if ("SUPER_ADMIN".equals(user.getRole())) {
            return true;
        }

        // 根据角色判断权限
        RoleEnum role = RoleEnum.fromCode(user.getRole());
        return checkPermissionByRole(role, permissionCode);
    }

    @Override
    public boolean canAccessDepartment(Long userId, String department) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("用户不存在: " + userId));

        RoleEnum role = RoleEnum.fromCode(user.getRole());

        // 超级管理员可以访问所有部门
        if (role == RoleEnum.SUPER_ADMIN) {
            return true;
        }

        // 部门经理只能访问自己的部门
        if (role == RoleEnum.MANAGER) {
            return department.equals(user.getDepartment());
        }

        // 普通员工不能访问其他部门的数据
        return false;
    }

    @Override
    public boolean canAccessEmployee(Long userId, Long employeeId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("用户不存在: " + userId));

        RoleEnum role = RoleEnum.fromCode(user.getRole());

        // 超级管理员可以访问所有员工
        if (role == RoleEnum.SUPER_ADMIN) {
            return true;
        }

        // 部门经理可以访问本部门员工
        if (role == RoleEnum.MANAGER) {
            String managerDept = user.getDepartment();
            String employeeDept = employeeRepository.findById(employeeId)
                .map(Employee::getDepartment)
                .orElse(null);
            return managerDept != null && managerDept.equals(employeeDept);
        }

        // 普通员工只能访问自己的数据
        if (role == RoleEnum.EMPLOYEE) {
            return employeeId.equals(user.getEmployeeId());
        }

        return false;
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

**核心逻辑**：
1. **超管特权**：如果是 `SUPER_ADMIN`，直接返回 `true`（拥有所有权限）
2. **角色权限映射**：通过 `switch` 语句，根据角色返回对应的权限集合
3. **数据范围检查**：通过 `canAccessDepartment` 和 `canAccessEmployee` 方法，检查用户是否能访问特定数据

### 4.4 第四步：实现权限切面（AOP）

```java
@Aspect
@Slf4j
@Component
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

        // 获取方法签名和注解
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        RequiresPermission annotation = method.getAnnotation(RequiresPermission.class);

        String permissionCode = annotation.value();

        // 1. 检查基本权限
        if (!permissionService.hasPermission(userId, permissionCode)) {
            log.warn("用户 {} 没有权限 {}", userId, permissionCode);
            throw new PermissionDeniedException("没有权限执行此操作");
        }

        // 2. 检查部门权限
        if (annotation.checkDepartment()) {
            checkDepartmentPermission(joinPoint, userId);
        }

        // 3. 检查所有者权限
        if (annotation.checkOwner()) {
            checkOwnerPermission(joinPoint, userId);
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
}
```

**AOP 工作原理**：
1. `@Before`：在方法执行**之前**拦截
2. `@annotation(...)`：只拦截带有 `@RequiresPermission` 注解的方法
3. 通过 `JoinPoint` 获取方法参数，进行数据范围检查
4. 如果权限检查失败，抛出 `PermissionDeniedException`，阻止方法执行

### 4.5 第五步：在Controller中使用

```java
@RestController
@RequestMapping("/api/employ")
public class EmployeeController {

    /**
     * 创建员工（需要创建权限 + 部门检查）
     */
    @PostMapping
    @RequiresPermission(value = "employee:create", checkDepartment = true)
    public Result<EmployeeResponse> create(@RequestBody EmployeeCreateRequest request) {
        EmployeeResponse response = employeeService.create(request);
        return Result.success(response);
    }

    /**
     * 查询员工列表（需要读取权限）
     */
    @GetMapping
    @RequiresPermission("employee:read")
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
     * 删除员工（需要删除权限 + 部门检查）
     */
    @DeleteMapping("/{id}")
    @RequiresPermission(value = "employee:delete", checkDepartment = true)
    public Result<Void> delete(@PathVariable Long id) {
        employeeService.delete(id);
        return Result.success(null);
    }
}
```

**注解说明**：
- `@RequiresPermission("employee:read")`：只需要读取权限
- `@RequiresPermission(value = "employee:create", checkDepartment = true)`：需要创建权限，且要检查部门
- `@RequiresPermission(value = "employee:delete", checkDepartment = true)`：需要删除权限，且要检查部门

---

## 五、完整请求流程

### 5.1 请求流程图

```
┌─────────────────────────────────────────────────────────────────┐
│              从前端点击按钮到后端返回数据的完整流程                │
└─────────────────────────────────────────────────────────────────┘

【前端】
1. 用户操作
   ┌──────────────────┐
   │ 用户点击"删除"按钮 │
   └─────────┬────────┘
             │
             ▼
2. 前端权限检查（可选，仅用于UI展示）
   ┌────────────────────────────────┐
   │ <PermissionButton               │
   │   permission="employee:delete"> │
   │                                 │
   │ → permissionStore.hasPermission│
   │   ("employee:delete")          │
   │                                 │
   │   ├─ 有权限: 显示按钮           │
   │   └─ 无权限: 隐藏按钮           │
   └─────────┬──────────────────────┘
             │ 有权限
             ▼
3. 发起 API 请求
   ┌──────────────────────────────┐
   │ DELETE /api/employ/5         │
   │ Authorization: Bearer {token}│
   └─────────┬────────────────────┘
             │
             │ HTTP DELETE
             │
━━━━━━━━━━━━┼━━━━━━━━━━━━━━━━━━━━━  网络传输
             │
             ▼

【后端】
4. Spring Security 过滤器
   ┌──────────────────────────────┐
   │ JwtAuthFilter                 │
   │ ↓                             │
   │ 1. 提取 Token                 │
   │ 2. 验证 Token                 │
   │ 3. 解析用户信息:              │
   │    - userId: 2                │
   │    - role: "MANAGER"          │
   │    - department: "技术部"     │
   │ 4. 存入 SecurityContext       │
   └─────────┬────────────────────┘
             │
             ▼
5. 到达 Controller
   ┌─────────────────────────────────────┐
   │ @DeleteMapping("/{id}")              │
   │ @RequiresPermission(                 │
   │   value = "employee:delete",         │
   │   checkDepartment = true             │
   │ )                                    │
   │ public Result delete(@PathVariable id)│
   └─────────┬───────────────────────────┘
             │
             │ 方法调用前
             ▼
6. 权限切面拦截
   ┌──────────────────────────────────┐
   │ PermissionAspect                  │
   │ @Before                           │
   │ ↓                                 │
   │ checkPermission() {               │
   │   1. 获取当前用户                 │
   │      userId = SecurityUtil        │
   │        .getCurrentUserId()        │
   │                                   │
   │   2. 提取注解信息                 │
   │      permission = "employee:delete"│
   │      checkDept = true             │
   │                                   │
   │   3. 基础权限检查                 │
   │      PermissionService            │
   │        .hasPermission(userId,     │
   │          "employee:delete")       │
   │      └─ ✅ 通过（经理有删除权限）│
   │                                   │
   │   4. 数据范围检查                 │
   │      (因为 checkDepartment=true)  │
   │      - 查询员工的部门             │
   │        SELECT department          │
   │        FROM employee              │
   │        WHERE id = 5               │
   │        → "技术部"                 │
   │      - 检查用户是否能访问该部门   │
   │        user.department = "技术部" │
   │        employee.department = "技术部"│
   │        └─ ✅ 通过                 │
   │                                   │
   │   5. 结果判断                     │
   │      └─ ✅ 通过: 继续执行         │
   │ }                                 │
   └─────────┬────────────────────────┘
             │
             │ 权限验证通过
             ▼
7. 执行业务逻辑
   ┌──────────────────────────────┐
   │ EmployeeService.delete(id)    │
   │ ↓                             │
   │ 1. 查询员工                   │
   │ 2. 标记为已删除（软删除）     │
   │ 3. 保存到数据库               │
   └─────────┬────────────────────┘
             │
             ▼
8. 返回响应
   ┌──────────────────────────────┐
   │ Result.success(null)          │
   │ {                             │
   │   code: 200,                  │
   │   message: "删除成功",         │
   │   data: null                  │
   │ }                             │
   └─────────┬────────────────────┘
             │
             │ HTTP Response
             │
━━━━━━━━━━━━┼━━━━━━━━━━━━━━━━━━━━━  网络传输
             │
             ▼

【前端】
9. 响应拦截器
   ┌──────────────────────────────┐
   │ request.interceptors.response │
   │ ↓                             │
   │ 1. 检查 code                  │
   │    └─ 200: 成功               │
   │ 2. 返回 data 部分             │
   └─────────┬────────────────────┘
             │
             ▼
10. 组件接收结果
   ┌──────────────────────────────┐
   │ 显示成功消息                  │
   │ 刷新列表                      │
   │ message.success("删除成功")   │
   └──────────────────────────────┘
```

### 5.2 权限检查失败流程

```
场景：销售部经理尝试删除技术部员工

【后端权限检查】

PermissionAspect.checkPermission()
   │
   ├─ 1. 基础权限检查
   │     hasPermission(userId, "employee:delete")
   │     └─ ✅ 通过（部门经理有删除权限）
   │
   ├─ 2. 数据范围检查 (checkDepartment = true)
   │     │
   │     ├─ 获取目标员工信息
   │     │    SELECT * FROM employee WHERE id = 5
   │     │    → department = "技术部"
   │     │
   │     ├─ 获取当前用户部门
   │     │    SecurityUtil.getCurrentUserDepartment()
   │     │    → "销售部"
   │     │
   │     └─ 对比部门
   │          "销售部" ≠ "技术部"
   │          └─ ❌ 失败！
   │
   └─ 3. 抛出异常
        throw new PermissionDeniedException(
          "只能操作本部门的数据"
        )

              │
              ▼

【异常处理】

GlobalExceptionHandler
   │
   ├─ @ExceptionHandler(PermissionDeniedException.class)
   │
   └─ 返回响应
        {
          code: 403,
          message: "只能操作本部门的数据",
          data: null
        }

              │
              │ HTTP 403 Forbidden
              ▼

【前端处理】

响应拦截器
   │
   ├─ 检测到 code === 403
   │
   ├─ 显示错误消息
   │    message.error("只能操作本部门的数据")
   │
   └─ 返回 Promise.reject()
```

---

## 六、踩坑经历

### 6.1 坑一：JWT Token 中缺少角色信息

**问题**：
```java
// 最初的 Token 生成代码
public String generateToken(User user) {
    Map<String, Object> claims = new HashMap<>();
    claims.put("userId", user.getId());
    claims.put("username", user.getUsername());
    // ❌ 缺少 role、department、employeeId
    return Jwts.builder()...
}
```

**现象**：
- 权限检查时，无法从 Token 中获取角色信息
- 每次都要查数据库，性能差

**解决方案**：
```java
public String generateToken(User user) {
    Map<String, Object> claims = new HashMap<>();
    claims.put("sub", user.getUsername());
    claims.put("userId", user.getId());
    claims.put("username", user.getUsername());
    claims.put("role", user.getRole());           // ✅ 添加角色
    claims.put("department", user.getDepartment()); // ✅ 添加部门
    claims.put("employeeId", user.getEmployeeId());  // ✅ 添加员工ID
    return Jwts.builder()...
}
```

**经验**：Token 中应该包含**所有权限检查需要的信息**，避免频繁查数据库！

### 6.2 坑二：数据范围检查逻辑错误

**问题**：
```java
// 最初的部门检查代码
if (role == RoleEnum.MANAGER) {
    // ❌ 错误：直接比较字符串，没有处理 null
    return user.getDepartment().equals(department);
}
```

**现象**：
- 如果 `user.getDepartment()` 为 `null`，会抛出 `NullPointerException`

**解决方案**：
```java
if (role == RoleEnum.MANAGER) {
    // ✅ 正确：先判断 null，再比较
    return user.getDepartment() != null && 
           user.getDepartment().equals(department);
}
```

**经验**：**永远要考虑 null 值**！特别是从数据库查询出来的字段。

### 6.3 坑三：切面中无法获取方法参数

**问题**：
```java
// 尝试从参数中提取员工ID
@Before("@annotation(RequiresPermission)")
public void checkPermission(JoinPoint joinPoint) {
    Object[] args = joinPoint.getArgs();
    // ❌ 问题：不知道哪个参数是员工ID
    Long employeeId = (Long) args[0];  // 假设第一个参数是ID？
}
```

**现象**：
- 如果方法签名改变，参数顺序改变，就会出错
- 不够灵活

**解决方案**：
```java
// ✅ 方案1：通过参数类型判断
for (Object arg : args) {
    if (arg instanceof Long) {
        Long employeeId = (Long) arg;
        // 检查权限
    }
}

// ✅ 方案2：使用自定义注解标记参数
public void delete(@PathVariable @EmployeeId Long id) {
    // ...
}
```

**经验**：**参数提取要灵活**，不能硬编码参数位置！

### 6.4 坑四：前端权限检查被绕过

**问题**：
- 前端通过 `permissionStore.hasPermission()` 隐藏了按钮
- 但用户可以通过**直接调用 API** 绕过前端检查

**解决方案**：
```
✅ 前端权限检查：仅用于UI展示，提升用户体验
✅ 后端权限检查：真正的安全防线，必须做！

记住：前端权限检查是"用户体验"，后端权限检查是"安全保障"！
```

---

## 七、总结

### 7.1 核心要点

1. **RBAC 模型**：用户 → 角色 → 权限，清晰明了
2. **三级权限**：超管（全部）、经理（本部门）、员工（仅自己）
3. **注解式权限**：通过 `@RequiresPermission` 和 `@RequiresRole` 注解，代码简洁
4. **AOP 切面**：统一处理权限检查，避免代码重复
5. **数据范围检查**：不仅检查"能不能做"，还检查"能不能操作这个数据"

### 7.2 实现步骤总结

```
1. 定义角色枚举（RoleEnum）
   ↓
2. 创建权限注解（@RequiresPermission、@RequiresRole）
   ↓
3. 实现权限服务（PermissionService）
   ↓
4. 实现权限切面（PermissionAspect）
   ↓
5. 在Controller中使用注解
   ↓
6. 测试验证
```

### 7.3 优势

✅ **灵活扩展**：新增角色或权限，只需要修改枚举和权限映射  
✅ **代码清晰**：注解式权限控制，一目了然  
✅ **安全可靠**：前后端双重验证，后端是真正的安全防线  
✅ **易于维护**：权限逻辑集中管理，修改方便  

### 7.4 后续优化方向

1. **权限缓存**：使用 Redis 缓存用户权限，减少数据库查询
2. **动态权限**：支持从数据库加载权限规则，运行时修改
3. **权限审计**：记录所有权限检查结果，生成审计报告
4. **细粒度权限**：支持字段级别的权限控制（如：普通员工不能修改薪资字段）

---

## 八、参考资源

- [RBAC 模型详解](https://en.wikipedia.org/wiki/Role-based_access_control)
- [Spring AOP 官方文档](https://docs.spring.io/spring-framework/docs/current/reference/html/core.html#aop)
- [JWT 最佳实践](https://jwt.io/introduction)

---

**如果这篇文章对你有帮助，欢迎点赞、收藏、转发！** 🎉  
**有问题欢迎在评论区讨论，一起进步！** 💪

---

**文档版本**: v1.0  
**创建日期**: 2026-01-02  
**作者**: 博主本人

