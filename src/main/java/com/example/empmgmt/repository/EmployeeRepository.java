package com.example.empmgmt.repository;

import com.example.empmgmt.domain.Employee;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;


@Repository
public interface EmployeeRepository extends JpaRepository<Employee,Long> {

    // 根据名字模糊匹配+忽略大小写查询（过滤已删除）
    List<Employee> findByNameContainingIgnoreCaseAndDeleteFalse(String name);

    // 根据部门查询（过滤已删除）
    List<Employee> findByDepartmentAndDeletedFalse(String department);

    // 分页查询未删除的记录
    Page<Employee> findAllByDeletedFalse(Pageable pageable);

    // 查询未删除的记录
    Optional<Employee> findByIdAndDeletedFalse(Long id);

    // 查询所有未删除的记录
    List<Employee> findAllByDeletedFalse();
}
