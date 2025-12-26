package com.example.empmgmt.repository;

import com.example.empmgmt.domain.Employee;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;


@Repository
public interface EmployeeRepository extends JpaRepository<Employee,Long> {

    List<Employee> findByNameContainingIgnoreCase(String name);

    List<Employee> findByDepartment(String department);
}
