package com.example.empmgmt.service.Impl;

import com.example.empmgmt.common.Exception.BusinessException;
import com.example.empmgmt.common.util.CacheKeyUtil;
import com.example.empmgmt.domain.Employee;
import com.example.empmgmt.dto.request.EmployeeCreateRequest;
import com.example.empmgmt.dto.request.EmployeeUpdateRequest;
import com.example.empmgmt.dto.response.DeptStatsResponse;
import com.example.empmgmt.dto.response.EmployeeResponse;
import com.example.empmgmt.dto.response.PageResponse;
import com.example.empmgmt.repository.EmployeeRepository;
import com.example.empmgmt.service.EmployeeService;

import com.example.empmgmt.common.util.SecurityUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;

import jakarta.persistence.PersistenceContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.parameters.P;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

@Service
@Transactional
@Slf4j
public class EmployeeServiceImpl implements EmployeeService {


    private final EmployeeRepository employeeRepository;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    public EmployeeServiceImpl(EmployeeRepository employeeRepository,
                               StringRedisTemplate stringRedisTemplate,
                               ObjectMapper objectMapper) {
        this.employeeRepository = employeeRepository;
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
    }

    //告诉Spring 这是 JPA 的 EntityManager，不是你自己 new 的
    @PersistenceContext
    //注入数据库会话（JPA 的核心 API）
    private EntityManager entityManager;

    @Override
    public EmployeeResponse create(EmployeeCreateRequest request) {
        Employee employee = new Employee();
        copyFromRequest(request, employee);
        Employee saved = employeeRepository.save(employee);

        // 写后删除缓存
        // 因为我们不知道哪些具体条件的列表被查询过，因此无法只删某几个缓存
        // 写操作频率通常远低于读，全部删除是可以接受的
        // 并且删除效率高于更新key，删的时候只需遍历这个 Set，O(1) 定位，O(N) 批量删
        clearEmployeeListCache();

        return EmployeeResponse.from(saved);
    }



    @Override
    public EmployeeResponse update(Long id, EmployeeUpdateRequest request) {
        Employee employee = employeeRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new EntityNotFoundException("员工不存在，ID:" + id));
        // 将dto赋值给实体对象
        copyFromRequest(request, employee);
        Employee updated = employeeRepository.save(employee);
        // 写后删除缓存
        clearEmployeeListCache();
        return EmployeeResponse.from(updated);
    }

    @Override
    public void delete(Long id) {
        Employee employee = employeeRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new EntityNotFoundException("员工不存在，ID: " + id));

        //软删除
        employee.setDeleted(true);
        employee.setDeletedAt(LocalDateTime.now());
        employee.setDeletedBy(getCurrentUserId()); // 从SecurityContext获取用户id
        // 写后删除缓存
        clearEmployeeListCache();
        employeeRepository.save(employee);
    }

    @Override
    @Transactional(readOnly = true)
    public EmployeeResponse findById(Long id) {
        Employee employee = employeeRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new EntityNotFoundException("员工不存在，ID: " + id));
        return EmployeeResponse.from(employee);
    }

    @Override
    public PageResponse<EmployeeResponse> pageQuery(String name, String department, int page, int size) {

        // 1. 生成缓存 key
        String cacheKey = CacheKeyUtil.buildEmployeeListKey(name, department, page, size);

        // 2. 从 Redis 读取缓存
        String json = stringRedisTemplate.opsForValue().get(cacheKey);
        if (json != null && !json.isBlank()) {
            try {
                // 3. 反序列化为 PageResponse<EmployeeResponse>
                PageResponse<EmployeeResponse> employeeResponsePageResponse = objectMapper.readValue(
                        json,
                        new TypeReference<PageResponse<EmployeeResponse>>() {}
                );
                // 如果缓存存在，直接返回
                return employeeResponsePageResponse;
            } catch (Exception e) {
                // 解析失败就当没缓存，用日志记录一下，不影响主流程
                log.warn("解析员工分页缓存失败，key={}, json={}", cacheKey, json, e);
            }
        }


        // 4. 缓存不存在，执行数据库查询
        PageResponse<EmployeeResponse> pageResult = doQueryFromDb(name, department, page, size);

        // 5. 查完将数据写入缓存（加一点随机 TTL，避免雪崩）
        try {
            String toCache = objectMapper.writeValueAsString(pageResult);

            long baseTtlSeconds = 5 * 60; // 5分钟
            long randomExtra = ThreadLocalRandom.current().nextLong(0, 60); // 0~60秒
            stringRedisTemplate.opsForValue().set(
                cacheKey,
                toCache,
                Duration.ofSeconds(baseTtlSeconds + randomExtra)
            );
            // 6. 把 key 记到“索引集合”中，方便写后删除
            stringRedisTemplate.opsForSet().add(CacheKeyUtil.employeeListKeySet(), cacheKey);
        } catch (Exception e) {
            // 序列化失败就不缓存，用日志记录一下，不影响主流程
            log.warn("写入员工分页缓存失败，key={}", cacheKey, e);
        }

        return pageResult;

    }



    @Override
    @Transactional(readOnly = true)
    public List<EmployeeResponse> listAll() {
        return employeeRepository.findAllByDeletedFalse().stream()
                .map(EmployeeResponse::from)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<EmployeeResponse> search(String name, String department) {
        List<Employee> employees;
        if (name != null && !name.isBlank()) {
            // containing : LIKE '%值%' , 大量数据的时候不要使用
            employees = employeeRepository.findByNameContainingIgnoreCaseAndDeletedFalse(name);
        } else if (department != null && !department.isBlank()) {
            employees = employeeRepository.findByDepartmentAndDeletedFalse(department);
        } else {
            employees = employeeRepository.findAllByDeletedFalse();
        }
        return employees.stream()
                .map(EmployeeResponse::from)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    public List<DeptStatsResponse> getDeptEmpCount() {
        //调用pgsql函数
        List<Object[]> rows = entityManager.createNativeQuery("SELECT * FROM fn_dept_emp_count()").getResultList();
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
    @SuppressWarnings("unchecked")
    public List<DeptStatsResponse> getDeptAvgSalary() {
        List<Object[]> rows = entityManager.createNativeQuery("SELECT * FROM fn_dept_avg_salary()").getResultList();
        return rows.stream().map(row -> new DeptStatsResponse(
                (String) row[0],
                null,
                (BigDecimal) row[1]
        )).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal getEmpYears(Long id) {
        Object result = entityManager
                .createNativeQuery("SELECT fn_emp_years(:id)")
                .setParameter("id", id)
                .getSingleResult();

        return result == null
                ? BigDecimal.ZERO
                : new BigDecimal(result.toString());
    }

    // 数据库查询方法
    private PageResponse<EmployeeResponse> doQueryFromDb(String name, String department, int page, int size) {
        // 1. PageRequest：page 从 0 开始；按 id 正序排列
        Pageable pageable = PageRequest.of(
                Math.max(page - 1, 0),      // 前端一般从 1 开始，后端做个保护
                size,
                Sort.by(Sort.Direction.ASC, "id")   // 核心：按 id 升序
        );

        Page<Employee> employeePage;

        // 2. 根据搜索条件走不同的查询
        if (name != null && !name.isBlank()) {
            employeePage = employeeRepository.findByNameContainingIgnoreCaseAndDeletedFalse(name, pageable);
        } else if (department != null && !department.isBlank()) {
            employeePage = employeeRepository.findByDepartmentAndDeletedFalse(department, pageable);
        } else {
            employeePage = employeeRepository.findAllByDeletedFalse(pageable);
        }

        // 3. 将 Page<Employee> 转成 Page<EmployeeResponse>
        Page<EmployeeResponse> mappedPage = employeePage.map(EmployeeResponse::from);

        // 4. 用 PageResponse 包装
        return PageResponse.of(mappedPage);
    }


    /**
     * 恢复员工信息
     */
    public void restore(Long id){
        Employee employee = employeeRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("员工不存在，ID: " + id));

        if(!employee.getDeleted()){
            throw  new BusinessException("员工未被删除，无需恢复");
        }

        employee.setDeleted(false);
        employee.setDeletedAt(null);
        employee.setDeletedBy(null);
        employee.setUpdatedBy(getCurrentUserId());

        employeeRepository.save(employee);
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
        employee.setAvatar(request.avatar());
    }

    /**
     * 将更新请求 DTO 的数据复制到实体对象
     */
    private void copyFromRequest(EmployeeUpdateRequest request, Employee employee) {
        if(request.name() != null && !request.name().isBlank()){
            employee.setName(request.name());
        }
        if (request.gender() != null && !request.gender().isBlank()) {
            employee.setGender(request.gender());
        }
        if (request.age() != null) {
            employee.setAge(request.age());
        }
        if (request.department() != null && !request.department().isBlank()) {
            employee.setDepartment(request.department());
        }
        if (request.position() != null && !request.position().isBlank()) {
            employee.setPosition(request.position());
        }
        // 7. 薪资
        if (request.salary() != null) {
            employee.setSalary(request.salary());
        }
        if (request.hireDate() != null) {
            employee.setHireDate(request.hireDate());
        }
        if (request.avatar() != null && !request.avatar().isBlank()) {
            employee.setAvatar(request.avatar());
        }
        employee.setSalary(request.salary());
    }

    // 添加私有方法
    private Long getCurrentUserId() {
        Long userId = SecurityUtil.getCurrentUserId();
        if (userId == null) {
            throw new BusinessException("用户未登录或登录已过期");
        }
        return userId;
    }


    /**
     * 删除所有员工列表相关缓存
     */
    private void clearEmployeeListCache() {
        String keySet = CacheKeyUtil.employeeListKeySet();
        // 1. 取出所有列表 key（现在存的就是 String）
        Set<String> keys = stringRedisTemplate.opsForSet().members(keySet);
        if (keys != null && !keys.isEmpty()) {
            // 2. 删除这些 key
            stringRedisTemplate.delete(keys);
        }
        // 3. 清空 key 集合本身
        stringRedisTemplate.delete(keySet);
    }
}
