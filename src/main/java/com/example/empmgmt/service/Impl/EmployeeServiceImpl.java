package com.example.empmgmt.service.Impl;

import com.example.empmgmt.Exception.BusinessException;
import com.example.empmgmt.domain.Employee;
import com.example.empmgmt.dto.request.EmployeeCreateRequest;
import com.example.empmgmt.dto.request.EmployeeUpdateRequest;
import com.example.empmgmt.dto.response.DeptStatsResponse;
import com.example.empmgmt.dto.response.EmployeeResponse;
import com.example.empmgmt.dto.response.PageResponse;
import com.example.empmgmt.repository.EmployeeRepository;
import com.example.empmgmt.service.EmployeeService;

import com.example.empmgmt.util.SecurityUtil;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;

import jakarta.persistence.PersistenceContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional
public class EmployeeServiceImpl implements EmployeeService {

    private final EmployeeRepository employeeRepository;

    public EmployeeServiceImpl(EmployeeRepository employeeRepository) {
        this.employeeRepository = employeeRepository;
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
        return EmployeeResponse.from(saved);
    }



    @Override
    public EmployeeResponse update(Long id, EmployeeUpdateRequest request) {
        Employee employee = employeeRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new EntityNotFoundException("员工不存在，ID:" + id));
        // 将dto赋值给实体对象
        copyFromRequest(request, employee);
        Employee updated = employeeRepository.save(employee);
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

        // 4. 用刚才的 PageResponse 包装
        return PageResponse.of(mappedPage);
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


    /**
     * 恢复员工信息
     * @param id
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
}
