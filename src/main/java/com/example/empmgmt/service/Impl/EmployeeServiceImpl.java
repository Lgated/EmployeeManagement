package com.example.empmgmt.service.Impl;

import com.example.empmgmt.Exception.BusinessException;
import com.example.empmgmt.domain.Employee;
import com.example.empmgmt.dto.request.EmployeeCreateRequest;
import com.example.empmgmt.dto.request.EmployeeUpdateRequest;
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


    //TODO: 更新逻辑有问题：要求部分的时候，其他不动·
    @Override
    public EmployeeResponse update(Long id, EmployeeUpdateRequest request) {
        Employee employee = employeeRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("员工不存在，ID:" + id));
        copyFromRequest(request, employee);
        Employee updated = employeeRepository.save(employee);
        return EmployeeResponse.from(updated);
    }

    @Override
    public void delete(Long id) {
        Employee employee = employeeRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("员工不存在，ID: " + id));


        employeeRepository.deleteById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public EmployeeResponse findById(Long id) {
        Employee employee = employeeRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("员工不存在，ID: " + id));
        return EmployeeResponse.from(employee);
    }

    @Override
    @Transactional(readOnly = true)
    public List<EmployeeResponse> listAll() {
        return employeeRepository.findAll().stream()
                .map(EmployeeResponse::from)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<EmployeeResponse> search(String name, String department) {
        List<Employee> employees;
        if (name != null && !name.isBlank()) {
            // containing : LIKE '%值%' , 大量数据的时候不要使用
            employees = employeeRepository.findByNameContainingIgnoreCaseAndDeleteFalse(name);
        } else if (department != null && !department.isBlank()) {
            employees = employeeRepository.findByDepartmentAndDeletedFalse(department);
        } else {
            employees = employeeRepository.findAll();
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
