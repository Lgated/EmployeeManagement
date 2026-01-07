# Spring Data JPA分页功能实战：从入门到精通

  

> 最近在用新的技术栈做员工管理系统的时候，遇到了一个很实际的问题：前端列表展示几千条数据，每次都要全部加载，页面卡得不行！😭 于是决定把分页功能从前端移到后端，用Spring Data JPA来实现。在这个过程中学到了很多新的知识。今天就来分享一下JPA分页的完整实现过程，包括不同的实现方式、最佳实践，以及与MyBatis的对比。

  

---

  

## 一、为什么需要后端分页？

  

### 1.1 前端分页的痛点

  

刚开始我的项目是这样的：

  

```java

// 后端：直接返回所有数据

@GetMapping

public Result<List<EmployeeResponse>> list() {

    List<EmployeeResponse> all = employeeService.listAll();

    return Result.success(all);  // 假设有10000条数据，全部返回！

}

```

  

```tsx

// 前端：拿到所有数据后，Antd Table自己分页

const [employees, setEmployees] = useState<Employee[]>([])

const data = await getEmployeeList()  // 一次性加载10000条

setEmployees(data)  // 前端内存压力山大！

```

  

**问题来了：**

  

- ❌ **性能问题**：数据库查询10000条数据，网络传输10000条，前端渲染10000条（虽然只显示10条）

- ❌ **内存占用**：前端浏览器内存被大量数据占用

- ❌ **用户体验**：首次加载慢，页面卡顿

- ❌ **服务器压力**：每次请求都要查询全表

  

### 1.2 后端分页的优势

  

改成后端分页后：

  

```java

// 后端：只查询当前页的数据

@GetMapping

public Result<PageResponse<EmployeeResponse>> list(

        @RequestParam(defaultValue = "1") int page,

        @RequestParam(defaultValue = "10") int size) {

    // 只查询第1页的10条数据

    return Result.success(employeeService.pageQuery(page, size));

}

```

  

**优势明显：**

  

- ✅ **性能提升**：数据库只查询10条，网络只传输10条

- ✅ **内存友好**：前端只保存当前页数据

- ✅ **用户体验**：加载速度快，页面流畅

- ✅ **服务器友好**：减少数据库查询压力

  

---

  

## 二、Spring Data JPA分页核心概念

  

### 2.1 三个核心接口

  

JPA分页主要依赖三个接口，都在 `org.springframework.data.domain` 包下：

  

#### **1. Pageable（分页请求）**

  

`Pageable` 是一个接口，代表"分页请求"，包含：

- **页码**（page）：第几页（从0开始）

- **每页大小**（size）：每页多少条

- **排序信息**（Sort）：按什么字段排序

  

```java

// 导包

import org.springframework.data.domain.Pageable;

import org.springframework.data.domain.PageRequest;

import org.springframework.data.domain.Sort;

  

// 创建Pageable的两种方式

// 方式1：使用PageRequest（最常用）

Pageable pageable = PageRequest.of(0, 10);  // 第0页，每页10条

  

// 方式2：带排序的PageRequest

Pageable pageable = PageRequest.of(

    0,                                      // 页码（从0开始）

    10,                                     // 每页大小

    Sort.by(Sort.Direction.ASC, "id")      // 按id升序

);

```

  

#### **2. Page（分页结果）**

  

`Page` 接口继承自 `Slice`，代表"分页查询的结果"，包含：

- **数据列表**（content）：当前页的数据

- **总记录数**（totalElements）：数据库总共有多少条

- **总页数**（totalPages）：总共多少页

- **当前页码**（number）：当前是第几页（从0开始）

- **每页大小**（size）：每页多少条

  

```java

// 导包

import org.springframework.data.domain.Page;

  

// Repository返回Page对象

Page<Employee> page = employeeRepository.findAllByDeletedFalse(pageable);

  

// 获取数据

List<Employee> employees = page.getContent();        // 当前页数据

long total = page.getTotalElements();                 // 总记录数

int totalPages = page.getTotalPages();               // 总页数

int currentPage = page.getNumber();                  // 当前页码（从0开始）

int pageSize = page.getSize();                       // 每页大小

boolean hasNext = page.hasNext();                    // 是否有下一页

boolean hasPrevious = page.hasPrevious();            // 是否有上一页

```

  

#### **3. Sort（排序）**

  

`Sort` 用于指定排序规则：

  

```java

// 导包

import org.springframework.data.domain.Sort;

  

// 单字段排序

Sort sort = Sort.by(Sort.Direction.ASC, "id");        // 按id升序

Sort sort = Sort.by(Sort.Direction.DESC, "createdAt"); // 按创建时间降序

  

// 多字段排序

Sort sort = Sort.by(

    Sort.Order.asc("department"),   // 先按部门升序

    Sort.Order.desc("salary")      // 再按薪资降序

);

  

// 在PageRequest中使用

Pageable pageable = PageRequest.of(0, 10, sort);

```

  

---

  

## 三、JPA分页的三种实现方式

  

### 方式一：方法命名查询（最简单，推荐！⭐）

  

**适用场景**：查询条件简单，字段明确

  

**优点**：

- ✅ 代码简洁，Spring自动生成SQL

- ✅ 类型安全，编译期检查

- ✅ 支持软删除过滤（`DeletedFalse`）

  

**缺点**：

- ❌ 方法名会很长（条件多时）

- ❌ 复杂查询不支持

  

**实现步骤：**

  

#### 步骤1：Repository接口定义

  

```java

package com.example.empmgmt.repository;

  

import com.example.empmgmt.domain.Employee;

import org.springframework.data.domain.Page;

import org.springframework.data.domain.Pageable;

import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.stereotype.Repository;

  

@Repository

public interface EmployeeRepository extends JpaRepository<Employee, Long> {

    // 基础分页查询（过滤已删除）

    Page<Employee> findAllByDeletedFalse(Pageable pageable);

    // 按姓名搜索 + 分页

    Page<Employee> findByNameContainingIgnoreCaseAndDeletedFalse(

        String name,

        Pageable pageable

    );

    // 按部门搜索 + 分页

    Page<Employee> findByDepartmentAndDeletedFalse(

        String department,

        Pageable pageable

    );

}

```

  

**关键点：**

- 方法名必须以 `find`、`query`、`get`、`read` 开头

- 参数中必须有 `Pageable`，返回类型必须是 `Page<T>`

- `DeletedFalse` 会自动添加 `WHERE deleted = false` 条件

  

#### 步骤2：Service层调用

  

```java

@Service

@Transactional

public class EmployeeServiceImpl implements EmployeeService {

    private final EmployeeRepository employeeRepository;

    @Override

    @Transactional(readOnly = true)

    public PageResponse<EmployeeResponse> pageQuery(

            String name,

            String department,

            int page,

            int size) {

        // 1. 创建Pageable（注意：page从0开始，前端传的是1开始）

        Pageable pageable = PageRequest.of(

            Math.max(page - 1, 0),              // 前端第1页 = 后端第0页

            size,

            Sort.by(Sort.Direction.ASC, "id")   // 按id升序，保证顺序稳定

        );

        // 2. 根据条件调用不同的Repository方法

        Page<Employee> employeePage;

        if (name != null && !name.isBlank()) {

            employeePage = employeeRepository

                .findByNameContainingIgnoreCaseAndDeletedFalse(name, pageable);

        } else if (department != null && !department.isBlank()) {

            employeePage = employeeRepository

                .findByDepartmentAndDeletedFalse(department, pageable);

        } else {

            employeePage = employeeRepository.findAllByDeletedFalse(pageable);

        }

        // 3. 转换为DTO

        Page<EmployeeResponse> mappedPage = employeePage.map(EmployeeResponse::from);

        // 4. 包装成自定义的PageResponse（前端友好）

        return PageResponse.of(mappedPage);

    }

}

```

  

#### 步骤3：自定义PageResponse（前端友好）

  

Spring的`Page`对象对前端不太友好（页码从0开始），我们可以包装一下：

  

```java

package com.example.empmgmt.dto.response;

  

import org.springframework.data.domain.Page;

import java.util.List;

  

public record PageResponse<T>(

        List<T> records,    // 数据列表

        long total,         // 总记录数

        int page,          // 当前页（从1开始，前端友好）

        int size           // 每页大小

) {

    public static <T> PageResponse<T> of(Page<T> page) {

        return new PageResponse<>(

            page.getContent(),           // 数据列表

            page.getTotalElements(),     // 总记录数

            page.getNumber() + 1,        // 页码从0转1（前端友好）

            page.getSize()               // 每页大小

        );

    }

}

```

  

#### 步骤4：Controller层接收参数
  

```java

@RestController

@RequestMapping("/api/employ")

public class EmployeeController {

    @GetMapping

    public Result<PageResponse<EmployeeResponse>> list(

            @RequestParam(required = false) String name,

            @RequestParam(required = false) String department,

            @RequestParam(defaultValue = "1") int page,   // 默认第1页

            @RequestParam(defaultValue = "10") int size    // 默认10条

    ) {

        PageResponse<EmployeeResponse> result =

            employeeService.pageQuery(name, department, page, size);

        return Result.success(result);

    }

}

```

  

**生成的SQL（Hibernate自动生成）：**

  

```sql

-- 查询总数

SELECT COUNT(*) FROM employee WHERE deleted = false;

  

-- 查询数据

SELECT * FROM employee

WHERE deleted = false

ORDER BY id ASC

LIMIT 10 OFFSET 0;

```

  

---

  

### 方式二：@Query注解 + 分页参数

  

**适用场景**：需要自定义SQL，但查询逻辑不太复杂

  

**优点**：

- ✅ 可以写自定义SQL

- ✅ 支持复杂查询条件

  

**缺点**：

- ❌ 需要手动写SQL

- ❌ 维护成本稍高


**实现示例：**


```java

@Repository

public interface EmployeeRepository extends JpaRepository<Employee, Long> {

    // 使用JPQL（Java Persistence Query Language）

    @Query("SELECT e FROM Employee e WHERE e.deleted = false " +

           "AND (:name IS NULL OR e.name LIKE %:name%) " +

           "AND (:department IS NULL OR e.department = :department)")

    Page<Employee> findByConditions(

        @Param("name") String name,

        @Param("department") String department,

        Pageable pageable

    );
      // 使用原生SQL（需要设置nativeQuery = true）

    @Query(value = "SELECT * FROM employee " +

                   "WHERE deleted = false " +

                   "AND (:name IS NULL OR name LIKE %:name%) " +

                   "ORDER BY id ASC",

           countQuery = "SELECT COUNT(*) FROM employee WHERE deleted = false",

           nativeQuery = true)

    Page<Employee> findByConditionsNative(
            @Param("name") String name,

        Pageable pageable

    );

}
```

**注意点：**

- 使用原生SQL时，必须提供 `countQuery`（用于查询总数）

- JPQL使用实体类名和属性名，原生SQL使用表名和列名

  

---

  

### 方式三：Specification动态查询（最灵活！🔥）

**适用场景**：查询条件动态变化，需要灵活组合

  

**优点**：

- ✅ 最灵活，可以动态组合查询条件

- ✅ 适合复杂的多条件查询

  

**缺点**：

- ❌ 代码相对复杂

- ❌ 需要理解Specification API

  

**实现步骤：**
  
#### 步骤1：Repository继承JpaSpecificationExecutor


```java

@Repository

public interface EmployeeRepository extends

    JpaRepository<Employee, Long>,

    JpaSpecificationExecutor<Employee> {  // 关键：继承这个接口

    // ...

}

```

  

#### 步骤2：Service层构建Specification
```java

@Service

public class EmployeeServiceImpl implements EmployeeService {

    @Override

    @Transactional(readOnly = true)

    public PageResponse<EmployeeResponse> pageQuery(

            String name,

            String department,

            int page,

            int size) {

        // 1. 创建Pageable

        Pageable pageable = PageRequest.of(

            Math.max(page - 1, 0),

            size,

            Sort.by(Sort.Direction.ASC, "id")

        );

        // 2. 构建Specification（动态查询条件）

        Specification<Employee> spec = (root, query, cb) -> {

            List<Predicate> predicates = new ArrayList<>();

            // 必须条件：未删除

            predicates.add(cb.equal(root.get("deleted"), false));

            // 可选条件：姓名模糊查询

            if (name != null && !name.isBlank()) {

                predicates.add(cb.like(

                    cb.lower(root.get("name")),
                    "%" + name.toLowerCase() + "%"

                ));

            }
               // 可选条件：部门精确查询

            if (department != null && !department.isBlank()) {

                predicates.add(cb.equal(root.get("department"), department));

            }

            // 组合所有条件（AND关系）

            return cb.and(predicates.toArray(new Predicate[0]));

        };
          // 3. 执行查询

        Page<Employee> employeePage = employeeRepository.findAll(spec, pageable);
                // 4. 转换并返回

        Page<EmployeeResponse> mappedPage = employeePage.map(EmployeeResponse::from);

        return PageResponse.of(mappedPage);

    }

}

```
        
        **Specification的优势：**

- 可以动态组合AND、OR条件

- 支持复杂的查询逻辑

- 类型安全，编译期检查

  

---

  

## 四、三种方式对比总结

  

| 对比项 | 方法命名查询 | @Query注解 | Specification |

|--------|------------|-----------|---------------|

| **代码简洁度** | ⭐⭐⭐⭐⭐ | ⭐⭐⭐ | ⭐⭐ |

| **灵活性** | ⭐⭐ | ⭐⭐⭐ | ⭐⭐⭐⭐⭐ |

| **学习成本** | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐ | ⭐⭐ |

| **适用场景** | 简单查询 | 自定义SQL | 动态复杂查询 |

| **推荐度** | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐ | ⭐⭐⭐⭐ |

  

**我的建议：**

- **优先使用方法命名查询**：简单、直观、类型安全

- **复杂查询用@Query**：需要自定义SQL时

- **动态查询用Specification**：条件经常变化时

## 五、完整导包清单（避免踩坑！）

  

### Repository层

  

```java

import org.springframework.data.domain.Page;           // 分页结果

import org.springframework.data.domain.Pageable;        // 分页请求

import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.data.jpa.repository.JpaSpecificationExecutor;  // 动态查询

import org.springframework.data.jpa.repository.Query;  // 自定义查询

import org.springframework.data.repository.query.Param; // 参数绑定

